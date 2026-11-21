@file:OptIn(TruthInput::class)

package se.pundaren.guide.verification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.archive.core.ArchiveImporter
import se.pundaren.guide.archive.core.ArchiveSources
import se.pundaren.guide.archive.core.InMemoryArchiveStore
import se.pundaren.guide.conductor.TailConductor
import se.pundaren.guide.conductor.TailStage
import se.pundaren.guide.engine.DefaultIllogicalEngine
import se.pundaren.guide.engine.LineBank
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.safetygate.GateInput
import se.pundaren.guide.safetygate.GateState
import se.pundaren.guide.safetygate.SafetyGate
import se.pundaren.guide.safetygate.SuppressReason
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.api.TruthEvent
import se.pundaren.guide.truth.core.TruthInput
import se.pundaren.guide.truth.core.kernel.KernelConfig
import se.pundaren.guide.truth.core.kernel.NavPhase
import se.pundaren.guide.truth.core.kernel.NavTick
import se.pundaren.guide.truth.core.kernel.PositionFix
import se.pundaren.guide.truth.core.kernel.Stamp
import se.pundaren.guide.truth.core.kernel.TruthKernel
import java.io.File
import java.time.Instant
import java.util.Collections

/**
 * Dioden mot den riktiga sanningskärnan (K-10 i etapp1-protokollet var bara prövad mot en stubbe).
 *
 * Samma tur spelas två gånger genom [TruthKernel]: en gång ensam och en gång med hela tramslagret
 * inkopplat (riktig motor, riktigt arkiv, SafetyGate och dirigenten). Händelserna och varje
 * ögonblicksbild måste bli exakt likadana. Kan tramslagret på något sätt påverka sanningen, så
 * skiljer sig körningarna åt.
 */
class KernelDiodeTest {
    private val orsa = GeoPoint(61.1201175, 14.6222536)
    private val gronklitt = GeoPoint(61.2077132, 14.5377551)
    private val t0 = Instant.parse("2026-11-21T10:00:00Z")
    private fun stamp(ms: Long) = Stamp(ms, t0.plusMillis(ms))

    private fun tick(ms: Long, steps: Int, generation: Int, toManeuver: Double, deviation: Double? = null, phase: NavPhase = NavPhase.NAVIGATING) = NavTick(
        stamp = stamp(ms), phase = phase, routeGeneration = generation, stepsRemaining = steps,
        instructionText = "Sväng vänster in på Slaggatan.", maneuverType = "turn", maneuverModifier = "left", streetName = "Slaggatan",
        distanceToManeuverMeters = toManeuver, secondsToManeuverByRoute = toManeuver / 13.9, metersToNextSpokenTrigger = toManeuver * 0.6,
        distanceRemainingMeters = 15_000.0 - ms / 100.0, secondsRemaining = 900.0 - ms / 1000.0, distanceTraveledMeters = ms / 100.0,
        deviationMeters = deviation, snappedPosition = orsa,
    )

    /**
     * En tur med allt som kan hända: besked, stegbyten, avvikelse, omräkning, nödläge, lågt batteri, framkomst.
     * Farten är 40 km/h: över 50 km/h släpper SafetyGate bara fram svansar på högst 1,5 s (R-10), och så korta
     * rader finns inte i arkivet.
     */
    private fun trip(kernel: TruthKernel, afterEachStep: (nowMs: Long) -> Unit) {
        var ms = 0L
        fun step(action: () -> Unit) { action(); afterEachStep(ms) }
        step { kernel.battery(stamp(ms), 80, charging = false) }
        step { kernel.positionFix(stamp(ms), PositionFix(orsa, 5.0, speedMps = 11.0)) }
        step { kernel.routeStarted(stamp(ms), "Grönklitt", gronklitt, TravelMode.PASSENGER, 15_730.0, 960.0, listOf(orsa, gronklitt)) }
        var steps = 17
        var generation = 1
        repeat(240) { i ->
            ms += 1_000
            val toManeuver = 900.0 - (i % 60) * 15.0
            step { kernel.positionFix(stamp(ms), PositionFix(orsa, if (i in 150..156) 60.0 else 5.0, speedMps = 11.0)) }
            if (i % 60 == 0 && i > 0) steps--
            val deviation = if (i in 80..83) 249.7 else null
            if (i == 84) generation++
            step { kernel.navTick(tick(ms, if (i >= 84) steps + 3 else steps, generation, toManeuver, deviation)) }
            if (i % 20 == 5 || i == 85) {
                step { kernel.instructionSpeaking(stamp(ms), true) }
                step { kernel.instructionTriggered(stamp(ms)) }
            }
            if (i % 20 == 8 || i == 88) step { kernel.instructionSpeaking(stamp(ms), false) }
            if (i == 120) step { kernel.emergency(stamp(ms), true) }
            if (i == 128) step { kernel.emergency(stamp(ms), false) }
            if (i == 200) step { kernel.battery(stamp(ms), 12, charging = false) }
            if (i == 215) step { kernel.battery(stamp(ms), 45, charging = true) }
            step { kernel.clockTick(stamp(ms)) }
        }
        ms += 1_000
        step { kernel.navTick(tick(ms, 0, generation, 0.0, phase = NavPhase.ARRIVED)) }
        repeat(70) { ms += 1_000; step { kernel.positionFix(stamp(ms), PositionFix(gronklitt, 5.0, speedMps = 0.0)) }; step { kernel.clockTick(stamp(ms)) } }
    }

    private class Run(val events: List<String>, val snapshots: List<String>, val presented: List<TailCandidate>)

    private fun run(withTrams: Boolean): Run {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val kernel = TruthKernel(KernelConfig(fallbackPosition = orsa))
        val events = Collections.synchronizedList(mutableListOf<TruthEvent>())
        scope.launch { kernel.events.collect { events += it } }
        val snapshots = mutableListOf<String>()
        val presented = mutableListOf<TailCandidate>()

        val gate = SafetyGate()
        var now = 0L
        var conductor: TailConductor? = null
        val stage = object : TailStage {
            override fun present(tail: TailCandidate, maxMillis: Int) {
                val state = gate.state(GateInput(now, kernel.safety.value, kernel.navigation.value, kernel.position.value))
                assertFalse("svans i $state", state in setOf(GateState.EMERGENCY, GateState.REROUTING, GateState.MANEUVER_WINDOW))
                presented += tail
            }
            override fun interrupt(reason: SuppressReason) {}
        }
        if (withTrams) {
            val archive = InMemoryArchiveStore.of(ArchiveImporter.import(ArchiveSources.fromRepo(File(System.getProperty("guide.repoRoot")))))
            conductor = TailConductor(DefaultIllogicalEngine(LineBank.from(archive)), gate, kernel, stage, seed = 20261121)
        }
        var delivered = 0
        trip(kernel) { ms ->
            now = ms
            snapshots += "${kernel.navigation.value} | ${kernel.position.value} | ${kernel.safety.value} | ${kernel.guidance.value} | ${kernel.map.value}"
            conductor?.let { c ->
                while (delivered < events.size) c.onEvent(events[delivered++], ms)
                c.reevaluate(ms)
                c.playing?.let { playing -> if (ms % 3_000 == 0L) c.onTailFinished(playing.id, ms) }
            }
        }
        scope.cancel()
        return Run(events.map { it.toString() }, snapshots, presented)
    }

    @Test fun `samma tur med och utan tramslagret ger exakt samma sanning`() {
        val alone = run(withTrams = false)
        val withTrams = run(withTrams = true)
        assertTrue("turen ger händelser: ${alone.events.size}", alone.events.size >= 20)
        assertEquals("händelserna skiljer sig när tramslagret är inkopplat", alone.events, withTrams.events)
        assertEquals("ögonblicksbilderna skiljer sig när tramslagret är inkopplat", alone.snapshots, withTrams.snapshots)
        assertTrue("tramslagret var verkligen igång: ${withTrams.presented.size} svansar", withTrams.presented.size >= 3)
        assertTrue(alone.presented.isEmpty())
        println("KernelDiodeTest: ${alone.events.size} händelser, ${alone.snapshots.size} ögonblicksbilder, ${withTrams.presented.size} svansar presenterade; sanningen identisk")
    }

    @Test fun `turen innehåller alla lägen som ska tysta tramset`() {
        val run = run(withTrams = false)
        val kinds = run.events.map { it.substringBefore('(') }.toSet()
        for (expected in listOf("RouteStarted", "ManeuverUpcoming", "ManeuverCompleted", "OffRoute", "Rerouted", "EmergencyActivated", "EmergencyCleared", "BatteryLow", "PositionQualityChanged", "Arrived", "Stationary")) {
            assertTrue("$expected saknas i turen: $kinds", expected in kinds)
        }
    }
}
