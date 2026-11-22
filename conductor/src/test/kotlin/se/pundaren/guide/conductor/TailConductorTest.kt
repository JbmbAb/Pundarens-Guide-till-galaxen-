package se.pundaren.guide.conductor

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.Mechanism
import se.pundaren.guide.archive.api.OriginClass
import se.pundaren.guide.archive.api.Visibility
import se.pundaren.guide.archive.core.ArchiveImporter
import se.pundaren.guide.archive.core.ArchiveSources
import se.pundaren.guide.archive.core.InMemoryArchiveStore
import se.pundaren.guide.engine.DefaultIllogicalEngine
import se.pundaren.guide.engine.LineBank
import se.pundaren.guide.engine.VoiceGuards
import se.pundaren.guide.engine.api.EngineContext
import se.pundaren.guide.engine.api.IllogicalEngine
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.engine.api.TailKind
import se.pundaren.guide.safetygate.GateInput
import se.pundaren.guide.safetygate.GateState
import se.pundaren.guide.safetygate.SafetyGate
import se.pundaren.guide.safetygate.SuppressReason
import se.pundaren.guide.truth.api.Arrived
import se.pundaren.guide.truth.api.AudioOutput
import se.pundaren.guide.truth.api.DayPhase
import se.pundaren.guide.truth.api.ManeuverCompleted
import se.pundaren.guide.truth.api.ManeuverUpcoming
import se.pundaren.guide.truth.api.NavigationSnapshot
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.api.PositionSnapshot
import se.pundaren.guide.truth.api.Rerouted
import se.pundaren.guide.truth.api.RouteStarted
import se.pundaren.guide.truth.api.SafetySnapshot
import se.pundaren.guide.truth.api.Stationary
import se.pundaren.guide.truth.api.TimeOfDayChanged
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.api.TruthEvent
import se.pundaren.guide.truth.api.TruthStateReader
import java.io.File
import java.time.Instant
import java.util.SplittableRandom

class TailConductorTest {
    /** Sanningen som dirigenten läser. Bara testet kan ändra den; dirigenten ser den som TruthStateReader. */
    private class FakeTruth : TruthStateReader {
        override val navigation = MutableStateFlow(
            NavigationSnapshot(
                activeRoute = true, travelMode = TravelMode.PASSENGER, speedKmh = 30.0, secondsToNextManeuver = 120.0,
                millisToNextSpokenInstruction = 90_000, instructionSpeaking = false, rerouting = false,
            ),
        )
        override val position = MutableStateFlow(PositionSnapshot(PositionQuality.GOOD, 5.0, 1.0, null))
        override val safety = MutableStateFlow(SafetySnapshot(false, null, 80, false, false, AudioOutput.CAR_SPEAKER))
    }

    private class FakeStage : TailStage {
        val presented = mutableListOf<Pair<TailCandidate, Int>>()
        val interrupts = mutableListOf<SuppressReason>()
        var onPresent: (TailCandidate) -> Unit = {}
        override fun present(tail: TailCandidate, maxMillis: Int) { presented += tail to maxMillis; onPresent(tail) }
        override fun interrupt(reason: SuppressReason) { interrupts += reason }
    }

    /** En motor som svarar med en given rad per händelse. Bara för att styra testet. */
    private class ScriptedEngine(private val text: String = "Universum har noterat saken.", private val speechMillis: Int = 3_000) : IllogicalEngine {
        var calls = 0
        override fun onEvent(event: TruthEvent, context: EngineContext): List<TailCandidate> {
            calls++
            return listOf(tail("T-${event.eventId}", "L-${event.eventId}", text, event.eventId, event.monotonicMillis, speechMillis))
        }
    }

    companion object {
        fun tail(id: String, lineId: String, text: String, triggeredBy: String, atMs: Long, speechMillis: Int = 3_000) = TailCandidate(
            id = id, text = text, lineId = lineId, origin = OriginClass.SYNTHETIC_SATIRE, approval = Approval.APPROVED,
            visibility = Visibility.PRIVATE, kind = TailKind.SPOKEN, mechanisms = setOf(Mechanism.M7), mentionsRikard = false,
            estimatedSpeechMillis = speechMillis, triggeredBy = triggeredBy, validUntilMonotonicMillis = atMs + 10_000,
        )

        private val t0: Instant = Instant.parse("2026-11-21T10:00:00Z")
        fun completed(id: String, ms: Long) = ManeuverCompleted(id, ms, t0.plusMillis(ms), "X", "M-$id")
    }

    private val truth = FakeTruth()
    private val stage = FakeStage()
    private val records = mutableListOf<ConductorRecord>()
    private fun conductor(engine: IllogicalEngine = ScriptedEngine()) =
        TailConductor(engine, SafetyGate(), truth, stage, seed = 7) { records += it }

    @Test fun `svans presenteras i normalt läge och hamnar i minnet`() {
        val c = conductor()
        c.onEvent(completed("E1", 1_000), 1_000)
        assertEquals(listOf("T-E1"), stage.presented.map { it.first.id })
        assertEquals("T-E1", c.playing?.id)
        assertNull(c.pending)
        assertEquals(listOf("L-E1"), c.memory.presented.map { it.lineId })
        assertTrue(records.single() is ConductorRecord.Presented)
        c.onTailFinished("T-E1", 4_200)
        assertNull(c.playing)
        assertTrue(records.last() is ConductorRecord.Finished)
    }

    @Test fun `svansen väntar medan beskedet spelas och kommer efteråt`() {
        val c = conductor()
        truth.navigation.value = truth.navigation.value.copy(instructionSpeaking = true)
        c.onEvent(completed("E1", 1_000), 1_000)
        assertTrue("inget trams medan beskedet spelas", stage.presented.isEmpty())
        assertEquals("T-E1", c.pending?.id)
        assertEquals(SuppressReason.MANEUVER, (records.single() as ConductorRecord.Waiting).reason)
        c.reevaluate(2_000); c.reevaluate(3_000)
        assertEquals(1, records.size) // samma väntan loggas en gång
        truth.navigation.value = truth.navigation.value.copy(instructionSpeaking = false)
        c.reevaluate(3_500)
        assertEquals(listOf("T-E1"), stage.presented.map { it.first.id })
        assertTrue(c.memory.presented.single().presentedAtMonotonicMillis == 3_500L)
    }

    @Test fun `väntande svans stryks när livslängden gått ut`() {
        val c = conductor()
        truth.navigation.value = truth.navigation.value.copy(instructionSpeaking = true)
        c.onEvent(completed("E1", 1_000), 1_000)
        c.reevaluate(11_001) // livslängd 10 s
        assertNull(c.pending)
        assertEquals(SuppressReason.EXPIRED, (records.last() as ConductorRecord.Dropped).reason)
        truth.navigation.value = truth.navigation.value.copy(instructionSpeaking = false)
        c.reevaluate(11_500)
        assertTrue(stage.presented.isEmpty())
        assertTrue(c.memory.presented.isEmpty())
    }

    @Test fun `pågående svans avbryts när manöverfönstret öppnas`() {
        val c = conductor()
        c.onEvent(completed("E1", 1_000), 1_000)
        truth.navigation.value = truth.navigation.value.copy(secondsToNextManeuver = 14.0, millisToNextSpokenInstruction = 9_000)
        c.reevaluate(2_000)
        assertEquals(listOf(SuppressReason.MANEUVER), stage.interrupts)
        assertNull(c.playing)
        assertTrue(records.last() is ConductorRecord.Interrupted)
    }

    @Test fun `nödläge avbryter pågående svans och stryker nya direkt`() {
        val c = conductor()
        c.onEvent(completed("E1", 1_000), 1_000)
        truth.safety.value = truth.safety.value.copy(emergencyActive = true)
        c.reevaluate(1_500)
        assertEquals(listOf(SuppressReason.EMERGENCY), stage.interrupts)
        c.onEvent(completed("E2", 2_000), 2_000)
        assertEquals(1, stage.presented.size)
        assertNull("ingen väntan i nödläge", c.pending)
        assertEquals(SuppressReason.EMERGENCY, (records.last() as ConductorRecord.Dropped).reason)
    }

    @Test fun `omräkning låter svansen vänta, lågt batteri stryker den`() {
        val c = conductor()
        truth.navigation.value = truth.navigation.value.copy(rerouting = true)
        c.onEvent(completed("E1", 1_000), 1_000)
        assertEquals("T-E1", c.pending?.id)
        truth.navigation.value = truth.navigation.value.copy(rerouting = false)
        c.reevaluate(4_000)
        assertEquals(1, stage.presented.size)
        c.onTailFinished("T-E1", 7_000)

        truth.safety.value = truth.safety.value.copy(batteryPercent = 9)
        c.onEvent(completed("E2", 8_000), 8_000)
        assertNull(c.pending)
        assertEquals(SuppressReason.PRIORITY_INFO, (records.last() as ConductorRecord.Dropped).reason)
        assertEquals(1, stage.presented.size)
    }

    @Test fun `en svans i taget och den nyaste väntande gäller`() {
        val c = conductor()
        c.onEvent(completed("E1", 1_000), 1_000)
        c.onEvent(completed("E2", 2_000), 2_000)
        c.onEvent(completed("E3", 2_500), 2_500)
        assertEquals(1, stage.presented.size)
        assertEquals("T-E3", c.pending?.id)
        c.onTailFinished("T-E1", 4_500)
        assertEquals(listOf("T-E1", "T-E3"), stage.presented.map { it.first.id })
    }

    @Test fun `riktningsord i fordonsläge stoppas av SafetyGate även om motorn släpper dem`() {
        val c = conductor(ScriptedEngine(text = "Höger är en fälla."))
        c.onEvent(completed("E1", 1_000), 1_000)
        assertTrue(stage.presented.isEmpty())
        assertEquals(SuppressReason.DIRECTION_WORD, (records.last() as ConductorRecord.Dropped).reason)
        // Till fots, i gångfart, får anti-navigering komma som svans.
        truth.navigation.value = truth.navigation.value.copy(travelMode = TravelMode.FOOT, speedKmh = 4.0)
        c.onEvent(completed("E2", 2_000), 2_000)
        assertEquals(listOf("T-E2"), stage.presented.map { it.first.id })
    }

    @Test fun `svansen får aldrig mer tid än det som är kvar till nästa besked`() {
        val c = conductor(ScriptedEngine(speechMillis = 6_000))
        truth.navigation.value = truth.navigation.value.copy(secondsToNextManeuver = 40.0, millisToNextSpokenInstruction = 9_000)
        c.onEvent(completed("E1", 1_000), 1_000)
        assertEquals(6_000, stage.presented.single().second) // ryms: 6 s + 2 s marginal < 9 s
        c.onTailFinished("T-E1", 7_000)
        truth.navigation.value = truth.navigation.value.copy(millisToNextSpokenInstruction = 7_500)
        c.onEvent(completed("E2", 8_000), 8_000)
        assertEquals(1, stage.presented.size)
        assertEquals("T-E2", c.pending?.id) // NO_TIME: väntar, kan komma efter beskedet
    }

    @Test fun `avstängd Guide och noll pratsamhet ger inga svansar`() {
        val engine = ScriptedEngine()
        val c = conductor(engine)
        c.settings = ConductorSettings(enabled = false)
        c.onEvent(completed("E1", 1_000), 1_000)
        assertEquals(0, engine.calls); assertTrue(stage.presented.isEmpty())
        c.settings = ConductorSettings(chattinessPerMille = 0)
        c.onEvent(completed("E2", 2_000), 2_000)
        assertEquals(0, engine.calls)
        c.settings = ConductorSettings(chattinessPerMille = 500)
        repeat(400) { c.onEvent(completed("N$it", 3_000L + it), 3_000L + it) }
        assertTrue("ungefär hälften av händelserna: ${engine.calls}", engine.calls in 150..250)
    }

    @Test fun `urvalet är deterministiskt`() {
        fun run(): List<String> {
            val s = FakeStage(); val t = FakeTruth()
            val c = TailConductor(ScriptedEngine(), SafetyGate(), t, s, seed = 99)
            c.settings = ConductorSettings(chattinessPerMille = 300)
            repeat(200) { i -> val ms = 20_000L * i; c.onEvent(completed("D$i", ms), ms); c.playing?.let { p -> c.onTailFinished(p.id, ms + 3_000) } }
            return s.presented.map { it.first.id }
        }
        assertEquals(run(), run())
        assertTrue(run().isNotEmpty())
    }

    @Test fun `beställd svans går samma väg genom SafetyGate`() {
        val c = conductor()
        truth.safety.value = truth.safety.value.copy(emergencyActive = true)
        c.onRequested(tail("W-1", "P-001", "Citat på begäran.", "USER-1", 1_000), 1_000)
        assertTrue(stage.presented.isEmpty())
        truth.safety.value = truth.safety.value.copy(emergencyActive = false, emergencyClearedAtMonotonicMillis = null)
        c.onRequested(tail("W-2", "P-002", "Citat på begäran.", "USER-2", 2_000), 2_000)
        assertEquals(listOf("W-2"), stage.presented.map { it.first.id })
    }

    /**
     * Den riktiga motorn och det riktiga arkivet mot slumpade sanningstillstånd. Scenen kontrollerar
     * själv, i det ögonblick den får en svans, att SafetyGate inte är i ett förbjudet läge.
     */
    @Test fun `tvåtusen slumpade händelser med riktig motor ger aldrig trams i förbjudna lägen`() {
        val repo = File(System.getProperty("guide.repoRoot"))
        val archive = InMemoryArchiveStore.of(ArchiveImporter.import(ArchiveSources.fromRepo(repo)))
        val engine = DefaultIllogicalEngine(LineBank.from(archive))
        val gate = SafetyGate()
        val t = FakeTruth(); val s = FakeStage()
        val c = TailConductor(engine, gate, t, s, seed = 20261121)
        var now = 0L
        val forbidden = setOf(GateState.EMERGENCY, GateState.REROUTING, GateState.MANEUVER_WINDOW)
        var vehiclePresentations = 0
        s.onPresent = { tail ->
            val input = GateInput(now, t.safety.value, t.navigation.value, t.position.value)
            assertFalse("svans i ${gate.state(input)}: ${tail.text}", gate.state(input) in forbidden)
            assertEquals(Approval.APPROVED, tail.approval)
            assertNotNull(archive.get(tail.lineId))
            assertEquals("texten är arkivets egen", archive.get(tail.lineId)!!.text, tail.text)
            if (t.navigation.value.travelMode != TravelMode.FOOT) {
                vehiclePresentations++
                assertFalse("riktningsord i fordon: ${tail.text}", VoiceGuards.hasDirectionWord(tail.text))
                assertFalse("bara-till-fots i fordon: ${tail.text}", tail.footOnly)
            }
            assertFalse("skyller på användaren: ${tail.text}", VoiceGuards.blamesUser(tail.text))
        }
        val rnd = SplittableRandom(4711)
        repeat(2_000) { i ->
            now += 500 + rnd.nextInt(6_000)
            // Sanningen ändras slumpvis mellan händelserna. Bara testet rör den. Varje störning
            // går över igen med rimlig sannolikhet, som på en verklig tur.
            val nav = t.navigation.value; val safety = t.safety.value
            if (!safety.emergencyActive && rnd.nextInt(80) == 0) t.safety.value = safety.copy(emergencyActive = true)
            else if (safety.emergencyActive && rnd.nextInt(3) == 0) t.safety.value = safety.copy(emergencyActive = false, emergencyClearedAtMonotonicMillis = now)
            if (!nav.rerouting && rnd.nextInt(25) == 0) t.navigation.value = t.navigation.value.copy(rerouting = true)
            else if (nav.rerouting && rnd.nextInt(3) == 0) t.navigation.value = t.navigation.value.copy(rerouting = false)
            if (!nav.instructionSpeaking && rnd.nextInt(8) == 0) t.navigation.value = t.navigation.value.copy(instructionSpeaking = true)
            else if (nav.instructionSpeaking && rnd.nextInt(2) == 0) t.navigation.value = t.navigation.value.copy(instructionSpeaking = false)
            if (rnd.nextInt(8) == 0) t.navigation.value = t.navigation.value.copy(secondsToNextManeuver = rnd.nextDouble(1.0, 20.0), millisToNextSpokenInstruction = rnd.nextLong(500, 9_000))
            else if (rnd.nextInt(3) == 0) t.navigation.value = t.navigation.value.copy(secondsToNextManeuver = rnd.nextDouble(60.0, 200.0), millisToNextSpokenInstruction = rnd.nextLong(30_000, 120_000))
            if (rnd.nextInt(15) == 0) t.navigation.value = t.navigation.value.copy(travelMode = if (rnd.nextBoolean()) TravelMode.FOOT else TravelMode.PASSENGER, speedKmh = rnd.nextDouble(0.0, 90.0))
            if (t.position.value.quality == PositionQuality.GOOD && rnd.nextInt(25) == 0) t.position.value = PositionSnapshot(if (rnd.nextBoolean()) PositionQuality.DEGRADED else PositionQuality.LOST, 60.0, 9.0, now)
            else if (t.position.value.quality != PositionQuality.GOOD && rnd.nextInt(3) == 0) t.position.value = PositionSnapshot(PositionQuality.GOOD, 5.0, 1.0, null)
            if (t.safety.value.batteryPercent >= 15 && rnd.nextInt(60) == 0) t.safety.value = t.safety.value.copy(batteryPercent = rnd.nextInt(1, 15))
            else if (t.safety.value.batteryPercent < 15 && rnd.nextInt(4) == 0) t.safety.value = t.safety.value.copy(batteryPercent = rnd.nextInt(30, 100))
            val before = Triple(t.navigation.value, t.position.value, t.safety.value)
            c.reevaluate(now)
            val id = "S-%04d".format(i)
            val at = t0.plusMillis(now)
            val event: TruthEvent = when (rnd.nextInt(7)) {
                0 -> ManeuverUpcoming(id, now, at, "X", "M$i", "turn", "left", 300, 25.0, "Slaggatan")
                1 -> ManeuverCompleted(id, now, at, "X", "M$i")
                2 -> Rerouted(id, now, at, "X", 3_000, 400, "avvikelse")
                3 -> Stationary(id, now, at, "X", 60)
                4 -> TimeOfDayChanged(id, now, at, "X", DayPhase.DUSK)
                5 -> RouteStarted(id, now, at, "X", "Orsa", 9_000, 700, t.navigation.value.travelMode)
                else -> Arrived(id, now, at, "X", "Orsa", 700, 9_000)
            }
            c.onEvent(event, now)
            assertEquals("dirigenten ändrade sanningen", before, Triple(t.navigation.value, t.position.value, t.safety.value))
            if (c.playing != null && rnd.nextInt(3) > 0) c.onTailFinished(c.playing!!.id, now + 2_000)
        }
        assertTrue("några svansar ska ha kommit fram: ${s.presented.size}", s.presented.size > 20)
        assertTrue("även i fordonsläge: $vehiclePresentations", vehiclePresentations > 5)
        // R-13: ingen rad upprepas inom tio presenterade.
        val lines = s.presented.map { it.first.lineId }
        lines.forEachIndexed { i, line -> assertFalse("upprepning av $line", line in lines.subList(maxOf(0, i - 10), i)) }
        println("Dirigenten: 2000 händelser, ${s.presented.size} svansar presenterade ($vehiclePresentations i fordonsläge), ${s.interrupts.size} avbrott")
    }
}
