package se.pundaren.guide.app

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.pundaren.guide.convoy.ConvoyConsent
import se.pundaren.guide.convoy.ConvoyFreshness
import se.pundaren.guide.convoy.ConvoyInvite
import se.pundaren.guide.convoy.ConvoyLimits
import se.pundaren.guide.convoy.ConvoyPosition
import se.pundaren.guide.convoy.ConvoyReceiver
import se.pundaren.guide.convoy.ConvoySender
import se.pundaren.guide.convoy.ConvoySession
import se.pundaren.guide.convoy.ConvoySessionCredentials
import se.pundaren.guide.convoy.ConvoyVerification
import se.pundaren.guide.convoy.PeerPresentation
import se.pundaren.guide.convoy.ReceiveOutcome
import se.pundaren.guide.convoy.SealOutcome
import se.pundaren.guide.convoy.SendRefusal
import se.pundaren.guide.convoy.TransportState
import se.pundaren.guide.convoy.http.HttpConvoyTransport
import se.pundaren.guide.convoy.http.RelayClient
import se.pundaren.guide.convoy.http.RelayException
import se.pundaren.guide.truth.api.GeoPoint
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Den egna positionen som den får delas: bara en verklig position med känd noggrannhet. */
data class OwnFix(val point: GeoPoint, val accuracyMeters: Double, val fixAgeSeconds: Double)

/** Den andra deltagarens senaste position, med ärlig ålder (07, 3.5). */
data class ConvoyPeer(val point: GeoPoint, val accuracyMeters: Float, val presentation: PeerPresentation)

enum class ConvoyPhase { IDLE, CONNECTING, SHARING }

data class ConvoyView(
    val phase: ConvoyPhase = ConvoyPhase.IDLE,
    /** QR-kodens text medan inbjudan gäller, annars null. Bara hos den som skapade sessionen. */
    val inviteText: String? = null,
    val expiresAtUtc: Instant? = null,
    val transport: TransportState = TransportState.OFFLINE,
    /** Sex siffror som de två jämför muntligt (07 T-01). Finns när den andras första kuvert har kommit. */
    val verificationCode: String? = null,
    val peer: ConvoyPeer? = null,
    val peerStopped: Boolean = false,
    val peerEmergencyAtUtc: Instant? = null,
    /** Sant när den egna positionen inte sänds just nu (ingen position, eller provtur med simulerad position). */
    val notSending: Boolean = false,
    val message: String? = null,
)

/**
 * Convoy för två i appen (07): skapar eller går med i en session, sänder den egna positionen var
 * femte sekund och tar emot den andras. Allt som lämnar telefonen är förseglat med sessionsnyckeln.
 *
 * Sessionen finns bara i minnet. Stängs appen är delningen slut, och den andra ser att kontakten bröts.
 */
class ConvoyController(
    private val scope: CoroutineScope,
    private val ownFix: () -> OwnFix?,
    private val log: (String) -> Unit,
) {
    private class Live(
        val session: ConvoySession, val ownId: String, val sender: ConvoySender, val receiver: ConvoyReceiver,
        val transport: HttpConvoyTransport, val consent: ConvoyConsent,
    ) {
        var job: Job? = null
        var inviteText: String? = null
        var inviteValidUntil: Instant? = null
        var peerId: String? = null
        var peerPosition: ConvoyPosition? = null
        var peerSentAt = 0L
        var peerReceivedMonotonic = 0L
        var peerStopped = false
        var peerEmergencyAt: Instant? = null
        var notSending = false
    }

    private var live: Live? = null
    private val _view = MutableStateFlow(ConvoyView())
    val view: StateFlow<ConvoyView> = _view.asStateFlow()

    val active: Boolean get() = live != null

    /** Skapar en session och en inbjudan. Att trycka på knappen är samtycket till att dela sin position. */
    fun start(serverUrl: String, hours: Long) {
        if (live != null || _view.value.phase != ConvoyPhase.IDLE) return
        val url = serverUrl.trim()
        if (!url.startsWith("https://")) {
            _view.value = ConvoyView(message = "Reläets adress måste börja med https://.")
            return
        }
        _view.value = ConvoyView(phase = ConvoyPhase.CONNECTING)
        scope.launch {
            val now = Instant.now()
            val session = ConvoySession.create(now, now.plus(Duration.ofHours(hours.coerceIn(1, 24))), url)
            val invite = ConvoyInvite.issue(session, now)
            val ownId = ConvoySession.newParticipantId()
            val inviteValidUntil = now.plus(ConvoyLimits.INVITE_VALID)
            val token = relay("anmäla sessionen") {
                RelayClient(url).register(session.sessionId, session.expiresAtUtc.toEpochMilli(), inviteValidUntil.toEpochMilli(), invite.inviteSecret, ownId)
            }
            if (token == null) {
                session.wipeKey()
                return@launch
            }
            begin(session, ownId, token, now).apply {
                inviteText = invite.encode()
                this.inviteValidUntil = inviteValidUntil
            }
            publish()
        }
    }

    /** Går med via QR-kodens text. Att skanna koden och trycka på knappen är samtycket. */
    fun join(inviteText: String) {
        if (live != null || _view.value.phase != ConvoyPhase.IDLE) return
        val invite = ConvoyInvite.decode(inviteText.trim())
        if (invite == null) {
            _view.value = ConvoyView(message = "Det där är ingen inbjudan till Convoy.")
            return
        }
        val now = Instant.now()
        val session = invite.join(now)
        if (session == null) {
            _view.value = ConvoyView(message = "Inbjudan gäller inte längre. Be om en ny kod.")
            return
        }
        if (!invite.serverUrl.startsWith("https://")) {
            session.wipeKey()
            _view.value = ConvoyView(message = "Inbjudan pekar på ett relä utan https. Den godtas inte.")
            return
        }
        _view.value = ConvoyView(phase = ConvoyPhase.CONNECTING)
        scope.launch {
            val ownId = ConvoySession.newParticipantId()
            val joined = relay("lösa in inbjudan") { RelayClient(invite.serverUrl).join(invite.sessionId, invite.inviteSecret, ownId) }
            if (joined == null) {
                session.wipeKey()
                return@launch
            }
            begin(session, ownId, joined.accessToken, now)
            publish()
        }
    }

    private suspend fun <T> relay(what: String, call: () -> T): T? =
        try {
            withContext(Dispatchers.IO) { call() }
        } catch (e: IOException) {
            val status = (e as? RelayException)?.status
            val why = when (status) {
                409 -> "Inbjudan är redan använd."
                410 -> "Inbjudan har gått ut."
                403 -> "Reläet godtog inte inbjudan."
                null -> "Reläet gick inte att nå."
                else -> "Reläet svarade $status."
            }
            log("CONVOY: kunde inte $what: ${e.javaClass.simpleName} ${e.message}")
            _view.value = ConvoyView(message = why)
            null
        }

    private suspend fun begin(session: ConvoySession, ownId: String, token: String, now: Instant): Live {
        val transport = HttpConvoyTransport()
        val l = Live(
            session, ownId, ConvoySender(session, ownId), ConvoyReceiver(session, ownId), transport,
            ConvoyConsent(session.sessionId, ownId, now, session.expiresAtUtc),
        )
        live = l
        log("CONVOY: delning startad, slutar ${session.expiresAtUtc}")
        transport.open(ConvoySessionCredentials(session.serverUrl, session.sessionId, token))
        l.job = scope.launch {
            launch { transport.incoming.collect { receive(l, it) } }
            launch { transport.state.collect { publish() } }
            var tick = 0
            while (isActive) {
                val at = Instant.now()
                if (l.sender.refusal(at) == SendRefusal.EXPIRED) {
                    log("CONVOY: sluttiden nådd, delningen avslutas")
                    scope.launch { stop() }
                    return@launch
                }
                if (tick % SEND_EVERY_TICKS == 0) sendPosition(l, at)
                tick++
                publish()
                delay(TICK_MILLIS)
            }
        }
        return l
    }

    private suspend fun sendPosition(l: Live, at: Instant) {
        val fix = ownFix()
        l.notSending = fix == null
        if (fix == null) return
        val position = ConvoyPosition(
            fix.point.lat, fix.point.lon, fix.accuracyMeters.toFloat(), at.toEpochMilli() - (fix.fixAgeSeconds * 1000).toLong(),
        )
        val sealed = l.sender.sealPosition(position, at) as? SealOutcome.Sealed ?: return
        try {
            l.transport.send(sealed.envelope)
        } catch (e: IOException) {
            // Läget syns i transportens tillstånd. Nästa position ersätter den här.
        }
    }

    private fun receive(l: Live, envelope: se.pundaren.guide.convoy.SealedEnvelope) {
        when (val outcome = l.receiver.accept(envelope, Instant.now())) {
            is ReceiveOutcome.Position -> {
                l.peerId = outcome.senderId
                l.peerPosition = outcome.position
                l.peerSentAt = outcome.sentAtUtcMillis
                l.peerReceivedMonotonic = SystemClock.elapsedRealtime()
                l.inviteText = null
            }
            is ReceiveOutcome.Emergency -> {
                l.peerId = outcome.senderId
                l.peerEmergencyAt = Instant.now()
                log("CONVOY: nödsignal från den andra")
            }
            is ReceiveOutcome.Revoked -> {
                l.peerStopped = true
                l.peerPosition = null
                log("CONVOY: den andra har slutat dela")
            }
            is ReceiveOutcome.Rejected -> log("CONVOY: kuvert kastat (${outcome.reason})")
        }
        publish()
    }

    /** Nödsignalen (07, 3.6). Sänds bara medan en delning pågår. */
    fun sendEmergency() {
        val l = live ?: return
        scope.launch {
            val sealed = l.sender.sealEmergency(Instant.now()) as? SealOutcome.Sealed ?: return@launch
            try {
                l.transport.send(sealed.envelope)
                log("CONVOY: nödsignal sänd")
            } catch (e: IOException) {
                log("CONVOY: nödsignalen kom inte fram (${e.message})")
                if (live === l) _view.value = _view.value.copy(message = "Nödsignalen kom inte fram till reläet.")
            }
        }
    }

    /** Anropas från skärmen: avslutar delningen även om rutan stängs under tiden. */
    fun requestStop() {
        scope.launch { stop() }
    }

    /** "Sluta dela": säger det till den andra, spärrar sig själv hos reläet och raderar nyckeln. */
    suspend fun stop() {
        val l = live ?: run {
            _view.value = ConvoyView()
            return
        }
        live = null
        l.job?.cancel()
        withContext(NonCancellable) {
            val last = l.sender.revoke(Instant.now())
            try {
                if (last != null) l.transport.send(last) else l.transport.revokeSelf()
            } catch (e: IOException) {
                // Reläet nås inte. Nyckeln raderas ändå, och inget mer sänds härifrån.
            }
            l.transport.close()
            l.receiver.end()
        }
        log("CONVOY: delning avslutad")
        _view.value = ConvoyView(message = "Delningen är avslutad. Nyckeln är raderad.")
    }

    fun clearMessage() {
        _view.value = _view.value.copy(message = null)
    }

    private fun publish() {
        val l = live ?: return
        val now = Instant.now()
        if (l.inviteValidUntil?.let { !now.isBefore(it) } == true) l.inviteText = null
        val peer = l.peerPosition?.let { p ->
            val age = ConvoyFreshness.ageSeconds(now, p.fixTimeUtcMillis, l.peerSentAt, SystemClock.elapsedRealtime(), l.peerReceivedMonotonic)
            ConvoyPeer(GeoPoint(p.lat, p.lon), p.accuracyMeters, ConvoyFreshness.present(age, now, ZoneId.systemDefault()))
        }
        _view.value = ConvoyView(
            phase = ConvoyPhase.SHARING,
            inviteText = l.inviteText,
            expiresAtUtc = l.session.expiresAtUtc,
            transport = l.transport.state.value,
            verificationCode = l.peerId?.let { ConvoyVerification.code(l.session, l.ownId, it) },
            peer = peer,
            peerStopped = l.peerStopped,
            peerEmergencyAtUtc = l.peerEmergencyAt,
            notSending = l.notSending,
            message = _view.value.message,
        )
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val SEND_EVERY_TICKS = 5
    }
}
