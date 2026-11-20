package se.pundaren.guide.archive.api

/** Fyra ursprungsklasser (startprompten §5, 04 avsnitt 1.1). */
enum class OriginClass { ORIGINAL_VERBATIM, APPROVED_EDIT, DERIVED_RULE, SYNTHETIC_SATIRE }

/** Separat verifieringsstatus (04, 1.2). */
enum class Verification { VERIFIED_SOURCE, OWNER_CONFIRMED, DOCUMENTED_UNVERIFIED, RECALLED, UNKNOWN }

/** Bara Jimmy kan sätta APPROVED (04, 1.3). */
enum class Approval { APPROVED, PENDING, REJECTED, BLOCKED }

/** Synlighet (04, 1.4). Standard vid import är PRIVATE. Ärvs aldrig uppåt. */
enum class Visibility { PRIVATE, COMPANY, OPEN;
    /** Den striktaste av två synligheter. */
    fun strictest(other: Visibility): Visibility = if (this.ordinal <= other.ordinal) this else other
}

/** Logisktologins mekanismer M1–M8 (02, avsnitt 3). */
enum class Mechanism(val title: String) {
    M1("Paradoxal konsekvenslogik"),
    M2("Självmotsägande kontinuitet med minne"),
    M3("Omvänd kausalitet"),
    M4("Byråkratisk absurditet och överdriven precision"),
    M5("Rikardprincipen"),
    M6("Babel-Stoners cirkellogik BS-01–BS-10"),
    M7("Kosmisk proportionalitetsförskjutning"),
    M8("Observation → Paradox → Praktisk verklighet → Eventuell urspårning"),
}

enum class EntryKind { QUOTE, RULE, PATTERN, LEXICON, EXAMPLE, NAV_LINE, TAIL_LINE }

/** En arkivpost. Oföränderlig. Motsvarar tabellen archive_entry i 04, 1.5. */
data class ArchiveEntry(
    val id: String,
    val originClass: OriginClass,
    val subtype: String?,
    val kind: EntryKind,
    val text: String,
    val textSha256: String,
    val language: String,
    val verification: Verification,
    val approval: Approval,
    val visibility: Visibility,
    val author: String,
    val sourceRef: String?,
    val sourceNote: String?,
    val derivedFrom: String?,
    val legacyId: String?,
    val category: String?,
    val mechanisms: Set<Mechanism>,
    /** Fritext-taggar, t.ex. "denial" för rader som får användas av motsägelseminnet. */
    val tags: Set<String> = emptySet(),
    val revision: Int = 1,
)

/** Läsgränssnitt. Motorn och Babel ser bara detta. */
interface ArchiveReader {
    fun all(): List<ArchiveEntry>
    fun get(id: String): ArchiveEntry?
}

/** Tillåtna ändringar. Text, klass, författare, källa och id ändras aldrig på ett original. */
sealed interface ArchiveChange {
    data class SetVisibility(val visibility: Visibility, val changedBy: String) : ArchiveChange
    data class SetApproval(val approval: Approval, val changedBy: String) : ArchiveChange
    data class SetText(val text: String, val changedBy: String, val reason: String) : ArchiveChange
    data class SetOriginClass(val originClass: OriginClass, val changedBy: String) : ArchiveChange
}

sealed interface WriteResult {
    data object Ok : WriteResult
    data class Rejected(val reason: String) : WriteResult
}

data class RevisionRecord(
    val entryId: String,
    val revision: Int,
    val changedBy: String,
    val field: String,
    val oldValue: String?,
    val newValue: String?,
    val reason: String,
)

/**
 * Skrivgränssnitt. Valet mellan Room och SQLDelight (A-13) är uppskjutet;
 * varje implementation måste uppfylla samma regler som triggerna i 04, 1.6:
 * originalen kan inte ändras eller raderas, ingen post kan uppgraderas till original,
 * revisionshistoriken är bara tillägg och synlighetsändringar loggas.
 */
interface ArchiveStore : ArchiveReader {
    /** Bara import från källa får skapa ORIGINAL_VERBATIM. */
    fun importFromSource(entry: ArchiveEntry): WriteResult
    fun change(id: String, change: ArchiveChange): WriteResult
    fun delete(id: String): WriteResult
    fun revisions(): List<RevisionRecord>
}
