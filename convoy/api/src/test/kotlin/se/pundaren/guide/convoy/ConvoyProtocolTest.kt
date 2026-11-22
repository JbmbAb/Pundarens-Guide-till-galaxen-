package se.pundaren.guide.convoy

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Convoy för två (07): hotmodellens tester mot protokollets logik. Allt körs på JVM med två
 * "telefoner" i samma process. Ingen transport, ingen server och inga verkliga telefoner (G7 är
 * NOT_VERIFIED tills det är gjort på två enheter).
 */
class ConvoyProtocolTest {
    private val t0 = Instant.parse("2026-11-21T10:00:00Z")
    private val stockholm = ZoneId.of("Europe/Stockholm")
    private fun at(seconds: Long) = t0.plusSeconds(seconds)

    /** Två telefoner: A skapar sessionen och bjuder in, B går med via QR-kodens text. */
    private class Pair(val a: ConvoySession, val b: ConvoySession, val idA: String, val idB: String) {
        val senderA = ConvoySender(a, idA)
        val senderB = ConvoySender(b, idB)
        val receiverA = ConvoyReceiver(a, idA)
        val receiverB = ConvoyReceiver(b, idB)
    }

    private fun pair(hours: Long = 2, bEnds: Instant? = null): Pair {
        val a = ConvoySession.create(t0, t0.plus(Duration.ofHours(hours)), "wss://exempel.invalid/convoy")
        val qr = ConvoyInvite.issue(a, t0).encode()
        val b = ConvoyInvite.decode(qr)!!.join(at(60), bEnds ?: a.expiresAtUtc)!!
        return Pair(a, b, ConvoySession.newParticipantId(), ConvoySession.newParticipantId())
    }

    private fun position(atSeconds: Long, lat: Double = 61.1201175, lon: Double = 14.6222536) =
        ConvoyPosition(lat, lon, 5f, at(atSeconds).toEpochMilli(), speedMps = 1.4f, headingDegrees = 270f)

    private fun sealed(outcome: SealOutcome): SealedEnvelope = (outcome as SealOutcome.Sealed).envelope

    // ---------------- Sessionen och inbjudan ----------------

    @Test fun `en session har slumpat namn, 256 bitars nyckel och en sluttid högst ett dygn bort`() {
        val one = ConvoySession.create(t0, at(3_600), "wss://exempel.invalid/convoy")
        val two = ConvoySession.create(t0, at(3_600), "wss://exempel.invalid/convoy")
        assertEquals(32, one.sessionId.length)
        assertNotEquals(one.sessionId, two.sessionId)
        assertEquals(32, one.keyOrNull()!!.size)
        assertFalse(one.keyOrNull()!!.contentEquals(two.keyOrNull()!!))
        assertFalse(one.isExpired(at(3_599))); assertTrue(one.isExpired(at(3_600)))
        assertThrows { ConvoySession.create(t0, t0, "x") }
        assertThrows { ConvoySession.create(t0, t0.plus(Duration.ofHours(24)).plusSeconds(1), "x") }
        assertNotNull(ConvoySession.create(t0, t0.plus(Duration.ofHours(24)), "x"))
    }

    @Test fun `inbjudan går runt som text och gäller i tio minuter`() {
        val a = ConvoySession.create(t0, at(7_200), "wss://exempel.invalid/convoy")
        val invite = ConvoyInvite.issue(a, t0)
        val text = invite.encode()
        assertTrue(text.startsWith("pundaren-convoy:1:"))
        val read = ConvoyInvite.decode(text)!!
        assertEquals(a.sessionId, read.sessionId); assertEquals(a.serverUrl, read.serverUrl)
        assertEquals(a.expiresAtUtc, read.expiresAtUtc)
        assertArrayEquals(invite.inviteSecret, read.inviteSecret)
        assertTrue(read.isValid(at(599))); assertFalse("tio minuter har gått", read.isValid(at(600)))
        assertNull(read.join(at(600)))
        assertNull(ConvoyInvite.decode("pundaren-convoy:1:inte-en-inbjudan"))
        assertNull(ConvoyInvite.decode("https://exempel.invalid/"))
        assertNull(ConvoyInvite.decode(text.dropLast(6)))
    }

    @Test fun `den som går med kan välja en tidigare sluttid men aldrig en senare`() {
        val a = ConvoySession.create(t0, at(7_200), "wss://exempel.invalid/convoy")
        val invite = ConvoyInvite.decode(ConvoyInvite.issue(a, t0).encode())!!
        assertEquals(at(3_600), invite.join(at(60), at(3_600))!!.expiresAtUtc)
        assertEquals(at(7_200), invite.join(at(60))!!.expiresAtUtc)
        assertNull("senare än A:s sluttid", invite.join(at(60), at(7_201)))
        assertNull("sluttid som redan har passerat", invite.join(at(60), at(30)))
    }

    @Test fun `verifieringskoden är densamma på båda telefonerna och en annan för en obehörig`() {
        val p = pair()
        val code = ConvoyVerification.code(p.a, p.idA, p.idB)!!
        assertTrue(code, Regex("""\d{3} \d{3}""").matches(code))
        assertEquals("ordningen spelar ingen roll", code, ConvoyVerification.code(p.b, p.idB, p.idA))
        // Någon som har fotograferat QR-koden och löst in den får ett eget id, och koden stämmer inte (T-01).
        var differs = 0
        repeat(20) { if (ConvoyVerification.code(p.a, p.idA, ConvoySession.newParticipantId()) != code) differs++ }
        assertTrue("minst 19 av 20 obehöriga id ger en annan kod, var $differs", differs >= 19)
        p.a.wipeKey()
        assertNull(ConvoyVerification.code(p.a, p.idA, p.idB))
    }

    @Test fun `samtycket gäller bara mellan godkännandet och sluttiden och inte efter återkallelse`() {
        val consent = ConvoyConsent("s", "p", grantedAtUtc = at(0), expiresAtUtc = at(3_600))
        assertTrue(consent.isActive(at(10))); assertFalse(consent.isActive(at(3_600))); assertFalse(consent.isActive(at(-1)))
        assertFalse(consent.copy(revokedAtUtc = at(100)).isActive(at(200)))
    }

    // ---------------- Kuverten ----------------

    @Test fun `en position går från den ena telefonen till den andra och bara dit`() {
        val p = pair()
        val envelope = sealed(p.senderA.sealPosition(position(120), at(121)))
        val got = p.receiverB.accept(envelope, at(122)) as ReceiveOutcome.Position
        assertEquals(position(120), got.position)
        assertEquals(p.idA, got.senderId); assertTrue(got.current)
        // Avsändarens egen telefon tar inte emot sitt eget kuvert.
        assertEquals(ReceiveOutcome.Rejected(RejectReason.OWN_MESSAGE), p.receiverA.accept(envelope, at(122)))
    }

    @Test fun `mellanhanden ser bara oläslig data av fast storlek (T-05, T-10)`() {
        val p = pair()
        val near = sealed(p.senderA.sealPosition(position(10, lat = 0.0, lon = 0.0), at(10)))
        val far = sealed(p.senderA.sealPosition(position(20, lat = 61.1201175, lon = 14.6222536).copy(speedMps = null, headingDegrees = null), at(20)))
        assertEquals("alla positionskuvert är lika stora", near.ciphertext.size, far.ciphertext.size)
        assertEquals(ConvoyPosition.ENCODED_BYTES + 16, far.ciphertext.size) // klartext plus GCM-märke
        val latitude = ByteBuffer.allocate(8).putDouble(61.1201175).array()
        assertFalse("latituden står inte i klartext i kuvertet", far.ciphertext.asList().windowed(8).any { it == latitude.asList() })
        assertNotEquals("ny nonce varje gång", near.nonce.asList(), far.nonce.asList())
        // Samma position två gånger ger olika chiffertext.
        val again = sealed(p.senderA.sealPosition(position(20).copy(speedMps = null, headingDegrees = null), at(21)))
        assertNotEquals(far.ciphertext.asList(), again.ciphertext.asList())
    }

    @Test fun `varje ändring av ett kuvert upptäcks (T-06)`() {
        val p = pair()
        val e = sealed(p.senderA.sealPosition(position(120), at(121)))
        val flipped = e.ciphertext.also { it[3] = (it[3].toInt() xor 1).toByte() }
        val forged = listOf(
            e.altered(ciphertext = flipped),
            e.altered(nonce = e.nonce.also { it[0] = (it[0].toInt() xor 1).toByte() }),
            e.altered(seq = e.seq + 1),
            e.altered(sentAtUtcMillis = e.sentAtUtcMillis + 1_000),
            e.altered(senderId = "någon-annan"),
            e.altered(kind = EnvelopeKind.EMERGENCY),
            e.altered(ciphertext = e.ciphertext.copyOf(e.ciphertext.size - 1)),
        )
        forged.forEach { assertEquals(ReceiveOutcome.Rejected(RejectReason.NOT_AUTHENTIC), p.receiverB.accept(it, at(122))) }
        assertEquals(ReceiveOutcome.Rejected(RejectReason.WRONG_SESSION), p.receiverB.accept(e.altered(sessionId = "annan-session"), at(122)))
        // Det orörda kuvertet godtas fortfarande: de förfalskade har inte förbrukat löpnumret.
        assertTrue(p.receiverB.accept(e, at(122)) is ReceiveOutcome.Position)
    }

    @Test fun `fel nyckel läser ingenting`() {
        val p = pair()
        val outsider = ConvoyReceiver(ConvoySession(p.a.sessionId, ByteArray(32).also(SecureRandom()::nextBytes), p.a.expiresAtUtc, p.a.serverUrl), "utomstående")
        val e = sealed(p.senderA.sealPosition(position(120), at(121)))
        assertEquals(ReceiveOutcome.Rejected(RejectReason.NOT_AUTHENTIC), outsider.accept(e, at(122)))
    }

    @Test fun `ett kuvert kan inte spelas upp igen (T-02)`() {
        val p = pair()
        val first = sealed(p.senderA.sealPosition(position(100), at(101)))
        val second = sealed(p.senderA.sealPosition(position(105), at(106)))
        assertTrue(second.seq > first.seq)
        assertTrue(p.receiverB.accept(first, at(102)) is ReceiveOutcome.Position)
        assertTrue(p.receiverB.accept(second, at(107)) is ReceiveOutcome.Position)
        assertEquals(ReceiveOutcome.Rejected(RejectReason.REPLAY), p.receiverB.accept(second, at(108)))
        assertEquals("ett äldre kuvert som kommer efter ett nyare kastas", ReceiveOutcome.Rejected(RejectReason.REPLAY), p.receiverB.accept(first, at(109)))
    }

    @Test fun `löpnumret ökar strikt även när flera kuvert förseglas i samma millisekund och efter omstart`() {
        val p = pair()
        val burst = List(50) { sealed(p.senderA.sealPosition(position(100), at(101))).seq }
        assertEquals(burst.sorted(), burst); assertEquals(50, burst.toSet().size)
        // Appen startas om: en ny avsändare för samma session fortsätter över det gamla numret när klockan har gått.
        val restarted = ConvoySender(p.a, p.idA)
        assertTrue(sealed(restarted.sealPosition(position(160), at(161))).seq > burst.last())
    }

    // ---------------- Tidsgränsen och återkallelsen ----------------

    @Test fun `sändningen upphör vid sluttiden och den andra telefonen visar senast sedd med rätt ålder (G7)`() {
        val p = pair(hours = 1)
        val end = 3_600L
        var received = 0
        var lastFix = 0L
        var lastSentAt = 0L
        var lastReceivedMono = 0L
        var refusedAfterEnd = 0
        // A försöker sända var femte sekund i 80 minuter. Telefonen ligger i fickan; ingen rör den.
        for (s in 60L..4_800L step 5) {
            when (val outcome = p.senderA.sealPosition(position(s), at(s))) {
                is SealOutcome.Sealed -> {
                    assertTrue("inget förseglas efter sluttiden", s < end)
                    val got = p.receiverB.accept(outcome.envelope, at(s + 1)) as ReceiveOutcome.Position
                    received++; lastFix = got.position.fixTimeUtcMillis; lastSentAt = got.sentAtUtcMillis; lastReceivedMono = (s + 1) * 1000
                }
                is SealOutcome.Refused -> { assertEquals(SendRefusal.EXPIRED, outcome.reason); assertTrue(s >= end); refusedAfterEnd++ }
            }
        }
        assertEquals(708, received)           // 60, 65, … 3595
        assertEquals(241, refusedAfterEnd)    // 3600, 3605, … 4800
        assertEquals(at(3_595).toEpochMilli(), lastFix)
        assertEquals(SendRefusal.EXPIRED, p.senderA.refusal(at(end)))

        // Tio minuter efter sista positionen visar B "senast sedd" med klockslaget då den togs.
        fun shown(nowSeconds: Long): PeerPresentation =
            ConvoyFreshness.present(ConvoyFreshness.ageSeconds(at(nowSeconds), lastFix, lastSentAt, nowSeconds * 1000, lastReceivedMono), at(nowSeconds), stockholm)
        val tenMinutes = shown(3_595 + 600)
        assertEquals(Freshness.LAST_SEEN, tenMinutes.freshness)
        assertEquals(600, tenMinutes.ageSeconds)
        assertEquals("senast sedd 11.59", tenMinutes.label) // 10.59.55 UTC är 11.59 svensk tid
        assertTrue(tenMinutes.showOnMap)
        // Efter mer än en kvart ritas ingen prick alls.
        val twentyMinutes = shown(3_595 + 1_200)
        assertEquals(Freshness.STALE, twentyMinutes.freshness)
        assertFalse(twentyMinutes.showOnMap)
        assertEquals("senast sedd 11.59", twentyMinutes.label)
    }

    @Test fun `mottagaren kastar det som är sänt efter sluttiden även om det är äkta`() {
        val p = pair(hours = 1)
        // En avsändare med nyckeln men utan tidsgräns (en trasig eller ändrad app) har en egen session med senare sluttid.
        val rogue = ConvoySender(ConvoySession(p.a.sessionId, p.a.keyOrNull()!!, at(86_400), p.a.serverUrl), p.idA)
        val late = sealed(rogue.sealPosition(position(3_700), at(3_700)))
        assertEquals(ReceiveOutcome.Rejected(RejectReason.AFTER_EXPIRY), p.receiverB.accept(late, at(3_701)))
    }

    @Test fun `den som gick med med tidigare sluttid slutar sända då`() {
        val p = pair(hours = 2, bEnds = at(1_800))
        assertTrue(p.senderB.sealPosition(position(1_799), at(1_799)) is SealOutcome.Sealed)
        assertEquals(SealOutcome.Refused(SendRefusal.EXPIRED), p.senderB.sealPosition(position(1_800), at(1_800)))
        assertNull("A sänder fortfarande", p.senderA.refusal(at(1_800)))
    }

    @Test fun `sluta dela stoppar sändningen direkt, säger det till den andra och raderar nyckeln`() {
        val p = pair()
        val before = sealed(p.senderA.sealPosition(position(100), at(100)))
        assertTrue(p.receiverB.accept(before, at(101)) is ReceiveOutcome.Position)
        val revoke = p.senderA.revoke(at(200))!!
        assertTrue(p.senderA.isRevoked); assertFalse(p.a.hasKey)
        assertEquals(SealOutcome.Refused(SendRefusal.REVOKED), p.senderA.sealPosition(position(201), at(201)))
        assertEquals(SealOutcome.Refused(SendRefusal.REVOKED), p.senderA.sealEmergency(at(202)))
        assertEquals(ReceiveOutcome.Revoked(p.idA, at(200).toEpochMilli()), p.receiverB.accept(revoke, at(201)))
        // Något som utger sig för att vara sänt av A efter återkallelsen kastas.
        val after = sealed(ConvoySender(ConvoySession(p.b.sessionId, p.b.keyOrNull()!!, p.b.expiresAtUtc, p.b.serverUrl), p.idA).sealPosition(position(300), at(300)))
        assertEquals(ReceiveOutcome.Rejected(RejectReason.AFTER_REVOCATION), p.receiverB.accept(after, at(301)))
        // A:s telefon kan inte heller läsa något mer.
        assertEquals(ReceiveOutcome.Rejected(RejectReason.NO_KEY), p.receiverA.accept(sealed(p.senderB.sealPosition(position(310), at(310))), at(311)))
        assertNull("en andra återkallelse ger inget kuvert", p.senderA.revoke(at(400)))
    }

    @Test fun `nödsignalen kommer fram som nödsignal och inte som position`() {
        val p = pair()
        val e = sealed(p.senderA.sealEmergency(at(500)))
        assertEquals(EnvelopeKind.EMERGENCY, e.kind)
        assertEquals("lika stor som ett positionskuvert", sealed(p.senderA.sealPosition(position(501), at(501))).ciphertext.size, e.ciphertext.size)
        assertEquals(ReceiveOutcome.Emergency(p.idA, at(500).toEpochMilli()), p.receiverB.accept(e, at(501)))
    }

    // ---------------- Ärlig ålder ----------------

    @Test fun `åldern delas in i fyra klasser och står alltid med`() {
        fun shown(age: Long) = ConvoyFreshness.present(age, at(3_600), stockholm)
        assertEquals(PeerPresentation(Freshness.FRESH, 0, "0 s", true), shown(0))
        assertEquals(PeerPresentation(Freshness.FRESH, 30, "30 s", true), shown(30))
        assertEquals(PeerPresentation(Freshness.RECENT, 31, "för 31 s sedan", true), shown(31))
        assertEquals(PeerPresentation(Freshness.RECENT, 120, "för 120 s sedan", true), shown(120))
        assertEquals(PeerPresentation(Freshness.LAST_SEEN, 121, "senast sedd 11.57", true), shown(121))
        assertEquals(PeerPresentation(Freshness.LAST_SEEN, 900, "senast sedd 11.45", true), shown(900))
        assertEquals(PeerPresentation(Freshness.STALE, 901, "senast sedd 11.44", false), shown(901))
    }

    @Test fun `en position som redan var gammal när den kom fram visas aldrig som aktuell`() {
        val p = pair()
        val old = sealed(p.senderA.sealPosition(position(100), at(1_100))) // togs 1 000 s innan den sändes
        val got = p.receiverB.accept(old, at(1_101)) as ReceiveOutcome.Position
        assertFalse(got.current)
        val age = ConvoyFreshness.ageSeconds(at(1_101), got.position.fixTimeUtcMillis, got.sentAtUtcMillis, 0, 0)
        assertEquals(1_001, age); assertEquals(Freshness.STALE, ConvoyFreshness.classify(age))
    }

    @Test fun `går klockorna olika visas den högre åldern (T-07)`() {
        val fix = at(1_000).toEpochMilli(); val sent = at(1_002).toEpochMilli()
        // Mottagarens klocka går fem minuter efter avsändarens: den naiva åldern blir negativ.
        val behind = ConvoyFreshness.ageSeconds(at(1_003 - 300), fix, sent, nowMonotonicMillis = 61_000, receivedMonotonicMillis = 1_000)
        assertEquals("60 s sedan mottagandet plus 2 s före sändningen", 62, behind)
        // Mottagarens klocka går fem minuter före: den naiva åldern är den högre och visas.
        val ahead = ConvoyFreshness.ageSeconds(at(1_063 + 300), fix, sent, nowMonotonicMillis = 61_000, receivedMonotonicMillis = 1_000)
        assertEquals(363, ahead)
        // Åldern blir aldrig negativ, och aldrig lägre än positionens ålder när den sändes.
        assertEquals(2, ConvoyFreshness.ageSeconds(at(0), fix, sent, 0, 0))
        assertEquals(0, ConvoyFreshness.ageSeconds(at(0), fix, fix, 0, 0))
    }

    // ---------------- Mötespunkten ----------------

    @Test fun `mötespunkten är den plats där den som har längst dit får kortast väg`() {
        //                 kandidat:   0       1       2       3
        val a = listOf<Double?>(600.0, 300.0, 900.0, null)
        val b = listOf<Double?>(700.0, 1_500.0, 800.0, 100.0)
        val choice = MeetingPoint.choose(a, b)!!
        assertEquals(0, choice.index) // längsta restid 700 s; kandidat 2 ger 900 s och kandidat 1 ger 1 500 s
        assertEquals(100.0, choice.waitSeconds, 0.0)
        assertNull("ingen plats som båda når", MeetingPoint.choose(listOf(null, 10.0), listOf(10.0, null)))
        // Vid lika längsta restid väljs den med minst sammanlagd restid.
        assertEquals(1, MeetingPoint.choose(listOf(600.0, 600.0), listOf(500.0, 100.0))!!.index)
    }

    private fun assertThrows(block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { return }
        throw AssertionError("förväntade IllegalArgumentException")
    }
}
