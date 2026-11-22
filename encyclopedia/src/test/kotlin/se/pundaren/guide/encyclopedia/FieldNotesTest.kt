package se.pundaren.guide.encyclopedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.EntryKind
import se.pundaren.guide.archive.core.ArchiveImporter
import se.pundaren.guide.archive.core.ArchiveSources
import se.pundaren.guide.archive.core.InMemoryArchiveStore
import se.pundaren.guide.truth.api.GeoPoint
import java.io.File
import java.time.Instant

/** Encyklopedin (etapp 7, G8): bara fältanteckningar och arkivets godkända lexikon. */
class FieldNotesTest {
    private val t0 = Instant.parse("2026-11-21T10:00:00Z")
    private val orsa = GeoPoint(61.120118, 14.622254)
    private val long = "Bron vid forsen var avstängd och vi fick gå runt via kraftstationen i regnet."

    @Test fun `en anteckning sparas ordagrant och går inte att ändra`() {
        val book = FieldNotebook()
        val note = book.add("  $long  ", t0, orsa, "Orsa")!!
        assertEquals(long, note.text)
        assertEquals(orsa, note.point); assertEquals("Orsa", note.placeName); assertEquals(t0, note.atUtc)
        assertTrue(note.id.startsWith("FA-"))
        assertNull("tom anteckning sparas inte", book.add("   ", t0, null, null))
        book.add("Vindskyddet har ved.", t0.plusSeconds(60), null, " ")
        assertEquals(2, book.notes.size)
        assertNull(book.notes[1].placeName)
        assertEquals("ordningen är den de skrevs i", listOf(long, "Vindskyddet har ved."), book.notes.map { it.text })
        assertEquals(2, book.notes.map { it.id }.toSet().size)
    }

    @Test fun `redaktionens strykning är två av anteckningens egna ord och originalet ligger kvar`() {
        val note = FieldNotebook().add(long, t0, orsa, null)!!
        assertEquals("avstängd kraftstationen", note.editorsCut)
        assertEquals(long, note.text)
        note.editorsCut.split(" ").forEach { assertTrue("«$it» står i originalet", it in long) }
        // Korta anteckningar stryks inte.
        assertEquals("Ved finns", EditorsCut.twoWords("Ved finns."))
        assertEquals("Hal", EditorsCut.twoWords("Hal!"))
        assertEquals("", EditorsCut.twoWords(" … "))
        // Samma ord räknas en gång, och ordningen är originalets.
        assertEquals("Rastplatsen översvämmad", EditorsCut.twoWords("Rastplatsen var översvämmad. Rastplatsen går inte att nå."))
        // Bara funktionsord: de två längsta ändå.
        assertEquals("skulle också", EditorsCut.twoWords("det skulle också vara"))
        repeat(50) { assertEquals(note.editorsCut, EditorsCut.twoWords(long)) }
    }

    @Test fun `anteckningar går runt som rader även med tabbar, radbrytningar och bakstreck`() {
        val book = FieldNotebook()
        val tricky = "Rad ett\nRad två\tmed tabb och \\ bakstreck"
        val notes = listOf(
            book.add(long, t0, orsa, "Orsa")!!,
            book.add(tricky, t0.plusSeconds(5), null, null)!!,
            book.add("Skylt: \"Enskild väg\"", t0.plusSeconds(9), GeoPoint(-33.5, -70.25), "By\tmed tabb")!!,
        )
        val lines = notes.map(FieldNotebook::encode)
        lines.forEach { assertTrue("en rad per anteckning", '\n' !in it && '\r' !in it) }
        assertEquals(notes, lines.map { FieldNotebook.decode(it)!! })
        assertEquals(notes, FieldNotebook(lines.mapNotNull(FieldNotebook::decode)).notes)
        assertNull(FieldNotebook.decode("inte en anteckning"))
        assertNull(FieldNotebook.decode("FA-1\tinte-ett-tal\t\t\t\ttext"))
        assertNotNull(FieldNotebook.decode("FA-1\t1000\t\t\t\ttext"))
    }

    @Test fun `lexikonet visar bara godkända lexikonposter ur arkivet`() {
        val archive = InMemoryArchiveStore.of(ArchiveImporter.import(ArchiveSources.fromRepo(File(System.getProperty("guide.repoRoot")))))
        val entries = ApprovedLexicon.entries(archive)
        assertTrue(entries.isNotEmpty())
        entries.forEach { assertEquals(EntryKind.LEXICON, it.kind); assertEquals(Approval.APPROVED, it.approval) }
        assertEquals(entries.map { it.id }.sorted(), entries.map { it.id })
        val all = archive.all().filter { it.kind == EntryKind.LEXICON }
        assertEquals(all.count { it.approval == Approval.APPROVED && "archive_only" !in it.tags }, entries.size)
    }
}
