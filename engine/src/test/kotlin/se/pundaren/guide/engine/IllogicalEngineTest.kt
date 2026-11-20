package se.pundaren.guide.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.OriginClass
import se.pundaren.guide.archive.core.InMemoryArchiveStore
import se.pundaren.guide.engine.api.EngineContext
import se.pundaren.guide.engine.api.EngineMemory
import se.pundaren.guide.engine.api.PresentedTail
import se.pundaren.guide.truth.api.TravelMode

class IllogicalEngineTest {
    private val archive = Fixtures.realArchive
    private val engine = DefaultIllogicalEngine(LineBank.from(archive))

    @Test fun `samma händelse, minne och seed ger samma utdata`() {
        val ev = Fixtures.routeStarted("EV-1")
        val ctx = EngineContext(seed = 42, travelMode = TravelMode.PASSENGER)
        val first = engine.onEvent(ev, ctx)
        repeat(1_000) { assertEquals(first, engine.onEvent(ev, ctx)) }
    }

    @Test fun `inga svansar vid avvikelse, nödläge, batteri eller konvojhändelser`() {
        val ctx = EngineContext(1, TravelMode.PASSENGER)
        assertTrue(engine.onEvent(Fixtures.offRoute("EV-2", 10), ctx).isEmpty())
        assertTrue(engine.onEvent(Fixtures.emergency("EV-3", 20), ctx).isEmpty())
        Fixtures.simulate(500, 7).filter { !DefaultIllogicalEngine.mayProposeTail(it) }.forEach {
            assertTrue(engine.onEvent(it, ctx).isEmpty())
        }
    }

    @Test fun `varje svans uppfyller svarskontraktet i 05`() {
        var memory = EngineMemory()
        Fixtures.simulate(300, 11).forEach { ev ->
            engine.onEvent(ev, EngineContext(99, TravelMode.PASSENGER, memory)).forEach { c ->
                assertEquals(emptyList<String>(), TailContract.violations(c, ev, archive, TravelMode.PASSENGER, memory.presented))
                memory = memory.withPresented(PresentedTail(c.id, c.lineId, ev.monotonicMillis))
            }
        }
    }

    @Test fun `ingen rad upprepas inom tio presenterade repliker`() {
        var memory = EngineMemory()
        val lineIds = mutableListOf<String>()
        Fixtures.simulate(400, 3).forEach { ev ->
            engine.onEvent(ev, EngineContext(5, TravelMode.PASSENGER, memory)).forEach { c ->
                assertFalse("${c.lineId} upprepas", c.lineId in lineIds.takeLast(10))
                lineIds += c.lineId
                memory = memory.withPresented(PresentedTail(c.id, c.lineId, ev.monotonicMillis))
            }
        }
        assertTrue(lineIds.size > 50)
    }

    @Test fun `riktningsord väljs bort i fordonsläge men inte till fots`() {
        val store = InMemoryArchiveStore.of(listOf(
            Fixtures.line("TEST-1", "Universum svänger åt vänster om sig självt."),
        ))
        val e = DefaultIllogicalEngine(LineBank.from(store))
        val ev = Fixtures.routeStarted("EV-9")
        assertTrue(e.onEvent(ev, EngineContext(1, TravelMode.PASSENGER)).isEmpty())
        assertEquals(1, e.onEvent(ev, EngineContext(1, TravelMode.FOOT)).size)
    }

    @Test fun `motsägelseminnet förnekar bara presenterade yttranden`() {
        val store = InMemoryArchiveStore.of(listOf(
            Fixtures.line("TEST-A", "Påstående A."),
            Fixtures.line("TEST-DENY", "Förnekelse.", tags = setOf(LineBank.DENIAL_TAG)),
        ))
        val e = DefaultIllogicalEngine(LineBank.from(store), EngineConfig(denialPerMille = 1000))
        // Inget presenterat: ingen förnekelse.
        val c0 = e.onEvent(Fixtures.routeStarted("EV-1"), EngineContext(1, TravelMode.FOOT)).single()
        assertNull(c0.contradicts)
        // Efter en presenterad svans pekar förnekelsen på just den.
        val mem = EngineMemory(listOf(PresentedTail(c0.id, c0.lineId, 0)))
        val c1 = e.onEvent(Fixtures.maneuverCompleted("EV-2", 1_000), EngineContext(1, TravelMode.FOOT, mem)).single()
        assertEquals(c0.id, c1.contradicts)
        assertTrue(c1.mechanisms.any { it.name == "M2" })
        assertEquals(emptyList<String>(), TailContract.violations(c1, Fixtures.maneuverCompleted("EV-2", 1_000), store, TravelMode.FOOT, mem.presented))
    }

    @Test fun `ej godkända rader och regler når aldrig motorn`() {
        val store = InMemoryArchiveStore.of(listOf(
            Fixtures.line("TEST-P", "Väntar.", approval = Approval.PENDING, origin = OriginClass.SYNTHETIC_SATIRE),
            Fixtures.line("TEST-R", "En regel.", origin = OriginClass.DERIVED_RULE),
        ))
        val e = DefaultIllogicalEngine(LineBank.from(store))
        assertTrue(e.onEvent(Fixtures.routeStarted("EV-1"), EngineContext(1, TravelMode.FOOT)).isEmpty())
    }

    @Test fun `godkänd syntetisk satir når motorn`() {
        val store = InMemoryArchiveStore.of(listOf(Fixtures.line("TEST-S", "Godkänd satir.", origin = OriginClass.SYNTHETIC_SATIRE)))
        assertEquals("TEST-S", DefaultIllogicalEngine(LineBank.from(store)).onEvent(Fixtures.routeStarted("EV-1"), EngineContext(1, TravelMode.PASSENGER)).single().lineId)
    }

    @Test fun `rader bara till fots föreslås aldrig i fordonsläge men till fots`() {
        val foot = mutableSetOf<String>()
        var memory = EngineMemory()
        Fixtures.simulate(600, 17).forEach { ev ->
            engine.onEvent(ev, EngineContext(3, TravelMode.PASSENGER, memory)).forEach { c ->
                assertFalse("${c.lineId} i fordonsläge", c.footOnly)
                memory = memory.withPresented(PresentedTail(c.id, c.lineId, ev.monotonicMillis))
            }
        }
        memory = EngineMemory()
        Fixtures.simulate(600, 17).forEach { ev ->
            engine.onEvent(ev, EngineContext(3, TravelMode.FOOT, memory)).forEach { c ->
                if (c.footOnly) foot += c.lineId
                memory = memory.withPresented(PresentedTail(c.id, c.lineId, ev.monotonicMillis))
            }
        }
        assertTrue("inga fotrader föreslogs till fots", foot.isNotEmpty())
    }

    @Test fun `navigationskommentarer följer sin situation`() {
        val ankomst = Fixtures.realArchive.all().filter { "situation:arrival" in it.tags }.map { it.id }.toSet()
        assertTrue(ankomst.isNotEmpty())
        var memory = EngineMemory()
        Fixtures.simulate(800, 23).forEach { ev ->
            engine.onEvent(ev, EngineContext(9, TravelMode.FOOT, memory)).forEach { c ->
                if (c.lineId in ankomst) assertTrue(ev is se.pundaren.guide.truth.api.Arrived)
                memory = memory.withPresented(PresentedTail(c.id, c.lineId, ev.monotonicMillis))
            }
        }
    }

    @Test fun `G1-kriteriet med hundra simulerade händelser`() {
        var memory = EngineMemory()
        val spoken = mutableListOf<String>()
        val events = Fixtures.simulate(100, 2026)
        assertEquals(100, events.size)
        events.forEach { ev ->
            engine.onEvent(ev, EngineContext(2026, TravelMode.PASSENGER, memory)).forEach { c ->
                assertFalse("motsäger besked: ${c.text}", VoiceGuards.hasDirectionWord(c.text) || VoiceGuards.soundsLikeInstruction(c.text))
                assertFalse("skyller på användaren: ${c.text}", VoiceGuards.blamesUser(c.text))
                assertFalse("upprepning: ${c.lineId}", c.lineId in spoken.takeLast(10))
                spoken += c.lineId
                memory = memory.withPresented(PresentedTail(c.id, c.lineId, ev.monotonicMillis))
            }
        }
        println("G1-SIMULERING: 100 händelser, ${spoken.size} svansar föreslagna, 0 brott")
    }
}
