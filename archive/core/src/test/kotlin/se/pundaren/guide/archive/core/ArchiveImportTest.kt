package se.pundaren.guide.archive.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.OriginClass
import se.pundaren.guide.archive.api.Verification
import se.pundaren.guide.archive.api.Visibility
import java.io.File

class ArchiveImportTest {
    private val root = File(System.getProperty("guide.repoRoot"))
    private val sources = ArchiveSources.fromRepo(root)
    private val entries = ArchiveImporter.import(sources)
    private val byId = entries.associateBy { it.id }

    @Test fun `originalen importeras byte för byte ur quotes json`() {
        val raw = Json.parseToJsonElement(sources.quotesJson).jsonObject["quotes"]!!.jsonArray
        val originals = raw.map { it.jsonObject }.filter { it["provenance"]!!.jsonPrimitive.content == "original" }
        assertEquals(19, originals.size)
        for (o in originals) {
            val id = o["id"]!!.jsonPrimitive.content
            val e = byId[id] ?: error("saknas: $id")
            assertEquals(OriginClass.ORIGINAL_VERBATIM, e.originClass)
            assertEquals("text för $id", o["text"]!!.jsonPrimitive.content, e.text)
            assertEquals(sha256Hex(e.text), e.textSha256)
        }
        // "arlset" står kvar i originalet (data/quotes/README.md, regel 1).
        assertTrue(byId["P-001"]!!.text.contains("arlset"))
    }

    @Test fun `alla poster är PRIVATE vid import`() {
        assertTrue(entries.all { it.visibility == Visibility.PRIVATE })
    }

    @Test fun `Grok Bots förslag är aldrig original och aldrig godkända`() {
        val grok = entries.filter { it.author.contains("Grok Bot") }
        assertEquals("V 20 + lexikon 12 + BS 6 + EX-F 7 + NAV 20", 65, grok.size)
        assertTrue(grok.none { it.originClass == OriginClass.ORIGINAL_VERBATIM })
        assertTrue(grok.none { it.approval == Approval.APPROVED })
    }

    @Test fun `F-10 och F-09 ger APPROVED_EDIT och originalen ligger kvar`() {
        val norm = byId["P-001-N"]!!
        assertEquals(OriginClass.APPROVED_EDIT, norm.originClass)
        assertEquals("P-001", norm.derivedFrom)
        assertTrue(norm.text.contains("arslet"))
        val red = byId["RED-P-007"]!!
        assertEquals(OriginClass.APPROVED_EDIT, red.originClass)
        assertEquals("P-007", red.derivedFrom)
        assertEquals(Verification.OWNER_CONFIRMED, red.verification)
        assertEquals(OriginClass.APPROVED_EDIT, byId["C-008"]!!.originClass)
        assertEquals(OriginClass.APPROVED_EDIT, byId["C-009"]!!.originClass)
        assertEquals(OriginClass.ORIGINAL_VERBATIM, byId["P-007"]!!.originClass)
    }

    @Test fun `verifieringsstatus följer 04`() {
        (1..7).forEach { assertEquals(Verification.RECALLED, byId["P-00$it"]!!.verification) }
        assertEquals(Verification.OWNER_CONFIRMED, byId["C-007"]!!.verification)
        assertTrue(entries.none { it.verification == Verification.VERIFIED_SOURCE })
    }

    @Test fun `F-12 och K-15 håller EX-01-FORMULERING och mönstren i arkivet`() {
        assertEquals(Approval.BLOCKED, byId["EX-01-FORMULERING"]!!.approval)
        (1..5).forEach {
            val p = byId["PAT-0$it"]!!
            assertEquals(Approval.PENDING, p.approval)
            assertTrue("archive_only" in p.tags)
        }
    }

    @Test fun `BS-01 till BS-10 är godkända regler och BS-11 till BS-16 väntar`() {
        (1..10).forEach { assertEquals(Approval.APPROVED, byId["BS-%02d".format(it)]!!.approval) }
        (11..16).forEach { assertEquals(Approval.PENDING, byId["BS-$it"]!!.approval) }
        assertTrue((1..16).all { byId["BS-%02d".format(it)]!!.originClass == OriginClass.DERIVED_RULE })
    }

    @Test fun `relationscitat och Rikard-utdrag är märkta`() {
        listOf("C-004", "C-006", "C-007", "C-008", "C-009", "P-006", "garderobsknullis").forEach {
            assertTrue("$it saknar relation", "relation" in byId[it]!!.tags)
        }
        listOf("R-001", "R-002", "R-003").forEach { assertTrue("real_person_excerpt" in byId[it]!!.tags) }
    }

    @Test fun `okänd proveniens fäller importen`() {
        val bad = sources.copy(quotesJson = sources.quotesJson.replaceFirst("\"provenance\": \"original\"", "\"provenance\": \"påhittad\""))
        try { ArchiveImporter.import(bad); fail("borde ha fallerat") } catch (e: ArchiveImportException) { assertNotNull(e.message) }
    }

    @Test fun `datafilerna ändras inte av importen`() {
        val before = root.resolve("data/quotes/quotes.json").readBytes()
        ArchiveImporter.import(sources)
        assertTrue(before.contentEquals(root.resolve("data/quotes/quotes.json").readBytes()))
        assertFalse(entries.isEmpty())
    }
}
