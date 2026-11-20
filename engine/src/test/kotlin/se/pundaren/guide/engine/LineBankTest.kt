package se.pundaren.guide.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.OriginClass

class LineBankTest {
    private val bank = LineBank.from(Fixtures.realArchive)

    @Test fun `radbanken innehåller bara godkända poster och inga regler`() {
        assertTrue(bank.lines.isNotEmpty())
        assertTrue(bank.lines.all { it.approval == Approval.APPROVED })
        assertTrue(bank.lines.all { it.originClass in setOf(OriginClass.ORIGINAL_VERBATIM, OriginClass.APPROVED_EDIT, OriginClass.SYNTHETIC_SATIRE) })
        println("RADBANK (${bank.lines.size}): " + bank.lines.joinToString { it.id })
        println("UTESLUTNA (${bank.excluded.size}): " + bank.excluded.entries.groupBy({ it.value }, { it.key }).entries.joinToString(" | ") { "${it.key}=${it.value.size}" })
    }

    @Test fun `godkända variationer och navigationskommentarer finns i radbanken`() {
        // Ägarbeslut 2026-11-20 kl. 23.36 (F-11).
        val ids = bank.lines.map { it.id }.toSet()
        (1..20).forEach { assertTrue("V-%03d".format(it) in ids) }
        (1..20).forEach { assertTrue("NAV-%03d".format(it) in ids) }
        // Lexikon, regler och exempel är inte svansar i navigatorn.
        assertTrue(bank.lines.none { it.id.startsWith("BS-") || it.id.startsWith("EX-") || it.category == "lexikon" })
    }

    @Test fun `märkta rader är bara till fots`() {
        val foot = bank.lines.filter { LineBank.isFootOnly(it) }
        assertTrue(foot.map { it.id }.containsAll(listOf("NAV-001", "NAV-003", "NAV-007", "NAV-012", "V-010")))
        println("BARA TILL FOTS (${foot.size}): " + foot.joinToString { it.id })
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
