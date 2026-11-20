package se.pundaren.guide.engine

import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.ArchiveEntry
import se.pundaren.guide.archive.api.ArchiveReader
import se.pundaren.guide.archive.api.EntryKind
import se.pundaren.guide.archive.api.OriginClass

/** Varför en post inte kom in i radbanken. Redovisas för granskning. */
enum class Exclusion {
    NOT_APPROVED,          // RR-11: bara APPROVED
    ORIGIN_NOT_ALLOWED,    // DERIVED_RULE är regler, inte svansar
    KIND_NOT_TAIL,         // regler, lexikon, exempel och mönster är inte svansar i navigatorn (RR-13, F-12)
    REAL_PERSON_EXCERPT,   // RR-07 och D-8: R-001–R-003 hålls utanför
    ARCHIVE_ONLY,          // F-12
    BORROWED_NAME,         // RR-10, B-12: lånat namn utan märkning som arbetsnamn
    BLAMES_USER,           // RR-06
    SOUNDS_LIKE_INSTRUCTION, // RR-04: låter som besked utan att vara märkt "bara till fots"
    NO_MECHANISM,          // RR-01
}

/**
 * Radbanken byggs ur arkivet vid start. Bara poster som klarar alla spärrar kommer in.
 * Motorn skriver ingen egen text; allt den säger är en godkänd arkivpost.
 *
 * Sedan ägarbeslutet 2026-11-20 kl. 23.36 (F-11, "Godkänn allt …") ingår också godkända
 * `SYNTHETIC_SATIRE`-poster: variationerna och navigationskommentarerna.
 */
class LineBank private constructor(
    val lines: List<ArchiveEntry>,
    val denialLines: List<ArchiveEntry>,
    val excluded: Map<String, Exclusion>,
) {
    companion object {
        val ALLOWED_ORIGINS = setOf(OriginClass.ORIGINAL_VERBATIM, OriginClass.APPROVED_EDIT, OriginClass.SYNTHETIC_SATIRE)
        val TAIL_KINDS = setOf(EntryKind.QUOTE, EntryKind.TAIL_LINE, EntryKind.NAV_LINE)
        const val DENIAL_TAG = "denial"
        const val FOOT_ONLY_TAG = "foot_only"
        const val WORKING_NAME_TAG = "arbetsnamn"

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
            VoiceGuards.hasBorrowedName(e.text) && WORKING_NAME_TAG !in e.tags -> Exclusion.BORROWED_NAME
            VoiceGuards.blamesUser(e.text) -> Exclusion.BLAMES_USER
            VoiceGuards.soundsLikeInstruction(e.text) && FOOT_ONLY_TAG !in e.tags -> Exclusion.SOUNDS_LIKE_INSTRUCTION
            e.mechanisms.isEmpty() -> Exclusion.NO_MECHANISM
            else -> null
        }

        /** Får bara presenteras till fots: märkt i F-11, eller innehåller riktningsord eller beskedsliknande mönster. */
        fun isFootOnly(e: ArchiveEntry): Boolean =
            FOOT_ONLY_TAG in e.tags || VoiceGuards.hasDirectionWord(e.text) || VoiceGuards.soundsLikeInstruction(e.text)
    }
}
