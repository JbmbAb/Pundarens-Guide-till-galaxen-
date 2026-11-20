package se.pundaren.guide.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.EntryKind
import se.pundaren.guide.archive.api.OriginClass
import se.pundaren.guide.archive.api.Visibility
import se.pundaren.guide.archive.core.ArchiveImporter
import se.pundaren.guide.archive.core.ArchiveSources
import se.pundaren.guide.archive.core.InMemoryArchiveStore
import se.pundaren.guide.babel.SatireMode
import se.pundaren.guide.engine.LineBank
import se.pundaren.guide.engine.VoiceGuards
import se.pundaren.guide.safetygate.GateDirectionCheck
import java.io.File

/** Röstreglerna i 12 som körbara tester. Regler som inte kan testas maskinellt står som NOT_VERIFIED i protokollet. */
class VoiceRulesTest {
    private val archive = InMemoryArchiveStore.of(ArchiveImporter.import(ArchiveSources.fromRepo(File(System.getProperty("guide.repoRoot")))))
    private val bank = LineBank.from(archive)

    @Test fun `RR-01 varje rad i radbanken bär minst en mekanism`() =
        assertTrue(bank.lines.all { it.mechanisms.isNotEmpty() })

    @Test fun `RR-04 inga rader i radbanken låter som besked`() =
        assertTrue(bank.lines.none { VoiceGuards.soundsLikeInstruction(it.text) })

    @Test fun `RR-05 spärrlistan omfattar böjningar men inte högre`() {
        listOf("vänster", "Höger", "rakt fram", "sväng", "U-sväng", "avfart", "rondell", "svängen", "vänstersväng", "rondellen", "avfarten")
            .forEach {
                assertTrue("motorn missar $it", VoiceGuards.hasDirectionWord(it))
                assertTrue("SafetyGate missar $it", GateDirectionCheck.hasDirectionWord(it))
            }
        assertFalse(VoiceGuards.hasDirectionWord("högre"))
        assertFalse(GateDirectionCheck.hasDirectionWord("högre"))
        assertTrue("radbanken har riktningsord", bank.lines.none { VoiceGuards.hasDirectionWord(it.text) })
    }

    @Test fun `RR-06 rader som skyller på användaren spärras`() {
        assertTrue(VoiceGuards.blamesUser("Det var ditt fel."))
        assertTrue(bank.lines.none { VoiceGuards.blamesUser(it.text) })
    }

    @Test fun `RR-07 Rikard hörs aldrig och verkliga utdrag läses inte upp`() {
        assertTrue(bank.lines.none { it.id.startsWith("R-") || "real_person_excerpt" in it.tags })
        assertTrue(archive.all().none { it.author.contains("Rikard", ignoreCase = true) })
    }

    @Test fun `RR-08 förslag och variationer kan inte vara original`() {
        val synthetic = archive.all().filter { it.author.contains("Grok Bot") || it.subtype in setOf("variation", "exempel", "navigationskommentar") }
        assertTrue(synthetic.isNotEmpty())
        assertTrue(synthetic.none { it.originClass == OriginClass.ORIGINAL_VERBATIM })
        assertTrue(archive.all().all { it.originClass in OriginClass.entries })
    }

    @Test fun `RR-10 lånade namn kommer inte in i radbanken`() {
        assertTrue(bank.lines.none { VoiceGuards.hasBorrowedName(it.text) })
        listOf("vogonkö", "Don't Panic", "handduken", "42").forEach { assertTrue(it, VoiceGuards.hasBorrowedName(it)) }
    }

    @Test fun `RR-11 bara APPROVED och relationscitat är PRIVATE`() {
        assertTrue(bank.lines.all { it.approval == Approval.APPROVED })
        assertTrue(archive.all().filter { "relation" in it.tags }.all { it.visibility == Visibility.PRIVATE })
    }

    @Test fun `RR-13 Babel-grepp finns inte i navigatorns radbank`() {
        assertTrue(bank.lines.none { it.kind == EntryKind.RULE || it.id.startsWith("BS-") })
    }

    @Test fun `RR-09 satiren är märkt som satir`() {
        val r = SatireMode(archive).reply("Varför?", SatireMode.Session(), 1)
        assertEquals("Satir", r.label)
    }
}
