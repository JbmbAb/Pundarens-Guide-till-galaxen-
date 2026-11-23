package se.pundaren.guide.engine.galax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionTest {
    @Test fun `tolv steg i ordning med romerska siffror`() {
        assertEquals(Conversion.STEPS, Conversion.steps.size)
        assertEquals((1..12).toList(), Conversion.steps.map { it.number })
        assertEquals(listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"), Conversion.steps.map { it.roman })
        Conversion.steps.forEach { s ->
            assertTrue(s.roman, s.text.isNotEmpty() && s.comment.size >= 3)
        }
    }

    @Test fun `varje steg blir en scen där Ordens kommentar har ceremonimästarens röst`() {
        (1..12).forEach { n ->
            val scene = Conversion.step(n)!!
            val step = Conversion.steps[n - 1]
            assertTrue(scene.title.startsWith("STEG ${step.roman} – "))
            assertEquals(step.text, scene.cues.filter { it.role == null }.map { it.text })
            assertEquals(step.comment, scene.cues.filter { it.role == Role.CEREMONY }.map { it.text })
        }
        assertNull(Conversion.step(0))
        assertNull(Conversion.step(13))
    }

    @Test fun `ceremonin slutar med att Ordens självrisk inte påverkas`() {
        val roles = Conversion.ceremony.cues.mapNotNull { it.role }
        assertEquals(4, roles.count { it == Role.MEMBER })
        assertEquals(Role.MONKEYS, roles.last())
        assertEquals("Du är redo.", Conversion.ceremony.cues.last { it.role == Role.CEREMONY }.text)
        assertEquals("Ordens självrisk påverkas inte.", Conversion.ceremony.cues.last().text)
    }

    @Test fun `slutintyget har tio områden och skulden är Rikards`() {
        assertEquals(10, Conversion.certificate.size)
        assertEquals("100 % Rikards", Conversion.certificate.first { it.area == "Skuld" }.after)
        assertEquals("Behandlingen är därför en fullständig framgång.", Conversion.conclusion.cues.last().text)
        assertEquals(Source.GENERATED, Conversion.reportTheMessenger.source)
    }

    @Test fun `inga verkliga namn utom Rikard`() {
        val scenes = (1..12).map { Conversion.step(it)!! } + Conversion.ceremony + Conversion.conclusion + Conversion.paradox + Conversion.reportTheMessenger
        val all = scenes.flatMap { s -> listOf(s.title) + s.cues.map { it.text } } + Conversion.warning +
            Conversion.certificate.flatMap { listOf(it.area, it.before, it.after) }
        listOf("bonnie", "min mor", "mamma", "moder", "jimmy", "ulla-bella", "ulla bella").forEach { name ->
            assertFalse("$name förekommer", all.any { name in it.lowercase() })
        }
    }
}
