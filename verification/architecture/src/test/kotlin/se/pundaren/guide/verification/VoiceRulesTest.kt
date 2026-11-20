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
import se.pundaren.guide.safetygate.GateInput
import se.pundaren.guide.safetygate.PresentationDecision
import se.pundaren.guide.safetygate.SafetyGate
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.engine.api.TailKind
import se.pundaren.guide.truth.api.AudioOutput
import se.pundaren.guide.truth.api.NavigationSnapshot
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.api.PositionSnapshot
import se.pundaren.guide.truth.api.SafetySnapshot
import se.pundaren.guide.truth.api.TravelMode
import java.io.File

/** Röstreglerna i 12 som körbara tester. Regler som inte kan testas maskinellt står som NOT_VERIFIED i protokollet. */
class VoiceRulesTest {
    private val archive = InMemoryArchiveStore.of(ArchiveImporter.import(ArchiveSources.fromRepo(File(System.getProperty("guide.repoRoot")))))
    private val bank = LineBank.from(archive)

    @Test fun `RR-01 varje rad i radbanken bär minst en mekanism`() =
        assertTrue(bank.lines.all { it.mechanisms.isNotEmpty() })

    @Test fun `RR-04 rader som låter som besked finns bara som rader till fots`() =
        assertTrue(bank.lines.filter { VoiceGuards.soundsLikeInstruction(it.text) || GateDirectionCheck.soundsLikeInstruction(it.text) }.all { LineBank.isFootOnly(it) })

    @Test fun `RR-05 spärrlistan omfattar böjningar men inte högre`() {
        listOf("vänster", "Höger", "rakt fram", "sväng", "U-sväng", "avfart", "rondell", "svängen", "vänstersväng", "rondellen", "avfarten")
            .forEach {
                assertTrue("motorn missar $it", VoiceGuards.hasDirectionWord(it))
                assertTrue("SafetyGate missar $it", GateDirectionCheck.hasDirectionWord(it))
            }
        assertFalse(VoiceGuards.hasDirectionWord("högre"))
        assertFalse(GateDirectionCheck.hasDirectionWord("högre"))
        // Rader med riktningsord godkändes av Jimmy 23.36 som "bara till fots"; spärren i fordonsläge ligger kvar.
        assertTrue("riktningsord utan fotmärkning", bank.lines.filter { VoiceGuards.hasDirectionWord(it.text) }.all { LineBank.isFootOnly(it) })
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

    @Test fun `RR-10 lånade namn finns bara som märkta arbetsnamn`() {
        assertTrue(bank.lines.filter { VoiceGuards.hasBorrowedName(it.text) }.all { LineBank.WORKING_NAME_TAG in it.tags })
        listOf("vogonkö", "Don't Panic", "handduken", "42").forEach { assertTrue(it, VoiceGuards.hasBorrowedName(it)) }
    }

    @Test fun `RR-11 bara APPROVED, relationscitat märkta, och privata rader hörs överallt`() {
        assertTrue(bank.lines.all { it.approval == Approval.APPROVED })
        // Markeringen finns kvar som uppgift om känsligt innehåll.
        assertTrue(archive.all().filter { "relation" in it.tags }.all { it.visibility == Visibility.PRIVATE })
        // Ägarbeslut 2026-11-20 kl. 23.30: "I helvete allt ska höras lägg av med detta trams".
        val gate = SafetyGate()
        val privateLine = bank.lines.first { "relation" in it.tags }
        val c = TailCandidate("TAIL-${privateLine.id}", privateLine.text, privateLine.id, privateLine.originClass, privateLine.approval,
            privateLine.visibility, TailKind.SPOKEN, privateLine.mechanisms, false, 2_000, "EV-1", 20_000)
        AudioOutput.entries.forEach { out ->
            listOf(false, true).forEach { convoy ->
                val i = GateInput(1_000, SafetySnapshot(false, null, 80, convoy, false, out),
                    NavigationSnapshot(true, TravelMode.PASSENGER, 30.0, 120.0, 90_000, false, false),
                    PositionSnapshot(PositionQuality.GOOD, 5.0, 1.0, null))
                assertTrue("$out konvoj=$convoy", gate.decide(i, c) is PresentationDecision.Present)
            }
        }
    }

    @Test fun `RR-13 Babel-grepp finns inte i navigatorns radbank`() {
        assertTrue(bank.lines.none { it.kind == EntryKind.RULE || it.id.startsWith("BS-") })
    }

    @Test fun `RR-09 satiren är märkt som satir`() {
        val r = SatireMode(archive).reply("Varför?", SatireMode.Session(), 1)
        assertEquals("Satir", r.label)
    }
}
