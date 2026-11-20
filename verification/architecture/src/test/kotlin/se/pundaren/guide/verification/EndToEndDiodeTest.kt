package se.pundaren.guide.verification

import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.archive.core.ArchiveImporter
import se.pundaren.guide.archive.core.ArchiveSources
import se.pundaren.guide.archive.core.InMemoryArchiveStore
import se.pundaren.guide.engine.DefaultIllogicalEngine
import se.pundaren.guide.engine.LineBank
import se.pundaren.guide.engine.api.EngineContext
import se.pundaren.guide.engine.api.EngineMemory
import se.pundaren.guide.engine.api.PresentedTail
import se.pundaren.guide.safetygate.GateInput
import se.pundaren.guide.safetygate.GateState
import se.pundaren.guide.safetygate.PresentationDecision
import se.pundaren.guide.safetygate.SafetyGate
import se.pundaren.guide.truth.api.*
import java.io.File
import java.time.Instant

/**
 * Händelser -> motor -> SafetyGate med en enkel tillståndsmodell av sanningen.
 * Visar dynamiskt att sanningstillståndet är oförändrat oavsett vad motorn föreslår,
 * och att inget släpps fram i EMERGENCY, REROUTING eller MANEUVER_WINDOW.
 */
class EndToEndDiodeTest {
    private val archive = InMemoryArchiveStore.of(ArchiveImporter.import(ArchiveSources.fromRepo(File(System.getProperty("guide.repoRoot")))))

    private data class Truth(val nav: NavigationSnapshot, val pos: PositionSnapshot, val safety: SafetySnapshot)

    private fun step(t: Truth, e: TruthEvent): Truth = when (e) {
        is ManeuverUpcoming -> t.copy(nav = t.nav.copy(secondsToNextManeuver = e.secondsToManeuver, millisToNextSpokenInstruction = (e.secondsToManeuver * 1000).toLong()))
        is ManeuverCompleted -> t.copy(nav = t.nav.copy(secondsToNextManeuver = 120.0, millisToNextSpokenInstruction = 90_000))
        is OffRoute -> t.copy(nav = t.nav.copy(rerouting = true))
        is Rerouted -> t.copy(nav = t.nav.copy(rerouting = false, recentRerouteMonotonicMillis = t.nav.recentRerouteMonotonicMillis + e.monotonicMillis))
        is PositionQualityChanged -> t.copy(pos = PositionSnapshot(e.quality, e.accuracyMeters, e.fixAgeSeconds, if (e.quality == PositionQuality.GOOD) null else e.monotonicMillis))
        is BatteryLow -> t.copy(safety = t.safety.copy(batteryPercent = e.percent))
        is ConvoyContactLost -> t.copy(safety = t.safety.copy(convoyContactLost = true))
        is EmergencyActivated -> t.copy(safety = t.safety.copy(emergencyActive = true))
        is EmergencyCleared -> t.copy(safety = t.safety.copy(emergencyActive = false, emergencyClearedAtMonotonicMillis = e.monotonicMillis))
        else -> t
    }

    @Test fun `tvåhundra simulerade händelser genom motor och SafetyGate`() {
        val engine = DefaultIllogicalEngine(LineBank.from(archive))
        val gate = SafetyGate()
        var truth = Truth(
            NavigationSnapshot(true, TravelMode.PASSENGER, 30.0, 120.0, 90_000, false, false),
            PositionSnapshot(PositionQuality.GOOD, 5.0, 1.0, null),
            SafetySnapshot(false, null, 80, false, false, AudioOutput.CAR_SPEAKER), // bilhögtalare: allt ska höras (ägarbeslut 23.30)
        )
        var memory = EngineMemory()
        var presented = 0
        var suppressed = 0
        val reasons = sortedMapOf<String, Int>()
        val t0 = Instant.parse("2026-11-20T21:00:00Z")
        val rnd = java.util.SplittableRandom(20261120)
        var ms = 0L
        repeat(200) { i ->
            ms += 1_000 + rnd.nextInt(5_000)
            val id = "E2E-%03d".format(i)
            val e: TruthEvent = when (rnd.nextInt(10)) {
                0 -> ManeuverUpcoming(id, ms, t0.plusMillis(ms), "X", "M$i", "turn", "right", 200, rnd.nextDouble(2.0, 60.0), null)
                1 -> ManeuverCompleted(id, ms, t0.plusMillis(ms), "X", "M$i")
                2 -> OffRoute(id, ms, t0.plusMillis(ms), "X", 50, 4)
                3 -> Rerouted(id, ms, t0.plusMillis(ms), "X", 3_000, 400, "avvikelse")
                4 -> PositionQualityChanged(id, ms, t0.plusMillis(ms), "X", 30.0, 3.0, PositionQuality.entries[rnd.nextInt(3)])
                5 -> if (rnd.nextInt(4) == 0) EmergencyActivated(id, ms, t0.plusMillis(ms), "X", EmergencySource.USER) else EmergencyCleared(id, ms, t0.plusMillis(ms), "X")
                6 -> Stationary(id, ms, t0.plusMillis(ms), "X", 30)
                7 -> TimeOfDayChanged(id, ms, t0.plusMillis(ms), "X", DayPhase.DUSK)
                8 -> RouteStarted(id, ms, t0.plusMillis(ms), "X", "Orsa", 9_000, 700, TravelMode.PASSENGER)
                else -> Arrived(id, ms, t0.plusMillis(ms), "X", "Orsa", 700, 9_000)
            }
            truth = step(truth, e)
            val before = truth
            val tails = engine.onEvent(e, EngineContext(7, TravelMode.PASSENGER, memory))
            val input = GateInput(ms, truth.safety, truth.nav, truth.pos, memory.presented.map { it.lineId })
            tails.forEach { c ->
                when (gate.decide(input, c)) {
                    is PresentationDecision.Present -> {
                        assertTrue(gate.state(input) !in setOf(GateState.EMERGENCY, GateState.REROUTING, GateState.MANEUVER_WINDOW))
                        presented++
                        memory = memory.withPresented(PresentedTail(c.id, c.lineId, ms))
                    }
                    is PresentationDecision.Suppress -> { suppressed++; val r = (gate.decide(input, c) as PresentationDecision.Suppress).reason.name; reasons[r] = (reasons[r] ?: 0) + 1 }
                    else -> suppressed++
                }
            }
            assertTrue("sanningen ändrades av tramslagret", before == truth)
        }
        println("E2E: 200 händelser, $presented svansar presenterade, $suppressed stoppade, skäl: $reasons")
    }
}
