package se.pundaren.guide.convoy.http

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import se.pundaren.guide.convoy.ConvoyInvite
import se.pundaren.guide.convoy.ConvoyLimits
import se.pundaren.guide.convoy.ConvoyPosition
import se.pundaren.guide.convoy.ConvoyReceiver
import se.pundaren.guide.convoy.ConvoySender
import se.pundaren.guide.convoy.ConvoySession
import se.pundaren.guide.convoy.ConvoySessionCredentials
import se.pundaren.guide.convoy.ReceiveOutcome
import se.pundaren.guide.convoy.SealOutcome
import se.pundaren.guide.convoy.TransportState
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * Transporten mot det riktiga reläet: testet startar `relay/convoy-relay.mjs` med Node på en ledig
 * port. Saknas Node på datorn hoppas testerna över, och det syns i testrapporten.
 */
class HttpConvoyTransportTest {
    private var relay: Process? = null
    private lateinit var url: String
    private val transports = mutableListOf<HttpConvoyTransport>()

    @Before fun startRelay() {
        val script = File(System.getProperty("guide.repoRoot"), "relay/convoy-relay.mjs")
        val started = try {
            ProcessBuilder("node", script.path).apply {
                environment()["PORT"] = "0"
                environment()["HOST"] = "127.0.0.1"
                redirectErrorStream(true)
            }.start()
        } catch (e: IOException) {
            null
        }
        assumeTrue("Node saknas, reläet kan inte startas", started != null)
        relay = started
        val line = started!!.inputStream.bufferedReader().readLine().orEmpty()
        assertTrue("Reläet startade inte: $line", line.startsWith("LISTENING "))
        url = "http://127.0.0.1:" + line.removePrefix("LISTENING ").trim()
    }

    @After fun stopRelay() {
        runBlocking { transports.forEach { it.close() } }
        relay?.destroyForcibly()?.waitFor()
    }

    private fun transport() = HttpConvoyTransport(pollMillis = 50, offlineAfterMillis = 1_500, connectTimeoutMillis = 300, readTimeoutMillis = 2_000)
        .also(transports::add)

    /** A skapar sessionen och anmäler den; B läser QR-kodens text och löser in inbjudan. */
    private class Two(
        val a: ConvoySession, val b: ConvoySession, val idA: String, val idB: String,
        val credentialsA: ConvoySessionCredentials, val credentialsB: ConvoySessionCredentials, val invite: ConvoyInvite,
    )

    private fun two(): Two {
        val now = Instant.now()
        val a = ConvoySession.create(now, now.plus(Duration.ofHours(1)), url)
        val invite = ConvoyInvite.issue(a, now)
        val idA = ConvoySession.newParticipantId()
        val idB = ConvoySession.newParticipantId()
        val tokenA = RelayClient(url).register(
            a.sessionId, a.expiresAtUtc.toEpochMilli(), now.plus(ConvoyLimits.INVITE_VALID).toEpochMilli(), invite.inviteSecret, idA,
        )
        val scanned = ConvoyInvite.decode(invite.encode())!!
        val b = scanned.join(Instant.now())!!
        val joined = RelayClient(scanned.serverUrl).join(scanned.sessionId, scanned.inviteSecret, idB)
        assertEquals(a.expiresAtUtc.toEpochMilli(), joined.expiresAtUtcMillis)
        return Two(
            a, b, idA, idB,
            ConvoySessionCredentials(url, a.sessionId, tokenA), ConvoySessionCredentials(url, b.sessionId, joined.accessToken), invite,
        )
    }

    private suspend fun HttpConvoyTransport.await(wanted: TransportState) = withTimeout(10_000) { state.first { it == wanted } }

    @Test fun `en position går från A till B genom reläet och låses upp med sessionsnyckeln`() = runBlocking {
        val t = two()
        val fromA = transport().also { it.open(t.credentialsA) }
        val toB = transport().also { it.open(t.credentialsB) }
        fromA.await(TransportState.CONNECTED)
        toB.await(TransportState.CONNECTED)

        val position = ConvoyPosition(61.1201175, 14.6222536, 5f, System.currentTimeMillis(), speedMps = 1.4f, headingDegrees = 270f)
        val envelope = (ConvoySender(t.a, t.idA).sealPosition(position, Instant.now()) as SealOutcome.Sealed).envelope
        val arriving = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(10_000) { toB.incoming.first() } }
        val ownEcho = async(start = CoroutineStart.UNDISPATCHED) { runCatching { withTimeout(500) { fromA.incoming.first() } }.getOrNull() }
        fromA.send(envelope)

        val got = ConvoyReceiver(t.b, t.idB).accept(arriving.await(), Instant.now())
        assertTrue("$got", got is ReceiveOutcome.Position)
        assertEquals(position, (got as ReceiveOutcome.Position).position)
        assertEquals(t.idA, got.senderId)
        assertEquals("A får aldrig tillbaka sitt eget kuvert", null, ownEcho.await())
        assertTrue("Klockskillnaden mot reläet på samma dator är liten", Math.abs(toB.serverAheadMillis.value!!) < 5_000)
    }

    @Test fun `inbjudan kan inte lösas in en andra gång`() {
        val t = two()
        try {
            RelayClient(url).join(t.a.sessionId, t.invite.inviteSecret, ConvoySession.newParticipantId())
            fail("Reläet godtog inbjudan två gånger")
        } catch (e: RelayException) {
            assertEquals(409, e.status)
        }
    }

    @Test fun `den som har slutat dela kan inte sända mer`() = runBlocking {
        val t = two()
        val fromB = transport().also { it.open(t.credentialsB) }
        fromB.await(TransportState.CONNECTED)
        fromB.revokeSelf()
        assertEquals(TransportState.OFFLINE, fromB.state.value)

        val position = ConvoyPosition(61.0, 14.0, 5f, System.currentTimeMillis(), speedMps = null, headingDegrees = null)
        val envelope = (ConvoySender(t.b, t.idB).sealPosition(position, Instant.now()) as SealOutcome.Sealed).envelope
        try {
            fromB.send(envelope)
            fail("Reläet tog emot ett kuvert efter att delningen avslutats")
        } catch (e: RelayException) {
            assertEquals(403, e.status)
        }
        assertEquals(TransportState.OFFLINE, fromB.state.value)
    }

    @Test fun `när reläet försvinner blir läget först DEGRADED och sedan OFFLINE`() = runBlocking {
        val t = two()
        val a = transport().also { it.open(t.credentialsA) }
        a.await(TransportState.CONNECTED)
        relay!!.destroyForcibly().waitFor()
        a.await(TransportState.DEGRADED)
        a.await(TransportState.OFFLINE)
        assertFalse(a.state.value == TransportState.CONNECTED)
    }

    @Test fun `utan relä är läget OFFLINE och sändningen misslyckas hörbart`() = runBlocking {
        val t = two()
        relay!!.destroyForcibly().waitFor()
        val a = transport().also { it.open(t.credentialsA) }
        val position = ConvoyPosition(61.0, 14.0, 5f, System.currentTimeMillis(), speedMps = null, headingDegrees = null)
        val envelope = (ConvoySender(t.a, t.idA).sealPosition(position, Instant.now()) as SealOutcome.Sealed).envelope
        try {
            a.send(envelope)
            fail("Sändningen lyckades utan relä")
        } catch (e: IOException) {
            assertEquals(TransportState.OFFLINE, a.state.value)
        }
    }
}
