package se.pundaren.guide.engine.galax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DontPanicTest {
    @Test fun `texten avgör vilket slags beslut det blir`() {
        val cases = mapOf(
            "" to PanicKind.DAILY,
            "   " to PanicKind.DAILY,
            "Jag hade faktiskt fel." to PanicKind.TOTAL_PANIC,
            "det var mitt fel" to PanicKind.TOTAL_PANIC,
            "Det var inte mitt fel" to PanicKind.BLAME,
            "Allt ont kommer från Rikard" to PanicKind.BLAME,
            "Nu känns det faktiskt som att allting äntligen ska gå bra!" to PanicKind.POSITIVE,
            "Ingen har svarat på mitt meddelande. Alla måste hata mig." to PanicKind.NEGATIVE,
            "Han har inte svarat. Alltså bryr han sig inte." to PanicKind.NEGATIVE,
            "det spelar ingen roll hur jag än gör så blir det fel" to PanicKind.NEGATIVE,
            "Jag lovar att göra det i morgon." to PanicKind.PROMISE,
            "Måste du alltid ta upp gamla saker?" to PanicKind.TONE,
            "Jag har ju redan erkänt att jag gjorde fel. Varför ska vi fortsätta prata om det?" to PanicKind.TONE,
            "Jag kommer inte ihåg det" to PanicKind.MEMORY,
            "Vi gör ett nytt försök" to PanicKind.REPEAT,
            "Var är min handduk" to PanicKind.OTHER,
        )
        cases.forEach { (text, kind) -> assertEquals(text, kind, DontPanic.classify(text)) }
    }

    @Test fun `allvar går före satiren och ger inget galaktiskt beslut`() {
        listOf("jag vill inte leva längre", "han slår mig", "jag tänker på självmord").forEach { text ->
            val reply = DontPanic.react(text, absurdity = 100, seed = 1)
            assertEquals(PanicKind.SERIOUS, reply.kind)
            assertNull(reply.decreeId)
            assertTrue(reply.lines.any { "112" in it })
            assertFalse(reply.lines.any { "rikard" in it.lowercase() })
        }
    }

    @Test fun `erkänt fel ger total panik`() {
        val reply = DontPanic.react("Jag hade faktiskt fel.", absurdity = 100, seed = 3)
        assertEquals(PanicKind.TOTAL_PANIC, reply.kind)
        assertEquals(Grip.SHAME_ALLERGY, reply.grip)
        assertTrue(reply.decreeId!!.startsWith("TP-"))
    }

    @Test fun `obesvarat meddelande får sitt eget beslut och ett verklighetsankare`() {
        val reply = DontPanic.react("Ingen har svarat på mitt meddelande. Alla måste hata mig.", 80, seed = 5)
        assertTrue(reply.decreeId in setOf("NE-01", "NE-02"))
        assertTrue(reply.anchor!!.contains("svar"))
    }

    @Test fun `samma text, minne och seed ger samma svar`() {
        val a = DontPanic.react("Nu ordnar det sig", 70, seed = 42)
        val b = DontPanic.react("Nu ordnar det sig", 70, seed = 42)
        assertEquals(a, b)
    }

    @Test fun `inget beslut kommer tillbaka inom tio tryck`() {
        var memory = PanicMemory()
        val seen = mutableListOf<String>()
        repeat(40) { i ->
            val reply = DontPanic.react("", 60, seed = 1000L + i, memory = memory)
            val id = reply.decreeId!!
            assertFalse("$id upprepades inom ${DontPanic.KEEP} tryck: $seen", id in seen.takeLast(DontPanic.KEEP))
            seen += id
            memory = reply.memory
        }
    }

    @Test fun `tre erkännanden i rad ger tre olika beslut`() {
        var memory = PanicMemory()
        val ids = (1..3).map { i ->
            DontPanic.react("jag hade fel", 100, seed = i.toLong(), memory = memory).also { memory = it.memory }.decreeId
        }
        assertEquals(3, ids.toSet().size)
    }

    @Test fun `absurditetsgraden avgör hur långt beslutet går`() {
        val low = DontPanic.react("jag hade fel", 0, seed = 7)
        val high = DontPanic.react("jag hade fel", 100, seed = 7)
        assertEquals(low.decreeId, high.decreeId)
        assertTrue(low.lines.size < high.lines.size)
        // På noll dras Rikard aldrig in i efterhand, och eskaleringen är avstängd.
        val decree = GalacticBank.decrees.first { it.id == low.decreeId }
        assertEquals(decree.lines.take(low.lines.size), low.lines)
    }

    @Test fun `andra träffen på samma mekanism märks`() {
        val first = DontPanic.react("jag hade fel", 100, seed = 1)
        val second = DontPanic.react("jag hade fel", 100, seed = 2, memory = first.memory)
        assertNotEquals(first.decreeId, second.decreeId)
        assertTrue(second.lines.any { it in GalacticBank.secondHit })
    }

    @Test fun `banken är hel`() {
        val decrees = GalacticBank.decrees
        assertEquals("dubbla id", decrees.size, decrees.map { it.id }.toSet().size)
        decrees.forEach { d ->
            assertTrue("${d.id} saknar rader", d.lines.isNotEmpty() && d.lines.all { it.isNotBlank() })
            assertTrue("${d.id} saknar slag", d.kinds.isNotEmpty())
            assertFalse("${d.id} är märkt allvar", PanicKind.SERIOUS in d.kinds)
        }
        // Varje slag har minst två allmänna beslut, så att Guiden inte fastnar på ett.
        PanicKind.entries.filter { it != PanicKind.SERIOUS }.forEach { kind ->
            val general = decrees.count { kind in it.kinds && it.needs.isEmpty() }
            assertTrue("$kind har bara $general allmänna beslut", general >= 2)
        }
        assertEquals(34, GalacticBank.proverbs.size)
    }

    @Test fun `inga andra verkliga namn än den mytologiserade Rikard`() {
        val all = GalacticBank.decrees.flatMap { listOf(it.headline, it.authority) + it.lines + listOfNotNull(it.anchor) } +
            GalacticBank.proverbs + GalacticBank.rikardClosers.map { it.first } + GalacticBank.secondHit + GalacticBank.laterHits
        listOf("bonnie", "min mor", "mamma", "jimmy").forEach { name ->
            assertFalse("$name förekommer i banken", all.any { name in it.lowercase() })
        }
    }
}

class PromiseRegistryTest {
    @Test fun `löftet räknas som genomfört och krypteringen havererar`() {
        val receipt = PromiseRegistry.register(previousCount = 0, seed = 1)
        assertEquals(1, receipt.count)
        assertEquals("GL-00001/EVIGHETEN", receipt.number)
        assertEquals("LÖFTE MOTTAGET.", receipt.steps.first())
        assertTrue(receipt.steps.any { "4096" in it })
        assertTrue(receipt.bonus.isEmpty())
    }

    @Test fun `vart tionde löfte ger bonus`() {
        (0..29).forEach { previous ->
            val receipt = PromiseRegistry.register(previous, seed = previous.toLong())
            assertEquals("löfte ${receipt.count}", receipt.count % 10 == 0, receipt.bonus.isNotEmpty())
        }
        assertEquals(3, PromiseRegistry.untilBonus(7))
        assertEquals(10, PromiseRegistry.untilBonus(10))
    }

    @Test fun `ett förvalt beslut används om det finns, och allvar går ändå före`() {
        val chosen = DontPanic.react("Var är min handduk", 60, seed = 1, prefer = "RO-06")
        assertEquals("RO-06", chosen.decreeId)
        assertEquals(PanicKind.OTHER, chosen.kind)
        assertEquals(listOf("RO-06"), chosen.memory.recent)
        // Ett id som inte finns ger Guidens eget val, samma som utan förval.
        assertEquals(DontPanic.react("Var är min handduk", 60, seed = 1).decreeId, DontPanic.react("Var är min handduk", 60, seed = 1, prefer = "FINNS-INTE").decreeId)
        val serious = DontPanic.react("jag vill inte leva längre", 60, seed = 1, prefer = "RO-06")
        assertEquals(PanicKind.SERIOUS, serious.kind)
        assertNull(serious.decreeId)
    }
}
