package se.pundaren.guide.encyclopedia

import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.ArchiveEntry
import se.pundaren.guide.archive.api.ArchiveReader
import se.pundaren.guide.archive.api.EntryKind
import se.pundaren.guide.truth.api.GeoPoint
import java.time.Instant
import java.util.Locale

/**
 * En fältanteckning: något användaren själv har sett och skrivit ned (C-003: "Skriv vad du hittar,
 * inte vad du antar."). Texten ändras aldrig efter att den har sparats.
 */
data class FieldNote(
    val id: String,
    val atUtc: Instant,
    /** Var anteckningen gjordes, om enheten hade en verklig position då. */
    val point: GeoPoint?,
    /** Närmaste namngivna plats i offlinepaketet vid tillfället, om någon. */
    val placeName: String?,
    val text: String,
) {
    /** Redaktionens strykning: anteckningen kortad till två ord. Originalet ligger kvar i [text]. */
    val editorsCut: String get() = EditorsCut.twoWords(text)
}

/**
 * Anteckningsboken (fältforskarprovet i 02: encyklopedin fylls med egna fältanteckningar, inte
 * genererad text). Bara tillägg: en anteckning kan inte ändras eller skrivas över. Boken gör ingen
 * I/O själv; [encode] och [decode] ger raderna som den som äger filen sparar och läser.
 */
class FieldNotebook(existing: List<FieldNote> = emptyList()) {
    private val kept = ArrayList(existing)

    /** Anteckningarna i den ordning de skrevs. */
    val notes: List<FieldNote> get() = kept.toList()

    /** Sparar en anteckning ordagrant. Null om texten är tom. */
    fun add(text: String, atUtc: Instant, point: GeoPoint?, placeName: String?): FieldNote? {
        if (text.isBlank()) return null
        val note = FieldNote("FA-%013d-%03d".format(Locale.ROOT, atUtc.toEpochMilli(), kept.size + 1), atUtc, point, placeName?.takeIf { it.isNotBlank() }, text.trim())
        kept += note
        return note
    }

    companion object {
        /** En anteckning som en rad: fälten skilda med tabb, och tabb, radbrytning och bakstreck skrivna som `\t`, `\n`, `\\`. */
        fun encode(note: FieldNote): String = listOf(
            note.id, note.atUtc.toEpochMilli().toString(),
            note.point?.lat?.let { String.format(Locale.ROOT, "%.6f", it) }.orEmpty(),
            note.point?.lon?.let { String.format(Locale.ROOT, "%.6f", it) }.orEmpty(),
            escape(note.placeName.orEmpty()), escape(note.text),
        ).joinToString("\t")

        /** Läser en rad. Null om raden inte är en anteckning. */
        fun decode(line: String): FieldNote? {
            val f = line.split('\t')
            if (f.size != 6 || f[0].isEmpty()) return null
            val at = f[1].toLongOrNull() ?: return null
            val lat = f[2].toDoubleOrNull(); val lon = f[3].toDoubleOrNull()
            val text = unescape(f[5])
            if (text.isBlank()) return null
            return FieldNote(f[0], Instant.ofEpochMilli(at), if (lat != null && lon != null) GeoPoint(lat, lon) else null, unescape(f[4]).ifBlank { null }, text)
        }

        private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")

        private fun unescape(s: String): String {
            val out = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) { 't' -> out.append('\t'); 'n' -> out.append('\n'); else -> out.append(s[i + 1]) }
                    i += 2
                } else { out.append(c); i++ }
            }
            return out.toString()
        }
    }
}

/**
 * Redaktionens strykning (byggplanen: "Din långa fältanteckning kortad till två ord. Originalet
 * ligger kvar"). Skämtet bär last genom att bara använda anteckningens egna ord: de två längsta
 * som inte är funktionsord, i den ordning de står. Inget ord läggs till och inget hittas på.
 */
object EditorsCut {
    private val word = Regex("""[\p{L}\p{Nd}]+(?:-[\p{L}\p{Nd}]+)*""")
    private val functionWords = setOf(
        "och", "att", "det", "som", "den", "detta", "denna", "dessa", "eller", "men", "för", "med", "till", "från", "över", "under", "efter",
        "innan", "utan", "inte", "hade", "har", "var", "vara", "varit", "blev", "blir", "skulle", "kunde", "måste", "också", "bara", "mycket",
        "sedan", "där", "här", "när", "hur", "vad", "någon", "något", "några", "alla", "allt", "fick", "får", "gick", "kom", "via", "runt",
    )

    fun twoWords(text: String): String {
        val words = word.findAll(text).map { it.value }.toList()
        if (words.size <= 2) return words.joinToString(" ")
        val candidates = words.withIndex().filter { it.value.lowercase() !in functionWords }.ifEmpty { words.withIndex().toList() }
        // De två längsta; vid lika längd det som står först. Samma ord räknas en gång.
        val chosen = candidates.distinctBy { it.value.lowercase() }
            .sortedWith(compareByDescending<IndexedValue<String>> { it.value.length }.thenBy { it.index })
            .take(2).sortedBy { it.index }
        return chosen.joinToString(" ") { it.value }
    }
}

/** Arkivets godkända lexikon. Encyklopedin visar posterna med id och klass; den skriver inga egna. */
object ApprovedLexicon {
    fun entries(archive: ArchiveReader): List<ArchiveEntry> =
        archive.all().filter { it.kind == EntryKind.LEXICON && it.approval == Approval.APPROVED && "archive_only" !in it.tags }.sortedBy { it.id }
}
