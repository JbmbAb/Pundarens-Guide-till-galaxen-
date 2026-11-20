package se.pundaren.guide.archive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.archive.api.ArchiveChange
import se.pundaren.guide.archive.api.OriginClass
import se.pundaren.guide.archive.api.Visibility
import se.pundaren.guide.archive.api.WriteResult
import java.io.File

/** Skrivskyddet på modellnivå. Samma regler som triggerna i 04, 1.6. */
class ArchiveStoreTest {
    private val store = InMemoryArchiveStore.of(
        ArchiveImporter.import(ArchiveSources.fromRepo(File(System.getProperty("guide.repoRoot")))),
    )

    @Test fun `original kan inte ändras i text`() {
        val before = store.get("P-001")!!
        assertTrue(store.change("P-001", ArchiveChange.SetText("Om man ...", "test", "rättning")) is WriteResult.Rejected)
        assertEquals(before, store.get("P-001"))
    }

    @Test fun `original kan inte raderas`() {
        assertTrue(store.delete("C-007") is WriteResult.Rejected)
        assertTrue(store.get("C-007") != null)
    }

    @Test fun `ingen post kan uppgraderas till original`() {
        assertTrue(store.change("V-001", ArchiveChange.SetOriginClass(OriginClass.ORIGINAL_VERBATIM, "test")) is WriteResult.Rejected)
        assertTrue(store.change("P-002", ArchiveChange.SetOriginClass(OriginClass.SYNTHETIC_SATIRE, "test")) is WriteResult.Rejected)
    }

    @Test fun `original med samma id kan inte importeras igen`() {
        val e = store.get("P-003")!!
        assertTrue(store.importFromSource(e.copy(text = "annan text", textSha256 = sha256Hex("annan text"))) is WriteResult.Rejected)
    }

    @Test fun `fel kontrollsumma stoppas vid import`() {
        val e = store.get("P-003")!!.copy(id = "P-003.r2", textSha256 = "0")
        assertTrue(InMemoryArchiveStore().importFromSource(e) is WriteResult.Rejected)
    }

    @Test fun `synlighetsändring är tillåten och loggas`() {
        assertEquals(WriteResult.Ok, store.change("C-001", ArchiveChange.SetVisibility(Visibility.COMPANY, "Jimmy")))
        val rev = store.revisions().single()
        assertEquals("visibility", rev.field)
        assertEquals("PRIVATE", rev.oldValue)
        assertEquals(2, store.get("C-001")!!.revision)
    }

    @Test fun `syntetisk text får ändras genom ny revision`() {
        assertEquals(WriteResult.Ok, store.change("V-001", ArchiveChange.SetText("ny", "Jimmy", "test")))
        assertEquals(2, store.get("V-001")!!.revision)
        assertEquals(sha256Hex("ny"), store.get("V-001")!!.textSha256)
    }
}
