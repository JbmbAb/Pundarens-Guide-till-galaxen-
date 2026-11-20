package se.pundaren.guide.engine

import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.ArchiveEntry
import se.pundaren.guide.archive.api.ArchiveReader
import se.pundaren.guide.archive.api.EntryKind
import se.pundaren.guide.archive.api.OriginClass

/** Varför en post inte kom in i radbanken. Redovisas för granskning. */
enum class Exclusion {
    NOT_APPROVED,          // RR-11, G0-grindbeslutet: bara APPROVED
    ORIGIN_NOT_ALLOWED,    // bara ORIGINAL_VERBATIM och APPROVED_EDIT tills F-11 är gjord
    KIND_NOT_TAIL,         // regler, lexikon, exempel och mönster är inte svansar (RR-13, F-12)
    REAL_PERSON_EXCERPT,   // RR-07: R-001–R-003 läses inte upp
    ARCHIVE_ONLY,          // F-12
    BORROWED_NAME,         // RR-10, B-12
    BLAMES_USER,           // RR-06
    SOUNDS_LIKE_INSTRUCTION, // RR-04
}

/**
 * Radbanken byggs ur arkivet vid start. Bara poster som klarar alla spärrar kommer in.
 * Det finns ingen väg in för text som inte är en arkivpost; motorn skriver ingen egen text.
 */
class LineBank private constructor(
    val lines: List<ArchiveEntry>,
    val denialLines: List<ArchiveEntry>,
    val excluded: Map<String, Exclusion>,
) {
    companion object {
        val ALLOWED_ORIGINS = setOf(OriginClass.ORIGINAL_VERBATIM, OriginClass.APPROVED_EDIT)
        val TAIL_KINDS = setOf(EntryKind.QUOTE, EntryKind.TAIL_LINE)
        const val DENIAL_TAG = "denial"

        fun from(reader: ArchiveReader): LineBank {
            val ok = mutableListOf<ArchiveEntry>()
            val excluded = LinkedHashMap<String, Exclusion>()
            for (e in reader.all().sortedBy { it.id }) {
                val reason = exclusionFor(e)
                if (reason == null) ok += e else excluded[e.id] = reason
            }
            val (denials, normal) = ok.partition { DENIAL_TAG in it.tags }
            return LineBank(normal, denials, excluded)
        }

        fun exclusionFor(e: ArchiveEntry): Exclusion? = when {
            e.approval != Approval.APPROVED -> Exclusion.NOT_APPROVED
            e.originClass !in ALLOWED_ORIGINS -> Exclusion.ORIGIN_NOT_ALLOWED
            e.kind !in TAIL_KINDS -> Exclusion.KIND_NOT_TAIL
            "real_person_excerpt" in e.tags -> Exclusion.REAL_PERSON_EXCERPT
            "archive_only" in e.tags -> Exclusion.ARCHIVE_ONLY
            VoiceGuards.hasBorrowedName(e.text) -> Exclusion.BORROWED_NAME
            VoiceGuards.blamesUser(e.text) -> Exclusion.BLAMES_USER
            VoiceGuards.soundsLikeInstruction(e.text) -> Exclusion.SOUNDS_LIKE_INSTRUCTION
            else -> null
        }
    }
}
