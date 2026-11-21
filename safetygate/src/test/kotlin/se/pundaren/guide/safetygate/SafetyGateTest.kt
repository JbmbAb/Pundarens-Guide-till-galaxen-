package se.pundaren.guide.safetygate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.Mechanism
import se.pundaren.guide.archive.api.OriginClass
import se.pundaren.guide.archive.api.Visibility
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.engine.api.TailKind
import se.pundaren.guide.safetygate.PresentationDecision.Interrupt
import se.pundaren.guide.safetygate.PresentationDecision.Present
import se.pundaren.guide.safetygate.PresentationDecision.Suppress
import se.pundaren.guide.truth.api.AudioOutput
import se.pundaren.guide.truth.api.NavigationSnapshot
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.api.PositionSnapshot
import se.pundaren.guide.truth.api.SafetySnapshot
import se.pundaren.guide.truth.api.TravelMode
import java.util.SplittableRandom

/** Testmatrisen i 06, avsnitt 4. Värdena läses ur SafetyGateConfig.CANDIDATE (ofrysta). */
class SafetyGateTest {
    private val cfg = SafetyGateConfig.CANDIDATE
    private val gate = SafetyGate(cfg)
    private val now = 100_000L

    private fun tail(
        text: String = "Ett lugnt konstaterande.", ms: Int = 3_000, lineId: String = "C-001",
        visibility: Visibility = Visibility.OPEN, approval: Approval = Approval.APPROVED,
        kind: TailKind = TailKind.SPOKEN, rikard: Boolean = false, validUntil: Long = now + 10_000,
    ) = TailCandidate("TAIL-$lineId", text, lineId, OriginClass.ORIGINAL_VERBATIM, approval, visibility, kind,
        setOf(Mechanism.M1), rikard, ms, "EV-1", validUntil)

    private fun input(
        mode: TravelMode = TravelMode.PASSENGER, speed: Double = 0.0, toManeuver: Double? = null,
        toInstruction: Long? = null, speaking: Boolean = false, rerouting: Boolean = false,
        reroutes: List<Long> = emptyList(), quality: PositionQuality = PositionQuality.GOOD, shownSince: Long? = null,
        emergency: Boolean = false, clearedAt: Long? = null, battery: Int = 80, convoy: Boolean = false,
        contactLost: Boolean = false, audio: AudioOutput = AudioOutput.DEVICE_SPEAKER, recent: List<String> = emptyList(),
        activeRoute: Boolean = true,
    ) = GateInput(
        now,
        SafetySnapshot(emergency, clearedAt, battery, convoy, contactLost, audio),
        NavigationSnapshot(activeRoute, mode, speed, toManeuver, toInstruction, speaking, rerouting, reroutes),
        PositionSnapshot(quality, 5.0, 1.0, shownSince),
        recent,
    )

    private fun reason(d: PresentationDecision) = (d as? Suppress)?.reason

    @Test fun `SG-01 normalt läge ger Present`() =
        assertTrue(gate.decide(input(toManeuver = 70.0, toInstruction = 60_000), tail()) is Present)

    @Test fun `SG-02 manöver om 10 s i fordon ger NO_TIME`() =
        assertEquals(SuppressReason.NO_TIME, reason(gate.decide(input(toManeuver = 10.0, toInstruction = 4_000), tail())))

    @Test fun `SG-02b manöver inom tyst fönster utan känd talinstruktion ger MANEUVER`() =
        assertEquals(SuppressReason.MANEUVER, reason(gate.decide(input(toManeuver = 10.0), tail())))

    @Test fun `SG-03 pågående svans avbryts när manövern går in i tyst fönster`() {
        assertEquals(PresentationDecision.Continue, gate.checkOngoing(input(toManeuver = 40.0), tail()))
        assertEquals(Interrupt(SuppressReason.MANEUVER), gate.checkOngoing(input(toManeuver = cfg.quietWindowVehicleSeconds), tail()))
        assertEquals(Interrupt(SuppressReason.MANEUVER), gate.checkOngoing(input(speaking = true), tail()))
    }

    @Test fun `SG-04 riktningsord i fordonsläge stoppas`() =
        assertEquals(SuppressReason.DIRECTION_WORD, reason(gate.decide(input(), tail("Sväng vänster för Rikard"))))

    @Test fun `SG-05 riktningsord till fots utan manöver tillåts`() =
        assertTrue(gate.decide(input(mode = TravelMode.FOOT, speed = 4.0, toManeuver = 70.0), tail("Sväng vänster för Rikard")) is Present)

    @Test fun `SG-05b till fots i fordonsfart gäller fordonsreglerna`() =
        assertEquals(SuppressReason.DIRECTION_WORD, reason(gate.decide(input(mode = TravelMode.FOOT, speed = 40.0), tail("Sväng vänster"))))

    @Test fun `F-11 rad bara till fots stoppas i fordon och släpps till fots`() {
        val foot = tail("Svängen är korrekt.").copy(footOnly = true)
        assertEquals(SuppressReason.DIRECTION_WORD, reason(gate.decide(input(), foot)))
        val kartan = tail("Kartan har rätt.")
        assertEquals(SuppressReason.FOOT_ONLY_IN_VEHICLE, reason(gate.decide(input(), kartan)))
        assertEquals(SuppressReason.FOOT_ONLY_IN_VEHICLE, reason(gate.decide(input(), kartan.copy(footOnly = true))))
        assertTrue(gate.decide(input(mode = TravelMode.FOOT, speed = 4.0, toManeuver = 70.0), kartan.copy(footOnly = true)) is Present)
    }

    @Test fun `SG-06 nödläge avbryter och stoppar sedan allt`() {
        assertEquals(Interrupt(SuppressReason.EMERGENCY), gate.checkOngoing(input(emergency = true), tail()))
        assertEquals(SuppressReason.EMERGENCY, reason(gate.decide(input(emergency = true), tail())))
    }

    @Test fun `SG-07 nödläge och 1000 slumpade kandidater ger alltid EMERGENCY`() {
        val r = SplittableRandom(7)
        repeat(1_000) {
            val c = tail(text = "rad $it", ms = r.nextInt(100, 20_000), lineId = "L-$it",
                visibility = Visibility.entries[r.nextInt(3)], approval = Approval.entries[r.nextInt(4)],
                kind = TailKind.entries[r.nextInt(3)], rikard = r.nextBoolean(), validUntil = now + r.nextInt(-5_000, 20_000))
            assertEquals(SuppressReason.EMERGENCY, reason(gate.decide(input(emergency = true, speed = r.nextDouble(0.0, 120.0)), c)))
        }
    }

    @Test fun `SG-08 avvikelse ger REROUTING`() =
        assertEquals(SuppressReason.REROUTING, reason(gate.decide(input(rerouting = true), tail())))

    @Test fun `SG-09 efter första nya beskedet tillåts svans igen`() =
        assertTrue(gate.decide(input(rerouting = false, reroutes = listOf(now - 10_000), toInstruction = 60_000), tail()) is Present)

    @Test fun `SG-10 förlorad position under aktiv rutt stoppar`() {
        assertEquals(SuppressReason.POSITION_LOST, reason(gate.decide(input(quality = PositionQuality.LOST, shownSince = now - 10_000), tail())))
        assertEquals(Interrupt(SuppressReason.POSITION_LOST), gate.checkOngoing(input(quality = PositionQuality.LOST), tail()))
    }

    @Test fun `SG-11 osäkerhet visad i 3 s tillåter Rikard-svans`() {
        assertTrue(gate.decide(input(quality = PositionQuality.DEGRADED, shownSince = now - 3_000), tail(rikard = true)) is Present)
        assertEquals(SuppressReason.UNCERTAINTY_NOT_SHOWN, reason(gate.decide(input(quality = PositionQuality.DEGRADED, shownSince = now - 1_000), tail(rikard = true))))
        assertEquals(SuppressReason.UNCERTAINTY_NOT_SHOWN, reason(gate.decide(input(quality = PositionQuality.DEGRADED, shownSince = null), tail())))
    }

    @Test fun `SG-12 batteri 12 procent ger PRIORITY_INFO`() {
        assertEquals(GateState.PRIORITY_INFO, gate.state(input(battery = 12)))
        assertEquals(SuppressReason.PRIORITY_INFO, reason(gate.decide(input(battery = 12), tail())))
    }

    @Test fun `SG-13 förlorad konvojkontakt ger PRIORITY_INFO`() {
        assertEquals(GateState.PRIORITY_INFO, gate.state(input(contactLost = true)))
        assertEquals(SuppressReason.PRIORITY_INFO, reason(gate.decide(input(contactLost = true), tail())))
    }

    @Test fun `SG-14 i 70 km per h stoppas en svans på 3 s`() {
        assertEquals(SuppressReason.STRESS_TOO_LONG, reason(gate.decide(input(speed = 70.0), tail(ms = 3_000))))
        assertTrue(gate.decide(input(speed = 70.0), tail(ms = 1_400)) is Present)
        assertEquals(SuppressReason.STRESS_TOO_LONG, reason(gate.decide(input(reroutes = listOf(now - 30_000, now - 90_000)), tail(ms = 3_000))))
    }

    @Test fun `SG-15 glitch i 40 km per h stoppas`() =
        assertEquals(SuppressReason.GLITCH_SPEED, reason(gate.decide(input(speed = 40.0), tail(kind = TailKind.ANIMATION, ms = 500))))

    // SG-16 och SG-17 är ändrade genom ägarbeslutet 2026-11-20 kl. 23.30 ("I helvete allt ska höras
    // lägg av med detta trams"): privata rader spelas upp på alla ljudutgångar och i konvoj.
    @Test fun `SG-16 privat rad med aktiv konvoj spelas upp`() =
        assertTrue(gate.decide(input(convoy = true), tail(visibility = Visibility.PRIVATE)) is Present)

    @Test fun `SG-17 privat rad spelas upp på alla ljudutgångar`() {
        AudioOutput.entries.forEach {
            assertTrue("$it", gate.decide(input(audio = it), tail(visibility = Visibility.PRIVATE)) is Present)
        }
    }

    @Test fun `ägarbeslutet tar inte bort säkerhetsreglerna för privata rader`() {
        val p = tail(visibility = Visibility.PRIVATE)
        assertEquals(SuppressReason.EMERGENCY, reason(gate.decide(input(audio = AudioOutput.CAR_SPEAKER, emergency = true), p)))
        assertEquals(SuppressReason.REROUTING, reason(gate.decide(input(audio = AudioOutput.CAR_SPEAKER, rerouting = true), p)))
        assertEquals(SuppressReason.MANEUVER, reason(gate.decide(input(audio = AudioOutput.CAR_SPEAKER, toManeuver = 5.0), p)))
        assertEquals(SuppressReason.NOT_APPROVED, reason(gate.decide(input(audio = AudioOutput.CAR_SPEAKER), tail(visibility = Visibility.PRIVATE, approval = Approval.PENDING))))
    }

    @Test fun `SG-18 samma rad som för fem svansar sedan stoppas`() =
        assertEquals(SuppressReason.REPEAT, reason(gate.decide(input(recent = listOf("C-001", "a", "b", "c", "d")), tail())))

    @Test fun `SG-19 utgången svans stoppas`() {
        assertEquals(SuppressReason.EXPIRED, reason(gate.decide(input(), tail(validUntil = now - 1))))
        // En svans som redan spelas klipps inte för att livslängden går ut (etapp 4, avvikelse E4-D5);
        // säkerhetsavbrotten gäller fortfarande för den.
        assertEquals(PresentationDecision.Continue, gate.checkOngoing(input(), tail(validUntil = now - 1)))
        assertEquals(Interrupt(SuppressReason.EMERGENCY), gate.checkOngoing(input(emergency = true), tail(validUntil = now - 1)))
    }

    @Test fun `SG-20 svans från V-012 med PENDING stoppas`() =
        assertEquals(SuppressReason.NOT_APPROVED, reason(gate.decide(input(), tail(lineId = "V-012", approval = Approval.PENDING))))

    @Test fun `R-16 ingen svans de första 60 s efter nödläge`() {
        assertEquals(SuppressReason.AFTER_EMERGENCY, reason(gate.decide(input(clearedAt = now - 59_000), tail())))
        assertTrue(gate.decide(input(clearedAt = now - cfg.afterEmergencyQuietMillis), tail()) is Present)
    }

    @Test fun `SG-21 samma indata 10000 gånger ger samma beslut`() {
        val inputs = listOf(input(), input(speed = 70.0), input(quality = PositionQuality.DEGRADED, shownSince = now - 3_000), input(battery = 3))
        inputs.forEach { i ->
            val first = gate.decide(i, tail())
            repeat(10_000) { assertEquals(first, gate.decide(i, tail())) }
        }
    }

    @Test fun `SG-22 aldrig Present i EMERGENCY, REROUTING eller MANEUVER_WINDOW`() {
        val r = SplittableRandom(2026)
        var checked = 0
        repeat(50_000) {
            val i = input(
                mode = TravelMode.entries[r.nextInt(TravelMode.entries.size)], speed = r.nextDouble(0.0, 130.0),
                toManeuver = if (r.nextBoolean()) r.nextDouble(0.0, 120.0) else null,
                toInstruction = if (r.nextBoolean()) r.nextLong(0, 120_000) else null, speaking = r.nextInt(5) == 0,
                rerouting = r.nextInt(4) == 0, quality = PositionQuality.entries[r.nextInt(3)],
                shownSince = if (r.nextBoolean()) now - r.nextLong(0, 10_000) else null, emergency = r.nextInt(6) == 0,
                battery = r.nextInt(0, 101), convoy = r.nextBoolean(), contactLost = r.nextInt(8) == 0,
                audio = AudioOutput.entries[r.nextInt(5)], activeRoute = r.nextBoolean(),
            )
            val c = tail(ms = r.nextInt(200, 8_000), visibility = Visibility.entries[r.nextInt(3)],
                approval = Approval.entries[r.nextInt(4)], kind = TailKind.entries[r.nextInt(3)], rikard = r.nextBoolean(),
                validUntil = now + r.nextLong(-2_000, 15_000), text = if (r.nextBoolean()) "Höger är ett tillstånd." else "Lugnt.")
            val s = gate.state(i)
            val d = gate.decide(i, c)
            if (s in setOf(GateState.EMERGENCY, GateState.REROUTING, GateState.MANEUVER_WINDOW)) {
                checked++
                assertTrue("Present i $s", d !is Present)
                assertTrue("Ongoing fortsätter i $s", gate.checkOngoing(i, c) is Interrupt)
            }
            if (d is Present) {
                assertEquals(Approval.APPROVED, c.approval)
                i.navigation.millisToNextSpokenInstruction?.let { assertTrue(c.estimatedSpeechMillis + cfg.noTimeMarginMillis < it) }
            }
        }
        println("SG-22: 50000 slumpade lägen, $checked i EMERGENCY/REROUTING/MANEUVER_WINDOW, 0 Present där")
    }

    @Test fun `kandidatvärdena är märkta som ofrysta`() =
        assertEquals("KANDIDAT_OFRYST", SafetyGateConfig.STATUS)
}
