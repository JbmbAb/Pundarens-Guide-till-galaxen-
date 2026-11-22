package se.pundaren.guide.convoy

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant

/** En position som delas (07, 3.2). Klartexten finns bara på de två telefonerna. */
data class ConvoyPosition(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    /** När positionen togs, enligt avsändarens klocka. */
    val fixTimeUtcMillis: Long,
    val speedMps: Float? = null,
    val headingDegrees: Float? = null,
) {
    /** Alltid lika många byte, vilka värden som än står i (07 T-10: fast meddelandestorlek). */
    internal fun encode(): ByteArray = ByteBuffer.allocate(ENCODED_BYTES)
        .putDouble(lat).putDouble(lon).putFloat(accuracyMeters).putLong(fixTimeUtcMillis)
        .putFloat(speedMps ?: Float.NaN).putFloat(headingDegrees ?: Float.NaN)
        .array()

    companion object {
        const val ENCODED_BYTES = 8 + 8 + 4 + 8 + 4 + 4

        internal fun decode(bytes: ByteArray): ConvoyPosition? {
            if (bytes.size != ENCODED_BYTES) return null
            val b = ByteBuffer.wrap(bytes)
            val lat = b.double; val lon = b.double; val accuracy = b.float; val fix = b.long
            val speed = b.float; val heading = b.float
            if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            return ConvoyPosition(lat, lon, accuracy, fix, speed.takeUnless { it.isNaN() }, heading.takeUnless { it.isNaN() })
        }
    }
}

/** Varför avsändaren vägrade sända. */
enum class SendRefusal { EXPIRED, REVOKED, NO_KEY }

sealed interface SealOutcome {
    data class Sealed(val envelope: SealedEnvelope) : SealOutcome
    data class Refused(val reason: SendRefusal) : SealOutcome
}

/**
 * Avsändarsidan. **Tidsgränsen sitter här** (byggplanen; 07, 3.4): sluttiden och återkallelsen
 * kontrolleras före varje försegling, så efter sluttiden finns inget kuvert att sända, oavsett vad
 * servern gör. Kraschar appen sänds ingenting, eftersom inget sänds utan ett anrop hit.
 *
 * Löpnumret är det större av föregående nummer plus ett och klockan i millisekunder. Det ökar då
 * strikt även efter en omstart av appen. Går klockan bakåt över en omstart kastar mottagaren
 * kuverten som återspelning: ingen position visas, hellre än en som kan vara gammal.
 */
class ConvoySender(
    private val session: ConvoySession,
    val senderId: String,
    private val aead: Aead = AesGcmAead,
    private val random: SecureRandom = SecureRandom(),
) {
    private var lastSeq = 0L
    private var revoked = false

    val isRevoked: Boolean get() = synchronized(this) { revoked }

    /** Sant om en sändning skulle vägras just nu. Det är det här värdet en schemalagd sändning ska läsa. */
    fun refusal(nowUtc: Instant): SendRefusal? = synchronized(this) {
        when {
            revoked -> SendRefusal.REVOKED
            !session.hasKey -> SendRefusal.NO_KEY
            session.isExpired(nowUtc) -> SendRefusal.EXPIRED
            else -> null
        }
    }

    fun sealPosition(position: ConvoyPosition, nowUtc: Instant): SealOutcome = seal(EnvelopeKind.POSITION, position.encode(), nowUtc)

    /** Nödsignalen (07, 3.6). Samma tidsgräns som allt annat: en avslutad delning sänder ingenting. */
    fun sealEmergency(nowUtc: Instant): SealOutcome = seal(EnvelopeKind.EMERGENCY, ByteArray(ConvoyPosition.ENCODED_BYTES), nowUtc)

    /**
     * "Sluta dela" (07, 3.4): ger ett sista kuvert som säger det till den andra telefonen, slutar
     * sända och raderar nyckeln. Null om delningen redan var slut; nyckeln raderas ändå.
     */
    fun revoke(nowUtc: Instant): SealedEnvelope? = synchronized(this) {
        val last = (seal(EnvelopeKind.REVOKE, ByteArray(ConvoyPosition.ENCODED_BYTES), nowUtc) as? SealOutcome.Sealed)?.envelope
        revoked = true
        session.wipeKey()
        last
    }

    private fun seal(kind: EnvelopeKind, plaintext: ByteArray, nowUtc: Instant): SealOutcome = synchronized(this) {
        refusal(nowUtc)?.let { return SealOutcome.Refused(it) }
        val key = session.keyOrNull() ?: return SealOutcome.Refused(SendRefusal.NO_KEY)
        val sentAt = nowUtc.toEpochMilli()
        val seq = maxOf(lastSeq + 1, sentAt)
        lastSeq = seq
        val nonce = ByteArray(aead.nonceBytes).also(random::nextBytes)
        val aad = ConvoyBytes.aad(session.sessionId, senderId, seq, sentAt, kind)
        SealOutcome.Sealed(SealedEnvelope(session.sessionId, senderId, seq, sentAt, kind, nonce, aead.seal(key, nonce, plaintext, aad)))
    }
}

/** Varför mottagaren kastade ett kuvert (07, 3.3). */
enum class RejectReason { WRONG_SESSION, OWN_MESSAGE, NO_KEY, NOT_AUTHENTIC, REPLAY, AFTER_EXPIRY, AFTER_REVOCATION, MALFORMED }

sealed interface ReceiveOutcome {
    data class Rejected(val reason: RejectReason) : ReceiveOutcome

    /**
     * En äkta position. [current] är falskt när positionen var äldre än femton minuter redan när den
     * kom fram: den sparas då som historik men visas aldrig som aktuellt läge (07, 3.3).
     */
    data class Position(val senderId: String, val position: ConvoyPosition, val sentAtUtcMillis: Long, val current: Boolean) : ReceiveOutcome
    data class Emergency(val senderId: String, val sentAtUtcMillis: Long) : ReceiveOutcome
    data class Revoked(val senderId: String, val sentAtUtcMillis: Long) : ReceiveOutcome
}

/**
 * Mottagarsidan med kontrollerna i 07, 3.3. Ett kuvert kastas om äkthetskontrollen fallerar, om
 * löpnumret inte är högre än det senast godtagna från samma avsändare, eller om det är sänt efter
 * sessionens sluttid eller efter avsändarens återkallelse.
 */
class ConvoyReceiver(
    private val session: ConvoySession,
    private val ownId: String,
    private val aead: Aead = AesGcmAead,
) {
    private val lastSeq = HashMap<String, Long>()
    private val revokedAt = HashMap<String, Long>()

    fun accept(envelope: SealedEnvelope, nowUtc: Instant): ReceiveOutcome = synchronized(this) {
        if (envelope.sessionId != session.sessionId) return ReceiveOutcome.Rejected(RejectReason.WRONG_SESSION)
        if (envelope.senderId == ownId) return ReceiveOutcome.Rejected(RejectReason.OWN_MESSAGE)
        val key = session.keyOrNull() ?: return ReceiveOutcome.Rejected(RejectReason.NO_KEY)
        val aad = ConvoyBytes.aad(envelope.sessionId, envelope.senderId, envelope.seq, envelope.sentAtUtcMillis, envelope.kind)
        val plaintext = aead.open(key, envelope.nonce, envelope.ciphertext, aad)
            ?: return ReceiveOutcome.Rejected(RejectReason.NOT_AUTHENTIC)
        // Härifrån är kuvertet äkta: det är förseglat med sessionsnyckeln och ingenting i det är ändrat.
        if (envelope.seq <= (lastSeq[envelope.senderId] ?: Long.MIN_VALUE)) return ReceiveOutcome.Rejected(RejectReason.REPLAY)
        if (envelope.sentAtUtcMillis >= session.expiresAtUtc.toEpochMilli()) return ReceiveOutcome.Rejected(RejectReason.AFTER_EXPIRY)
        revokedAt[envelope.senderId]?.let { if (envelope.sentAtUtcMillis >= it) return ReceiveOutcome.Rejected(RejectReason.AFTER_REVOCATION) }
        lastSeq[envelope.senderId] = envelope.seq
        when (envelope.kind) {
            EnvelopeKind.REVOKE -> {
                revokedAt[envelope.senderId] = envelope.sentAtUtcMillis
                ReceiveOutcome.Revoked(envelope.senderId, envelope.sentAtUtcMillis)
            }
            EnvelopeKind.EMERGENCY -> ReceiveOutcome.Emergency(envelope.senderId, envelope.sentAtUtcMillis)
            EnvelopeKind.POSITION -> {
                val position = ConvoyPosition.decode(plaintext) ?: return ReceiveOutcome.Rejected(RejectReason.MALFORMED)
                val ageAtReceipt = (nowUtc.toEpochMilli() - position.fixTimeUtcMillis) / 1000
                ReceiveOutcome.Position(envelope.senderId, position, envelope.sentAtUtcMillis, current = ageAtReceipt <= ConvoyLimits.LAST_SEEN_SECONDS)
            }
        }
    }

    /** Sessionen är slut (07 T-04): nyckeln raderas och inget mer kan läsas. Historiken raderas av den som håller den. */
    fun end() = session.wipeKey()
}
