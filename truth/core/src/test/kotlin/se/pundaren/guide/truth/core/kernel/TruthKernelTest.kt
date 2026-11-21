@file:OptIn(TruthInput::class)

package se.pundaren.guide.truth.core.kernel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.truth.api.Arrived
import se.pundaren.guide.truth.api.BatteryLow
import se.pundaren.guide.truth.api.DayPhase
import se.pundaren.guide.truth.api.EmergencyActivated
import se.pundaren.guide.truth.api.EmergencyCleared
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.GuidancePhase
import se.pundaren.guide.truth.api.ManeuverCompleted
import se.pundaren.guide.truth.api.ManeuverUpcoming
import se.pundaren.guide.truth.api.OffRoute
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.api.PositionQualityChanged
import se.pundaren.guide.truth.api.Rerouted
import se.pundaren.guide.truth.api.RouteStarted
import se.pundaren.guide.truth.api.Stationary
import se.pundaren.guide.truth.api.TimeOfDayChanged
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.api.TruthEvent
import se.pundaren.guide.truth.core.TruthInput
import java.time.Instant
import java.util.Collections

class TruthKernelTest {
    private val orsa = GeoPoint(61.1201175, 14.6222536)
    private val gronklitt = GeoPoint(61.2077132, 14.5377551)
    private val t0 = Instant.parse("2026-11-21T10:00:00Z")
    private fun stamp(ms: Long) = Stamp(ms, t0.plusMillis(ms))

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val kernel = TruthKernel(KernelConfig(fallbackPosition = orsa))
    private val seen: MutableList<TruthEvent> = Collections.synchronizedList(mutableListOf<TruthEvent>())

    init {
        scope.launch { kernel.events.collect { seen += it } }
    }

    @After fun close() = scope.cancel()

    private inline fun <reified T : TruthEvent> events() = seen.filterIsInstance<T>()

    private fun start(mode: TravelMode = TravelMode.PASSENGER) =
        kernel.routeStarted(stamp(0), "Grönklitt", gronklitt, mode, 15_730.0, 960.0, listOf(orsa, gronklitt))

    private fun tick(
        ms: Long, steps: Int, generation: Int = 1, toManeuver: Double = 400.0, byRoute: Double? = 40.0,
        deviation: Double? = null, recalculating: Boolean = false, phase: NavPhase = NavPhase.NAVIGATING,
        toSpoken: Double? = 250.0,
    ) = NavTick(
        stamp = stamp(ms), phase = phase, routeGeneration = generation, stepsRemaining = steps,
        instructionText = "Sväng vänster in på Slaggatan", maneuverType = "turn", maneuverModifier = "left",
        streetName = "Slaggatan", distanceToManeuverMeters = toManeuver, secondsToManeuverByRoute = byRoute,
        metersToNextSpokenTrigger = toSpoken, distanceRemainingMeters = 15_000.0, secondsRemaining = 900.0,
        distanceTraveledMeters = 700.0, deviationMeters = deviation, recalculating = recalculating, snappedPosition = orsa,
    )

    @Test fun `ruttstart ger RouteStarted och aktiv navigering`() {
        start()
        kernel.navTick(tick(100, steps = 17))
        val e = events<RouteStarted>().single()
        assertEquals("Grönklitt", e.destinationName); assertEquals(15_730, e.distanceMeters); assertEquals(960, e.etaSeconds)
        assertEquals(TravelMode.PASSENGER, e.travelMode)
        assertNotNull(e.expeditionId)
        val nav = kernel.navigation.value
        assertTrue(nav.activeRoute); assertFalse(nav.rerouting)
        assertEquals(40.0, nav.secondsToNextManeuver!!, 1e-9)
        val g = kernel.guidance.value
        assertEquals(GuidancePhase.NAVIGATING, g.phase)
        assertEquals("Sväng vänster in på Slaggatan", g.instructionText)
        assertEquals(17, g.stepsRemaining)
        assertEquals(t0.plusMillis(100).plusSeconds(900), g.etaWallClockUtc)
        assertEquals(listOf(orsa, gronklitt), kernel.map.value.routeLine)
    }

    @Test fun `stegbyte ger ManeuverCompleted för den manöver som just passerades`() {
        start()
        kernel.navTick(tick(100, steps = 17))
        kernel.navTick(tick(200, steps = 17))
        kernel.navTick(tick(300, steps = 16))
        assertEquals(listOf("R1-S17"), events<ManeuverCompleted>().map { it.maneuverId })
    }

    @Test fun `utlöst talinstruktion ger ManeuverUpcoming med sanna värden`() {
        start()
        kernel.navTick(tick(100, steps = 17, toManeuver = 300.0, byRoute = 21.6))
        kernel.instructionTriggered(stamp(120))
        val e = events<ManeuverUpcoming>().single()
        assertEquals("R1-S17", e.maneuverId); assertEquals("turn", e.type); assertEquals("left", e.modifier)
        assertEquals(300, e.distanceMeters); assertEquals(21.6, e.secondsToManeuver, 1e-9); assertEquals("Slaggatan", e.streetName)
    }

    @Test fun `restiden till manövern är den kortaste av ruttens och den i nuvarande fart`() {
        start()
        kernel.positionFix(stamp(0), PositionFix(orsa, 5.0, speedMps = 25.0))
        kernel.navTick(tick(100, steps = 17, toManeuver = 500.0, byRoute = 40.0))
        assertEquals(20.0, kernel.navigation.value.secondsToNextManeuver!!, 1e-6) // 500 m i 25 m/s
        kernel.positionFix(stamp(200), PositionFix(orsa, 5.0, speedMps = 0.0))
        kernel.positionFix(stamp(400), PositionFix(orsa, 5.0, speedMps = 0.0))
        kernel.positionFix(stamp(600), PositionFix(orsa, 5.0, speedMps = 0.0))
        kernel.navTick(tick(700, steps = 17, toManeuver = 500.0, byRoute = 40.0))
        // Stillastående före en sväng: ruttens restid gäller, inte oändligheten.
        assertTrue(kernel.navigation.value.secondsToNextManeuver!! <= 40.0)
        // Tiden till nästa talinstruktion räknas på den högsta av verklig och förväntad fart.
        assertEquals(20_000L, kernel.navigation.value.millisToNextSpokenInstruction) // 250 m / (500 m / 40 s)
    }

    @Test fun `avvikelse ger OffRoute och omräkningsläge tills första nya beskedet har spelats`() {
        start()
        kernel.navTick(tick(1_000, steps = 17))
        kernel.navTick(tick(2_000, steps = 17, deviation = 249.7))
        assertEquals(250, events<OffRoute>().single().deviationMeters)
        assertTrue(kernel.navigation.value.rerouting)
        assertEquals(GuidancePhase.REROUTING, kernel.guidance.value.phase)

        kernel.navTick(tick(2_500, steps = 17, deviation = 249.7, recalculating = true))
        assertEquals(1, events<OffRoute>().size) // samma avvikelse rapporteras en gång
        kernel.routeReplaced(stamp(3_000), listOf(orsa, gronklitt))
        kernel.navTick(tick(3_100, steps = 21, generation = 2))
        val r = events<Rerouted>().single()
        assertEquals(15_000, r.newDistanceMeters); assertEquals(900, r.newEtaSeconds)
        assertTrue("REROUTING gäller tills beskedet spelats", kernel.navigation.value.rerouting)
        assertEquals(listOf(3_100L), kernel.navigation.value.recentRerouteMonotonicMillis)
        assertEquals(GuidancePhase.NAVIGATING, kernel.guidance.value.phase) // remsan visar nästa manöver igen

        kernel.instructionSpeaking(stamp(3_150), true)
        kernel.instructionTriggered(stamp(3_160))
        kernel.clockTick(stamp(6_000))
        assertTrue("beskedet spelas fortfarande", kernel.navigation.value.rerouting)
        kernel.instructionSpeaking(stamp(6_500), false)
        assertFalse(kernel.navigation.value.rerouting)
    }

    @Test fun `utan röst släpps omräkningsläget efter en kort stund, och senast efter maxgränsen`() {
        start()
        kernel.navTick(tick(1_000, steps = 17))
        kernel.navTick(tick(2_000, steps = 17, deviation = 300.0))
        kernel.navTick(tick(3_000, steps = 20, generation = 2))
        kernel.instructionTriggered(stamp(3_050))
        kernel.clockTick(stamp(4_000))
        assertTrue(kernel.navigation.value.rerouting)
        kernel.clockTick(stamp(4_600))
        assertFalse(kernel.navigation.value.rerouting)

        // Ny avvikelse, ny rutt, men inget besked alls: läget hålls högst 30 s.
        kernel.navTick(tick(10_000, steps = 20, generation = 2, deviation = 400.0))
        kernel.navTick(tick(11_000, steps = 19, generation = 3))
        kernel.clockTick(stamp(40_000))
        assertTrue(kernel.navigation.value.rerouting)
        kernel.clockTick(stamp(41_100))
        assertFalse(kernel.navigation.value.rerouting)
    }

    @Test fun `tillbaka på rutten utan omräkning släpper läget`() {
        start()
        kernel.navTick(tick(1_000, steps = 17))
        kernel.navTick(tick(2_000, steps = 17, deviation = 60.0))
        assertTrue(kernel.navigation.value.rerouting)
        kernel.navTick(tick(3_000, steps = 17))
        assertFalse(kernel.navigation.value.rerouting)
        assertTrue(events<Rerouted>().isEmpty())
    }

    @Test fun `framkomst ger Arrived och avslutar rutten`() {
        start()
        kernel.navTick(tick(1_000, steps = 2))
        kernel.navTick(tick(962_000, steps = 0, phase = NavPhase.ARRIVED))
        val a = events<Arrived>().single()
        assertEquals("Grönklitt", a.destinationName); assertEquals(962, a.totalSeconds); assertEquals(700, a.totalMeters)
        assertFalse(kernel.navigation.value.activeRoute)
        assertEquals(GuidancePhase.ARRIVED, kernel.guidance.value.phase)
        kernel.navTick(tick(963_000, steps = 0, phase = NavPhase.ARRIVED))
        assertEquals(1, events<Arrived>().size)
    }

    @Test fun `positionskvalitet GOOD DEGRADED LOST med ärlig ålder`() {
        assertEquals(PositionQuality.LOST, kernel.position.value.quality)
        kernel.clockTick(stamp(1_000))
        assertTrue("ingen händelse innan första positionen", events<PositionQualityChanged>().isEmpty())

        kernel.positionFix(stamp(2_000), PositionFix(orsa, 4.0))
        assertEquals(PositionQuality.GOOD, kernel.position.value.quality)
        assertNull(kernel.position.value.uncertaintyShownSinceMonotonicMillis)

        kernel.positionFix(stamp(3_000), PositionFix(orsa, 45.0))
        assertEquals(PositionQuality.DEGRADED, kernel.position.value.quality)
        assertEquals(3_000L, kernel.position.value.uncertaintyShownSinceMonotonicMillis)

        kernel.positionFix(stamp(4_000), PositionFix(orsa, 5.0))
        kernel.clockTick(stamp(11_000)) // 7 s gammal
        assertEquals(PositionQuality.DEGRADED, kernel.position.value.quality)
        assertEquals(7.0, kernel.position.value.fixAgeSeconds!!, 1e-9)
        kernel.clockTick(stamp(25_000)) // 21 s gammal
        assertEquals(PositionQuality.LOST, kernel.position.value.quality)
        assertEquals(21.0, kernel.position.value.fixAgeSeconds!!, 1e-9)

        assertEquals(
            listOf(PositionQuality.GOOD, PositionQuality.DEGRADED, PositionQuality.GOOD, PositionQuality.DEGRADED, PositionQuality.LOST),
            events<PositionQualityChanged>().map { it.quality },
        )
        assertEquals(21.0, events<PositionQualityChanged>().last().fixAgeSeconds, 1e-9)
    }

    @Test fun `en gammal sparad position ser inte färsk ut`() {
        // Levereras vid 100 s men togs vid 10 s: 90 s gammal, alltså LOST från början.
        kernel.positionFix(stamp(100_000), PositionFix(orsa, 5.0), fixMonotonicMillis = 10_000)
        assertEquals(PositionQuality.LOST, kernel.position.value.quality)
        assertEquals(90.0, kernel.position.value.fixAgeSeconds!!, 1e-9)
        assertTrue(events<PositionQualityChanged>().isEmpty())
        assertEquals(orsa, kernel.map.value.position) // visas ändå på kartan, med sin ålder
        // En äldre position än den vi har kastas.
        kernel.positionFix(stamp(101_000), PositionFix(gronklitt, 5.0), fixMonotonicMillis = 5_000)
        assertEquals(orsa, kernel.map.value.position)
        // En färsk position gör läget GOOD.
        kernel.positionFix(stamp(102_000), PositionFix(orsa, 5.0))
        assertEquals(PositionQuality.GOOD, kernel.position.value.quality)
    }

    @Test fun `farten härleds ur positionerna när GNSS inte anger den`() {
        val p2 = se.pundaren.guide.truth.core.geo.Geo.destination(orsa, 0.0, 14.0)
        kernel.positionFix(stamp(0), PositionFix(orsa, 5.0))
        kernel.positionFix(stamp(1_000), PositionFix(p2, 5.0))
        assertEquals(50.4, kernel.navigation.value.speedKmh, 0.2) // 14 m/s
    }

    @Test fun `stillastående rapporteras vid 60 s, 5 min och 15 min och nollställs av rörelse`() {
        var ms = 0L
        fun stay(untilMs: Long) { while (ms < untilMs) { ms += 1_000; kernel.positionFix(stamp(ms), PositionFix(orsa, 5.0, speedMps = 0.0)); kernel.clockTick(stamp(ms)) } }
        stay(59_000); assertTrue(events<Stationary>().isEmpty())
        stay(62_000); assertEquals(1, events<Stationary>().size)
        assertTrue(events<Stationary>().first().durationSeconds in 60..62)
        stay(302_000); assertEquals(2, events<Stationary>().size)
        stay(902_000); assertEquals(3, events<Stationary>().size)
        stay(1_500_000); assertEquals(3, events<Stationary>().size)
        // Rörelse nollställer; ny stillhet börjar om från 60 s.
        repeat(5) { ms += 1_000; kernel.positionFix(stamp(ms), PositionFix(orsa, 5.0, speedMps = 5.0)); kernel.clockTick(stamp(ms)) }
        stay(ms + 100_000)
        assertEquals(4, events<Stationary>().size)
    }

    @Test fun `batteriet rapporteras när det passerar trösklarna nedåt, inte vid laddning`() {
        kernel.battery(stamp(0), 80, charging = false)
        kernel.battery(stamp(1), 21, charging = false)
        assertTrue(events<BatteryLow>().isEmpty())
        kernel.battery(stamp(2), 20, charging = false)
        kernel.battery(stamp(3), 19, charging = false)
        assertEquals(listOf(20), events<BatteryLow>().map { it.percent })
        kernel.battery(stamp(4), 12, charging = false) // passerar 15
        kernel.battery(stamp(5), 9, charging = false)  // passerar 10
        assertEquals(listOf(20, 12, 9), events<BatteryLow>().map { it.percent })
        kernel.battery(stamp(6), 30, charging = true)
        kernel.battery(stamp(7), 18, charging = false) // passerar 20 igen efter laddning
        assertEquals(4, events<BatteryLow>().size)
        assertEquals(18, kernel.safety.value.batteryPercent)
    }

    @Test fun `nödläge slås på och av och tidpunkten för avslaget sparas`() {
        kernel.emergency(stamp(1_000), true)
        kernel.emergency(stamp(1_500), true) // dubbeltryck ger ingen ny händelse
        assertTrue(kernel.safety.value.emergencyActive)
        kernel.emergency(stamp(9_000), false)
        assertFalse(kernel.safety.value.emergencyActive)
        assertEquals(9_000L, kernel.safety.value.emergencyClearedAtMonotonicMillis)
        assertEquals(1, events<EmergencyActivated>().size); assertEquals(1, events<EmergencyCleared>().size)
    }

    @Test fun `dygnsfasen ger händelse vid byte men inte vid första avläsningen`() {
        val k = TruthKernel(KernelConfig(fallbackPosition = orsa))
        val got = Collections.synchronizedList(mutableListOf<TruthEvent>())
        val job = scope.launch { k.events.collect { got += it } }
        k.clockTick(Stamp(0, Instant.parse("2026-11-21T10:30:00Z")))      // dag
        assertTrue(got.isEmpty())
        k.clockTick(Stamp(1_000, Instant.parse("2026-11-21T14:40:00Z")))  // skymning (solen ned ca 14.10 UTC)
        k.clockTick(Stamp(2_000, Instant.parse("2026-11-21T22:00:00Z")))  // natt
        assertEquals(listOf(DayPhase.DUSK, DayPhase.NIGHT), got.filterIsInstance<TimeOfDayChanged>().map { it.phase })
        job.cancel()
    }

    @Test fun `stoppad rutt nollställer vägledningen och ticks utan rutt ger inga händelser`() {
        start()
        kernel.navTick(tick(1_000, steps = 17))
        kernel.routeStopped(stamp(2_000))
        assertFalse(kernel.navigation.value.activeRoute)
        assertEquals(GuidancePhase.IDLE, kernel.guidance.value.phase)
        assertTrue(kernel.map.value.routeLine.isEmpty())
        val before = seen.size
        kernel.navTick(tick(3_000, steps = 16))
        kernel.instructionTriggered(stamp(3_100))
        assertEquals(before, seen.size)
    }

    @Test fun `händelsernas id är unika och sorterbara och kuvertet är ifyllt`() {
        start()
        kernel.navTick(tick(100, steps = 17))
        kernel.instructionTriggered(stamp(150))
        kernel.navTick(tick(200, steps = 16))
        val ids = seen.map { it.eventId }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ids.sorted(), ids)
        seen.forEach { e -> assertEquals(1, e.schemaVersion); assertEquals(t0.plusMillis(e.monotonicMillis), e.wallClockUtc) }
    }

    @Test fun `samma indata ger samma händelser`() {
        fun run(): List<String> {
            val k = TruthKernel(KernelConfig(fallbackPosition = orsa))
            val got = Collections.synchronizedList(mutableListOf<TruthEvent>())
            val job = scope.launch { k.events.collect { got += it } }
            k.routeStarted(stamp(0), "Grönklitt", gronklitt, TravelMode.FOOT, 12_420.0, 8_760.0, listOf(orsa, gronklitt))
            k.positionFix(stamp(10), PositionFix(orsa, 5.0, speedMps = 1.3))
            k.navTick(tick(100, steps = 30)); k.instructionTriggered(stamp(110)); k.navTick(tick(5_000, steps = 29))
            k.navTick(tick(6_000, steps = 29, deviation = 80.0)); k.navTick(tick(8_000, steps = 31, generation = 2))
            k.battery(stamp(9_000), 14, false); k.emergency(stamp(9_500), true)
            job.cancel()
            return got.map { it.toString() }
        }
        assertEquals(run(), run())
    }
}
