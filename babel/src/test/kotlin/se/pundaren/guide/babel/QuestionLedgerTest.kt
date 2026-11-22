package se.pundaren.guide.babel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Frågebokföringen (etapp 7, G8). Samtalen här är påhittade för testet (A-19); inga verkliga
 * meddelanden finns i repot.
 */
class QuestionLedgerTest {
    private val talk = """
        Alva: När går bussen till Mora i morgon?
        Bo: Du frågar alltid om bussen.
        Alva: Jag behöver veta tiden. När går bussen till Mora?
        Bo: Har du sett min cykelnyckel?

        Alva: Nej. Den ligger kanske i hallen.
        Bo: Bussen går 07.40 från torget.
        den stannar vid stationen också
    """.trimIndent()

    @Test fun `texten delas i repliker med radnummer och talare`() {
        val lines = QuestionLedger.parse(talk)
        assertEquals(listOf(1, 2, 3, 4, 6, 7, 8), lines.map { it.number }) // rad 5 är tom
        assertEquals(listOf("Alva", "Bo", "Alva", "Bo", "Alva", "Bo", "Bo"), lines.map { it.speaker })
        assertEquals("den stannar vid stationen också", lines.last().text) // fortsättningsrad hör till talaren före
        // Klockslag, hakparenteser och rader utan talare.
        val mixed = QuestionLedger.parse("[2026-11-20 12:03] Cia: Kommer du?\n12:04 Dan: Klockan är 10:30 nu\nutan talare")
        assertEquals(listOf("Cia", "Dan", "Dan"), mixed.map { it.speaker })
        assertEquals("Klockan är 10:30 nu", mixed[1].text)
        assertEquals(QuestionLedger.UNKNOWN_SPEAKER, QuestionLedger.parse("bara en rad").single().speaker)
    }

    @Test fun `varje fråga bokförs med rad, nästa replik och gemensamma ord`() {
        val ledger = QuestionLedger.analyze(talk)
        assertEquals(listOf(1, 3, 4), ledger.questions.map { it.line })
        val first = ledger.questions[0]
        assertEquals("Alva", first.speaker); assertEquals("När går bussen till Mora i morgon?", first.question)
        assertEquals(2, first.nextReplyLine); assertEquals("Bo", first.nextReplySpeaker)
        assertEquals(listOf("bussen"), first.sharedWords)
        assertFalse(first.nextReplyHasQuestion)
        assertEquals("frågan ställdes igen på rad 3", listOf(3), first.askedAgainAt)
        assertEquals(QuestionLedger.Answer.NOT_ASSESSED, first.answer)

        val second = ledger.questions[1]
        assertEquals(4, second.nextReplyLine)
        assertTrue("nästa replik är själv en fråga", second.nextReplyHasQuestion)
        assertEquals(emptyList<String>(), second.sharedWords)
        assertEquals(emptyList<Int>(), second.askedAgainAt)

        val third = ledger.questions[2] // samma regler för Bo
        assertEquals("Bo", third.speaker); assertEquals(6, third.nextReplyLine); assertEquals("Alva", third.nextReplySpeaker)
    }

    @Test fun `en fråga utan senare replik från någon annan bokförs så`() {
        val ledger = QuestionLedger.analyze("Eva: Kommer du i kväll?\nEva: Hallå?")
        assertEquals(2, ledger.questions.size)
        ledger.questions.forEach { assertNull(it.nextReplyLine); assertNull(it.nextReplySpeaker) }
    }

    @Test fun `repliker utan gemensamma ord med de tre föregående pekas ut med radnummer`() {
        val ledger = QuestionLedger.analyze(talk)
        // Rad 4 (cykelnyckel) delar inga innehållsord med rad 1–3. Rad 7 (bussen, torget) delar inga med rad 3–6? Jo: bussen står på rad 3.
        assertTrue(4 in ledger.wordBreaks.map { it.line })
        assertFalse(7 in ledger.wordBreaks.map { it.line })
        ledger.wordBreaks.forEach { assertEquals(3, it.window) }
    }

    @Test fun `samma regler gäller båda parter`() {
        val tallies = QuestionLedger.analyze(talk).tallies.associateBy { it.speaker }
        assertEquals(QuestionLedger.SpeakerTally("Alva", lines = 3, questions = 2, questionsMetByQuestion = 1, questionsAskedAgain = 1), tallies.getValue("Alva"))
        assertEquals(QuestionLedger.SpeakerTally("Bo", lines = 4, questions = 1, questionsMetByQuestion = 0, questionsAskedAgain = 0), tallies.getValue("Bo"))
        // Byter talarna plats byter också siffrorna plats: bokföringen bryr sig inte om vem som är vem.
        val swapped = talk.replace("Alva:", "X:").replace("Bo:", "Alva:").replace("X:", "Bo:")
        val t2 = QuestionLedger.analyze(swapped).tallies.associateBy { it.speaker }
        assertEquals(tallies.getValue("Alva").copy(speaker = "Bo"), t2.getValue("Bo"))
        assertEquals(tallies.getValue("Bo").copy(speaker = "Alva"), t2.getValue("Alva"))
    }

    @Test fun `utskriften pekar varje fynd på en rad och ställer inga diagnoser`() {
        val text = QuestionLedger.render(QuestionLedger.analyze(talk))
        assertTrue(text.startsWith("FRÅGEBOKFÖRING\nResultatklass: Deterministiskt konstaterat textförhållande."))
        assertTrue(text.contains(QuestionLedger.NOT_ASSESSED_NOTE))
        assertTrue(text.contains("Rad 1, Alva: «När går bussen till Mora i morgon?»"))
        assertTrue(text.contains("  Nästa replik från någon annan: rad 2, Bo."))
        assertTrue(text.contains("  Samma talare ställde en fråga med till stor del samma ord igen på rad 3."))
        // Varje frågerad börjar med ett radnummer.
        text.lines().filter { it.contains("«") }.forEach { assertTrue(it, Regex("""^Rad \d+, .+: «.+»$""").matches(it)) }
        // Bokföringen beskriver text. Den har inget ordförråd för motiv, skuld eller diagnoser.
        val forbidden = listOf("manipul", "narciss", "ljug", "skyld", "avsikt", "medvet", "undvik", "diagnos", "störning", "gaslight", "borde", "fel av")
        val own = QuestionLedger.render(QuestionLedger.analyze("A: Vad?\nB: Jaha.")).lowercase()
        forbidden.forEach { assertFalse("ordet «$it» hör inte hemma i bokföringen", it in own) }
    }

    @Test fun `tom text och text utan frågor ger en tom bokföring`() {
        assertEquals(0, QuestionLedger.analyze("").lines.size)
        val none = QuestionLedger.analyze("A: Hej.\nB: Hej hej.")
        assertEquals(0, none.questions.size)
        assertTrue(QuestionLedger.render(none).contains("Ingen mening i texten slutar med frågetecken."))
    }

    @Test fun `innehållsord är ord med minst fyra tecken som inte är funktionsord`() {
        assertEquals(setOf("bussen", "mora", "morgon"), QuestionLedger.contentWords("När går bussen till Mora i morgon?"))
        assertEquals(emptySet<String>(), QuestionLedger.contentWords("Vad är det som du har?"))
        assertEquals(listOf("Jag behöver veta tiden.".let { "När går bussen till Mora?" }), QuestionLedger.questionsIn("Jag behöver veta tiden. När går bussen till Mora?"))
    }
}
