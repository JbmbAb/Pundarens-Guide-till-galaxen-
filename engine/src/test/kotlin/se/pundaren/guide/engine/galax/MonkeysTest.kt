package se.pundaren.guide.engine.galax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonkeysTest {
    private fun released() = Monkeys.release().state

    @Test fun `dubbeltryck släpper lös första flocken med startvärdena`() {
        val scene = Monkeys.release()
        assertEquals(1, scene.state.flocks)
        assertEquals(16, scene.state.crumbsPerMonkey)
        assertEquals(1, scene.state.workDemand)
        assertEquals(23, scene.state.emotionalCostPercent)
        assertEquals(15, scene.state.solvedPercent)
        // Först ceremonimästaren, sedan panikministern, till sist den hjälpsamma handläggaren.
        assertEquals(listOf(Role.CEREMONY, Role.PANIC, Role.HELPFUL), scene.cues.mapNotNull { it.role })
        assertEquals("RELEASE THE MONKEYS!", scene.cues.first { it.role == Role.PANIC }.text)
    }

    @Test fun `andra dubbeltrycket ger två flockar och noll besvarade frågor`() {
        val scene = Monkeys.release(released())
        assertEquals(2, scene.state.flocks)
        assertTrue(scene.cues.any { "Två flockar. Noll besvarade frågor." in it.text })
    }

    @Test fun `fler flockar tar aldrig slut och nollställer inte spelet`() {
        var state = Monkeys.halve(released())!!.state
        val titles = (1..8).map { Monkeys.release(state).also { s -> state = s.state }.title }
        assertEquals(9, state.flocks)
        assertEquals(1, state.cuts)
        assertTrue(titles.drop(1).toSet().size == 3)
    }

    @Test fun `fyra effektiviseringar tar smulorna till noll och rösterna i rätt ordning`() {
        var state = released()
        val speakers = mutableListOf<Role>()
        val crumbs = mutableListOf<Int>()
        repeat(4) {
            val scene = Monkeys.halve(state)!!
            state = scene.state
            speakers += scene.cues.first().role!!
            crumbs += state.crumbsPerMonkey
        }
        assertEquals(listOf(Role.HELPFUL, Role.CEREMONY, Role.PANIC, Role.HELPFUL), speakers)
        assertEquals(listOf(8, 4, 2, 0), crumbs)
        // Slutrapporten säger att arbetsinsatsen har ökat med 800 procent.
        assertEquals(9, state.workDemand)
        assertFalse(Monkeys.canHalve(state))
        assertNull(Monkeys.halve(state))
    }

    @Test fun `varje suboptimering löser mindre och kostar mer medan resultatet rapporteras bättre`() {
        var state = released()
        repeat(Monkeys.MAX_WORSE) {
            val next = Monkeys.suboptimize(state)!!.state
            assertTrue(next.solvedPercent < state.solvedPercent)
            assertTrue(next.emotionalCostPercent > state.emotionalCostPercent)
            assertTrue(next.confirmersNeeded > state.confirmersNeeded)
            assertTrue(next.blamePercent > state.blamePercent)
            assertTrue(next.reportedResultPercent > state.reportedResultPercent)
            state = next
        }
        assertEquals(0, state.solvedPercent)
        assertEquals(100, state.reportedResultPercent)
        assertNull(Monkeys.suboptimize(state))
    }

    @Test fun `slutrapporten och Rikard finns först vid fullständig kollaps`() {
        var state = released()
        repeat(4) { state = Monkeys.halve(state)!!.state }
        assertFalse(state.collapsed)
        assertNull(Monkeys.finalReport(state))
        assertNull(Monkeys.blameRikard(state))
        repeat(3) { state = Monkeys.suboptimize(state)!!.state }
        val last = Monkeys.suboptimize(state)!!
        assertTrue(last.state.collapsed)
        assertTrue(last.cues.any { it.text == "DEN ULTIMATA PARADOXEN" })
        assertNull("Rikard får vänta på rapporten", Monkeys.blameRikard(last.state))

        val report = Monkeys.finalReport(last.state)!!
        assertTrue(report.cues.last().text == "Resultatet är därmed enhälligt.")
        val blamed = Monkeys.blameRikard(report.state)
        assertNotNull(blamed)
        assertEquals("Äntligen en lösning som alla kan enas om.", blamed!!.cues.first().text)
        assertNull(Monkeys.blameRikard(blamed.state))
        assertNull(Monkeys.halve(blamed.state))
    }

    @Test fun `ingenting går att effektivisera innan aporna är släppta`() {
        assertNull(Monkeys.halve(MonkeyState()))
        assertNull(Monkeys.suboptimize(MonkeyState()))
    }

    @Test fun `inga andra verkliga namn än Rikard i apornas repliker`() {
        var state = MonkeyState()
        val all = mutableListOf<String>()
        fun take(scene: MonkeyScene?) { scene?.let { state = it.state; all += it.title; all += it.cues.map { c -> c.text } } }
        repeat(5) { take(Monkeys.release(state)) }
        repeat(4) { take(Monkeys.halve(state)) }
        repeat(4) { take(Monkeys.suboptimize(state)) }
        take(Monkeys.finalReport(state)); take(Monkeys.blameRikard(state))
        listOf("bonnie", "min mor", "mamma", "jimmy", "ulla").forEach { name ->
            assertFalse("$name förekommer", all.any { name in it.lowercase() })
        }
    }
}
