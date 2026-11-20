package se.pundaren.guide.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.OriginClass

class LineBankTest {
    private val bank = LineBank.from(Fixtures.realArchive)

    @Test fun `radbanken innehåller bara godkända original och godkända bearbetningar`() {
        assertTrue(bank.lines.isNotEmpty())
        assertTrue(bank.lines.all { it.approval == Approval.APPROVED })
        assertTrue(bank.lines.all { it.originClass in setOf(OriginClass.ORIGINAL_VERBATIM, OriginClass.APPROVED_EDIT) })
        println("RADBANK (${bank.lines.size}): " + bank.lines.joinToString { it.id })
        println("UTESLUTNA (${bank.excluded.size}): " + bank.excluded.entries.groupBy({ it.value }, { it.key }).entries.joinToString(" | ") { "${it.key}=${it.value.size}" })
    }

    @Test fun `inga Grok Bot-förslag kommer in i radbanken`() {
        val grokIds = Fixtures.realArchive.all().filter { it.author.contains("Grok Bot") }.map { it.id }.toSet()
        assertEquals(65, grokIds.size)
        assertTrue(bank.lines.none { it.id in grokIds })
        assertTrue(bank.denialLines.none { it.id in grokIds })
        assertTrue(grokIds.all { bank.excluded[it] == Exclusion.NOT_APPROVED })
    }

    @Test fun `Rikard-utdrag, regler, lexikon och arkivposter hålls utanför`() {
        listOf("R-001", "R-002", "R-003").forEach { assertEquals(Exclusion.REAL_PERSON_EXCERPT, bank.excluded[it]) }
        (1..10).forEach { assertTrue(bank.excluded["BS-%02d".format(it)] in setOf(Exclusion.ORIGIN_NOT_ALLOWED, Exclusion.KIND_NOT_TAIL)) }
        assertEquals(Exclusion.KIND_NOT_TAIL, bank.excluded["garderobsknullis"])
        assertEquals(Exclusion.NOT_APPROVED, bank.excluded["EX-01-FORMULERING"])
    }

    @Test fun `det finns inga förnekelserader i dagens data`() {
        assertTrue(bank.denialLines.isEmpty())
    }
}
