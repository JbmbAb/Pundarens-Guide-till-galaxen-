package se.pundaren.guide.babel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.archive.core.ArchiveImporter
import se.pundaren.guide.archive.core.ArchiveSources
import se.pundaren.guide.archive.core.InMemoryArchiveStore
import java.io.File

class SatireModeTest {
    private val archive = InMemoryArchiveStore.of(ArchiveImporter.import(ArchiveSources.fromRepo(File(System.getProperty("guide.repoRoot")))))
    private val satire = SatireMode(archive)

    @Test fun `bara BS-01 till BS-10 används`() {
        assertEquals((1..10).map { "BS-%02d".format(it) }, satire.rules.map { it.id })
    }

    @Test fun `samma inmatning, session och seed ger samma svar`() {
        val a = satire.reply("Varför kom du inte?", SatireMode.Session(), 42)
        repeat(1_000) { assertEquals(a, satire.reply("Varför kom du inte?", SatireMode.Session(), 42)) }
    }

    @Test fun `svaret är märkt som satir och innehåller ingen ny text utöver regel och inmatning`() {
        val inputs = listOf("Du lovade att komma.", "Det var inte så jag menade.", "Vem tog sista kakan?", "Var är kartan?")
        var s = SatireMode.Session()
        inputs.forEachIndexed { i, t ->
            val r = satire.reply(t, s, 7)
            s = r.session
            assertEquals("Satir", r.label)
            assertTrue(r.ruleId in satire.rules.map { it.id })
            assertEquals(archive.get(r.ruleId)!!.text, r.ruleFunction)
            r.exampleLineId?.let { id -> assertEquals(archive.get(id)!!.text, r.exampleText) }
            assertTrue(r.render().startsWith("Satir · "))
        }
    }

    @Test fun `BS-08 återvänder till samtalets början efter flera varv`() {
        var s = SatireMode.Session()
        val turns = listOf("Första.", "Andra.", "Tredje.", "Fjärde.")
        var last: SatireMode.SatireReply? = null
        turns.forEach { last = satire.reply(it, s, 1); s = last!!.session }
        assertEquals("BS-08", last!!.ruleId)
        assertEquals("Första.", last!!.appliedTo)
    }

    @Test fun `Rikard-utdrag och väntande förslag används aldrig som exempel`() {
        (0 until 500).forEach { i ->
            val r = satire.reply("fråga $i", SatireMode.Session(), i.toLong())
            assertFalse(r.exampleLineId?.startsWith("R-") == true)
            assertFalse(r.ruleId in setOf("BS-11", "BS-12", "BS-13", "BS-14", "BS-15", "BS-16"))
        }
    }
}
