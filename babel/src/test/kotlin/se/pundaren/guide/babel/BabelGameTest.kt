package se.pundaren.guide.babel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.ArchiveEntry
import se.pundaren.guide.archive.api.EntryKind
import se.pundaren.guide.archive.api.OriginClass
import se.pundaren.guide.archive.core.ArchiveImporter
import se.pundaren.guide.archive.core.ArchiveSources
import se.pundaren.guide.archive.core.InMemoryArchiveStore
import java.io.File

/** Babel-Stoner som spel (etapp 7, G8): bara godkänt material, deterministiskt, och greppen stannar i spelet. */
class BabelGameTest {
    private val archive = InMemoryArchiveStore.of(ArchiveImporter.import(ArchiveSources.fromRepo(File(System.getProperty("guide.repoRoot")))))
    private val game = BabelGame(archive)
    private val question = "Vem tog sista kakan?"

    private fun written(line: ArchiveEntry) = line.id.startsWith(WrittenGrips.ID_PREFIX)

    @Test fun `alla sexton grepp går att spela, med godkända arkivrader eller nyskrivna repliker`() {
        assertEquals((1..16).map { "BS-%02d".format(it) }, game.grips.keys.sorted())
        assertTrue(game.unplayable.isEmpty())
        game.grips.values.forEach { grip ->
            assertEquals(EntryKind.RULE, grip.rule.kind); assertEquals(Approval.APPROVED, grip.rule.approval)
            assertTrue(grip.lines.isNotEmpty())
            // Ett grepp spelas antingen med arkivets rader eller med de nyskrivna, aldrig med en blandning.
            assertTrue(grip.rule.id, grip.lines.all(::written) || grip.lines.none(::written))
            grip.lines.forEach { line ->
                if (written(line)) {
                    assertEquals(line, WrittenGrips.lines.single { it.id == line.id })
                    assertNull("${line.id} får inte ligga i arkivet", archive.get(line.id))
                } else {
                    assertEquals("${line.id} är inte godkänd", Approval.APPROVED, line.approval)
                    assertFalse("${line.id} återger en verklig person", "real_person_excerpt" in line.tags)
                    assertFalse("${line.id} är bara för arkivet", "archive_only" in line.tags)
                }
            }
        }
        // Greppet om Rikard spelas med nyskrivna repliker om syndabocken, aldrig med utdrag från en verklig person.
        assertTrue(game.grips.getValue("BS-09").lines.all(::written))
        // Läget skrivs ut så att protokollet kan redovisa det.
        println("SPELBARA GREPP: " + game.grips.values.joinToString { "${it.rule.id} (${it.lines.joinToString("+") { l -> l.id }})" })
        println("VÄNTAR PÅ GODKÄNDA RADER: " + game.unplayable.joinToString { it.id })
        BabelGame.Opponent.entries.forEach { println("MOTSTÅNDARE ${it.title}: ${game.gripsOf(it).size} spelbara grepp") }
    }

    @Test fun `de nyskrivna replikerna är märkta som genererade och utger sig inte för något annat`() {
        assertEquals(30, WrittenGrips.lines.size)
        assertEquals(WrittenGrips.lines.size, WrittenGrips.lines.map { it.id }.toSet().size)
        assertEquals(WrittenGrips.lines.size, WrittenGrips.lines.map { it.text }.toSet().size)
        WrittenGrips.lines.forEach { line ->
            assertEquals(OriginClass.SYNTHETIC_SATIRE, line.originClass)
            assertEquals("bara ägaren kan godkänna", Approval.PENDING, line.approval)
            assertEquals(WrittenGrips.AUTHOR, line.author)
            assertEquals(line.id, setOf("rule:${line.derivedFrom}"), line.tags)
            // Inga namn på verkliga personer. Rikard är mytologins syndabock och förekommer bara i sitt eget grepp.
            listOf("bonnie", "min mor", "mamma", "jimmy", "ulla").forEach { assertFalse("${line.id}: $it", it in line.text.lowercase()) }
            if (line.derivedFrom != "BS-09") assertFalse(line.id, "rikard" in line.text.lowercase())
        }
    }

    @Test fun `analysen efter ett vunnet spel räknar bara det som hände i spelet`() {
        var round = game.start(question, BabelGame.Opponent.COSMIC_CONFUSION, 7)!!
        round = game.guess(round, round.options.first { it.id != round.grip.rule.id }.id)
        val won = game.guess(round, round.grip.rule.id)
        assertEquals(2, won.used.size); assertEquals(won.grip.rule.id, won.used.last())
        val analysis = won.analysis().toMap()
        assertEquals("2", analysis["Repliker från Babel-Stoner"])
        assertEquals("0", analysis["Repliker som besvarade frågan"])
        assertEquals(won.used.joinToString(", "), analysis["Grepp i tur och ordning"])
        assertEquals("1", analysis["Felaktigt namngivna grepp"])
        assertTrue(won.analysis().last().first.endsWith("(satir)"))
    }

    @Test fun `de fem motståndarna delar upp greppen och den femte har alla`() {
        assertEquals(5, BabelGame.Opponent.entries.size)
        val four = BabelGame.Opponent.entries.filter { it.ruleIds != null }
        assertEquals("varje grepp hör till en av de fyra första", (1..16).map { "BS-%02d".format(it) }, four.flatMap { it.ruleIds!! }.sorted())
        assertEquals(game.grips.keys.sorted(), game.gripsOf(BabelGame.Opponent.COSMIC_CONFUSION).map { it.rule.id })
        four.forEach { o -> assertTrue(game.gripsOf(o).all { it.rule.id in o.ruleIds!! }) }
    }

    @Test fun `spelet är deterministiskt`() {
        val a = game.start(question, BabelGame.Opponent.COSMIC_CONFUSION, 42)!!
        repeat(200) { assertEquals(a, game.start(question, BabelGame.Opponent.COSMIC_CONFUSION, 42)) }
        val wrong = a.options.first { it.id != a.grip.rule.id }.id
        assertEquals(game.guess(a, wrong), game.guess(a, wrong))
    }

    @Test fun `varje replik är en godkänd arkivrad och alternativen är godkända regler med det rätta bland dem`() {
        for (seed in 0L until 300L) {
            var round = game.start("$question $seed", BabelGame.Opponent.COSMIC_CONFUSION, seed)!!
            repeat(6) {
                assertEquals("Satir", round.label)
                assertTrue(round.line in round.grip.lines)
                assertEquals(round.line, if (written(round.line)) WrittenGrips.lines.single { it.id == round.line.id } else archive.get(round.line.id))
                assertEquals(BabelGame.OPTIONS, round.options.size); assertEquals(round.options.size, round.options.toSet().size)
                assertTrue(round.grip.rule in round.options)
                round.options.forEach { assertEquals(EntryKind.RULE, it.kind); assertEquals(Approval.APPROVED, it.approval) }
                round = game.guess(round, round.options.first { it.id != round.grip.rule.id }.id)
            }
        }
    }

    @Test fun `rätt grepp ger tillbaka frågan och poängen är antalet repliker`() {
        var round = game.start(question, BabelGame.Opponent.COSMIC_CONFUSION, 7)!!
        assertEquals(1, round.replies); assertFalse(round.won)
        // Två felgissningar: Babel-Stoner slingrar sig vidare med ett nytt grepp varje gång.
        repeat(2) {
            val before = round.grip.rule.id
            round = game.guess(round, round.options.first { it.id != round.grip.rule.id }.id)
            if (game.gripsOf(round.opponent).size > 1) assertTrue("nytt grepp efter en felgissning", round.grip.rule.id != before)
        }
        assertEquals(3, round.replies); assertEquals(2, round.wrongGuesses); assertFalse(round.won)
        val won = game.guess(round, round.grip.rule.id)
        assertTrue(won.won)
        assertEquals("frågan är återställd ordagrant", question, won.question)
        assertEquals(3, won.replies)
        assertEquals("ett vunnet spel ändras inte", won, game.guess(won, "BS-01"))
    }

    @Test fun `en motståndare utan spelbara grepp och en tom fråga ger inget spel`() {
        assertNull(game.start("   ", BabelGame.Opponent.COSMIC_CONFUSION, 1))
        BabelGame.Opponent.entries.forEach { o ->
            val round = game.start(question, o, 1)
            if (game.gripsOf(o).isEmpty()) assertNull(round) else assertNotNull(round)
        }
    }

    @Test fun `tecken på hot eller våld bryter satiren`() {
        listOf("Han hotar mig varje dag", "hon slog mig igår", "Jag vill inte leva längre", "han sa att han ska DÖDA mig").forEach {
            assertTrue(it, SeriousnessCheck.isSerious(it))
        }
        listOf(question, "Varför kom du inte i tid?", "Var är kartan?").forEach { assertFalse(it, SeriousnessCheck.isSerious(it)) }
        assertTrue(SeriousnessCheck.MESSAGE.contains("112"))
    }
}
