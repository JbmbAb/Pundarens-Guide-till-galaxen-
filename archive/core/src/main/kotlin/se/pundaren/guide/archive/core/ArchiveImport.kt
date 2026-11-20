package se.pundaren.guide.archive.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.ArchiveEntry
import se.pundaren.guide.archive.api.EntryKind
import se.pundaren.guide.archive.api.Mechanism
import se.pundaren.guide.archive.api.OriginClass
import se.pundaren.guide.archive.api.Verification
import se.pundaren.guide.archive.api.Visibility
import java.io.File
import java.security.MessageDigest

/** Råtext för de fem datafilerna. Läses av anroparen; importen själv gör ingen I/O. */
data class ArchiveSources(
    val quotesJson: String,
    val lexiconJson: String,
    val rulesJson: String,
    val examplesJson: String,
    val navigationCommentsJson: String,
) {
    companion object {
        /** Bekvämlighet för test och verktyg på datorn. Används inte av motorn. */
        fun fromRepo(repoRoot: File): ArchiveSources = ArchiveSources(
            quotesJson = repoRoot.resolve("data/quotes/quotes.json").readText(),
            lexiconJson = repoRoot.resolve("data/lexicon/lexicon.json").readText(),
            rulesJson = repoRoot.resolve("data/babel-stoner/rules.json").readText(),
            examplesJson = repoRoot.resolve("data/babel-stoner/examples.json").readText(),
            navigationCommentsJson = repoRoot.resolve("data/babel-stoner/navigation_comments.json").readText(),
        )
    }
}

class ArchiveImportException(message: String) : RuntimeException(message)

fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/**
 * Mappning från dagens proveniensvärden till de fyra ursprungsklasserna enligt 04, avsnitt 1.3,
 * med ägarbeslutet F-10 (2026-11-20): RED-P-007 och den normaliserade P-001 blir APPROVED_EDIT.
 *
 * Regler som byggaren har lagt till och som kräver Jimmys bekräftelse (se etapp1-protokoll, D-3):
 * - Ett ORIGINAL_VERBATIM får `approval = APPROVED` för att det är arkivägarens egna ord (04 anger "–").
 * - Alla poster får `visibility = PRIVATE` vid import (04, 1.4). Det är en markering av känsligt innehåll
 *   och styr inte uppspelning (ägarbeslut 2026-11-20 kl. 23.30).
 *
 * Datafilerna ändras inte. Okända proveniensvärden fäller importen.
 */
object ArchiveImporter {
    private val json = Json { ignoreUnknownKeys = true }

    const val OWNER_SOURCE = "personliga arkivet (upphov enligt sammanställningen 2026-11-20)"
    private const val COMPILATION_REF =
        "docs/baseline/source/Citatarkiv och samtalslogik för Babel-Stoner (sammanställning 2026-11-20).txt"

    /** Relationscitat (04, 1.4). Märkta som PRIVATE och "relation"; får spelas upp överallt (ägarbeslut 2026-11-20 kl. 23.30). */
    val RELATION_IDS = setOf("C-004", "C-006", "C-007", "C-008", "C-009", "P-006", "garderobsknullis")

    fun import(sources: ArchiveSources): List<ArchiveEntry> {
        val out = mutableListOf<ArchiveEntry>()
        importQuotes(json.parseToJsonElement(sources.quotesJson).jsonObject, out)
        importLexicon(json.parseToJsonElement(sources.lexiconJson).jsonObject, out)
        importRules(json.parseToJsonElement(sources.rulesJson).jsonObject, out)
        importExamples(json.parseToJsonElement(sources.examplesJson).jsonObject, out)
        importNav(json.parseToJsonElement(sources.navigationCommentsJson).jsonObject, out)
        val dupes = out.groupBy { it.id }.filter { it.value.size > 1 }.keys
        if (dupes.isNotEmpty()) throw ArchiveImportException("Dubblerade id: $dupes")
        return out
    }

    private fun importQuotes(root: JsonObject, out: MutableList<ArchiveEntry>) {
        for (q in root.arr("quotes")) {
            val o = q.jsonObject
            val id = o.str("id")!!
            val text = o.str("text") ?: throw ArchiveImportException("$id saknar text")
            val category = o.str("category")
            val tags = buildSet {
                if (id in RELATION_IDS) add("relation")
                if (category == "rikard") add("real_person_excerpt")
            }
            val mech = if (category == "rikard") setOf(Mechanism.M5) else setOf(Mechanism.M1)
            when (val prov = o.str("provenance")) {
                "original" -> {
                    val verification = when {
                        id == "C-007" -> Verification.OWNER_CONFIRMED
                        id.startsWith("P-") -> Verification.RECALLED
                        else -> Verification.DOCUMENTED_UNVERIFIED
                    }
                    out += entry(
                        id, OriginClass.ORIGINAL_VERBATIM, null, EntryKind.QUOTE, text, verification,
                        Approval.APPROVED, OWNER_SOURCE, "$COMPILATION_REF#$id", o.str("sourceNote"), null,
                        o.str("legacyId"), category, mech, tags,
                    )
                    // F-10: den normaliserade P-001 blir APPROVED_EDIT. Originalet ligger kvar orört.
                    val norm = o["normalized"]?.takeIf { it !is JsonNull }?.jsonObject
                    if (norm != null) {
                        out += entry(
                            "$id-N", OriginClass.APPROVED_EDIT, "normalisering", EntryKind.QUOTE,
                            norm.str("text")!!, Verification.OWNER_CONFIRMED, Approval.APPROVED,
                            "bearbetning av $id, godkänd av Jimmy Bruce 2026-11-20 (F-10)",
                            "data/quotes/quotes.json#$id.normalized", norm.str("note"), id, null, category, mech, tags,
                        )
                    }
                }
                "bearbetad" -> out += entry(
                    id, OriginClass.APPROVED_EDIT, null, EntryKind.QUOTE, text, Verification.DOCUMENTED_UNVERIFIED,
                    Approval.APPROVED, "gemensamt bearbetad meddelandetext 2026-11-17 (F-09)",
                    "$COMPILATION_REF#$id", o.str("sourceNote"), null, o.str("legacyId"), category, mech, tags,
                )
                "redaktionell" -> out += entry(
                    // F-10: RED-P-007 blir APPROVED_EDIT.
                    id, OriginClass.APPROVED_EDIT, "redaktionell_sammanfattning", EntryKind.QUOTE, text,
                    Verification.OWNER_CONFIRMED, Approval.APPROVED,
                    "redaktionell sammanfattning, godkänd av Jimmy Bruce 2026-11-20 (F-10)",
                    "$COMPILATION_REF#$id", o.str("sourceNote"), o.str("summarizesQuoteId"), null, category, mech, tags,
                )
                else -> throw ArchiveImportException("$id har okänd proveniens '$prov'")
            }
        }
        for (v in root.arr("variations")) {
            val o = v.jsonObject
            val id = o.str("id")!!
            val prov = o.str("provenance")
            if (prov != "variation") throw ArchiveImportException("$id: variation med proveniens '$prov'")
            val origin = o.str("originalId")
            // Synlighet ärvs aldrig uppåt; variation av relationscitat är också relation.
            val tags = if (origin != null && origin in RELATION_IDS) setOf("relation") else emptySet()
            out += entry(
                id, OriginClass.SYNTHETIC_SATIRE, "variation", EntryKind.QUOTE, o.str("text")!!,
                Verification.UNKNOWN, approvalFromFlag(o), o.str("author") ?: "okänd",
                "data/quotes/quotes.json#$id", o.str("sourceNote"), origin, null, null,
                mechanismFromWord(o.str("mechanism")), tags + flagTags(o),
            )
        }
    }

    private fun importLexicon(root: JsonObject, out: MutableList<ArchiveEntry>) {
        for (e in root.arr("entries")) {
            val o = e.jsonObject
            val id = o.str("id")!!
            val text = o.str("text")!!
            when (val prov = o.str("provenance")) {
                null -> out += entry(
                    id, OriginClass.ORIGINAL_VERBATIM, null, EntryKind.LEXICON, text,
                    Verification.DOCUMENTED_UNVERIFIED, Approval.APPROVED, OWNER_SOURCE,
                    "data/lexicon/lexicon.json#$id", o.str("sourceNote"), null, null, "lexikon", emptySet(),
                    if (id in RELATION_IDS) setOf("relation") else emptySet(),
                )
                "förslag" -> out += entry(
                    id, OriginClass.SYNTHETIC_SATIRE, "lexikonförslag", EntryKind.LEXICON, text,
                    Verification.UNKNOWN, approvalFromFlag(o), o.str("author") ?: "okänd",
                    "data/lexicon/lexicon.json#$id", o.str("sourceNote"), null, null, "lexikon", emptySet(), flagTags(o),
                )
                else -> throw ArchiveImportException("Lexikon $id har okänd proveniens '$prov'")
            }
        }
    }

    private fun importRules(root: JsonObject, out: MutableList<ArchiveEntry>) {
        for (r in root.arr("rules")) {
            val o = r.jsonObject
            val id = o.str("id")!!
            val prov = o.str("provenance")
            val mech = buildSet {
                add(Mechanism.M6)
                if (id == "BS-02" || id == "BS-06") add(Mechanism.M3)
                if (id == "BS-09") add(Mechanism.M5)
            }
            val related = (o.strList("relatedQuoteIds") + o.strList("thematicQuoteIds")).distinct()
            val tags = related.map { "quote:$it" }.toSet() + ("basis:" + (o.str("basis") ?: ""))
            when (prov) {
                null -> out += entry(
                    id, OriginClass.DERIVED_RULE, null, EntryKind.RULE, o.str("function")!!,
                    Verification.DOCUMENTED_UNVERIFIED, Approval.APPROVED, "sammanställningen 2026-11-20",
                    "$COMPILATION_REF#$id", o.str("sourceNote"), null, null, "babel-regel", mech, tags,
                )
                "förslag" -> out += entry(
                    id, OriginClass.DERIVED_RULE, "regelförslag", EntryKind.RULE, o.str("function")!!,
                    Verification.UNKNOWN, approvalFromFlag(o), o.str("author") ?: "okänd",
                    "data/babel-stoner/rules.json#$id", o.str("sourceNote"), null, null, "babel-regel", mech, tags + flagTags(o),
                )
                else -> throw ArchiveImportException("Regel $id har okänd proveniens '$prov'")
            }
        }
        for (p in root.arr("patterns")) {
            val o = p.jsonObject
            val id = o.str("id")!!
            out += entry(
                id, OriginClass.DERIVED_RULE, "mönsterhypotes", EntryKind.PATTERN, o.str("description")!!,
                Verification.UNKNOWN, Approval.PENDING, "sammanställningen 2026-11-20",
                "data/babel-stoner/rules.json#$id", o.str("sourceNote"), null, null, "mönster", emptySet(),
                setOf("archive_only"),
            )
        }
    }

    private fun importExamples(root: JsonObject, out: MutableList<ArchiveEntry>) {
        for (e in root.arr("examples")) {
            val o = e.jsonObject
            val id = o.str("id")!!
            val prov = o.str("provenance")
            if (prov != "konstruerad") throw ArchiveImportException("Exempel $id har proveniens '$prov'")
            val blocked = id == "EX-01-FORMULERING"
            val text = o.str("text") ?: o["response"]?.let(::flatten) ?: o.str("title") ?: id
            out += entry(
                id, OriginClass.SYNTHETIC_SATIRE, "exempel", EntryKind.EXAMPLE, text, Verification.UNKNOWN,
                if (blocked) Approval.BLOCKED else approvalFromFlag(o), o.str("author") ?: "sammanställningen 2026-11-20",
                "data/babel-stoner/examples.json#$id", o.str("sourceNote"), null, null, "exempel", setOf(Mechanism.M6),
                (if (blocked) setOf("återger_verklig_kommunikation", "archive_only") else emptySet()) + flagTags(o) +
                    listOfNotNull(o.str("original")?.let { "question:$it" }),
            )
        }
    }

    private fun importNav(root: JsonObject, out: MutableList<ArchiveEntry>) {
        for (c in root.arr("comments")) {
            val o = c.jsonObject
            val id = o.str("id")!!
            out += entry(
                id, OriginClass.SYNTHETIC_SATIRE, "navigationskommentar", EntryKind.NAV_LINE, o.str("text")!!,
                Verification.UNKNOWN, approvalFromFlag(o), o.str("author") ?: "okänd",
                "data/babel-stoner/navigation_comments.json#$id", o.str("sourceNote"), null, null,
                o.str("situation"), navMechanisms(o), flagTags(o) + listOfNotNull(o.str("situation")?.let { "situation:$it" }),
            )
        }
    }

    /** Bara Jimmy kan godkänna (04, 1.3): `approved: true` räknas bara med `approvedBy = "Jimmy Bruce"`. */
    const val OWNER = "Jimmy Bruce"

    private fun approvalFromFlag(o: JsonObject): Approval =
        if ((o["approved"] as? JsonPrimitive)?.booleanOrNull == true && o.str("approvedBy") == OWNER) Approval.APPROVED
        else Approval.PENDING

    /** F-11, ägarbeslut 2026-11-20 kl. 23.36: arbetsnamn (B-12) och rader som bara får användas till fots. */
    private fun flagTags(o: JsonObject): Set<String> = buildSet {
        if ((o["workingName"] as? JsonPrimitive)?.booleanOrNull == true) add(TAG_WORKING_NAME)
        if ((o["footOnly"] as? JsonPrimitive)?.booleanOrNull == true) add(TAG_FOOT_ONLY)
        o.str("approvedAt")?.let { add("approvedAt:$it") }
    }

    /** Byggarens klassning (D-12, utvidgad): kommentarerna följer svansens struktur (M8); Rikard ger M5. */
    private fun navMechanisms(o: JsonObject): Set<Mechanism> = buildSet {
        add(Mechanism.M8)
        if ((o["usesRikard"] as? JsonPrimitive)?.booleanOrNull == true) add(Mechanism.M5)
    }

    const val TAG_WORKING_NAME = "arbetsnamn"
    const val TAG_FOOT_ONLY = "foot_only"

    private fun mechanismFromWord(word: String?): Set<Mechanism> = when (word) {
        "paradox" -> setOf(Mechanism.M1)
        else -> emptySet()
    }

    @Suppress("LongParameterList")
    private fun entry(
        id: String, origin: OriginClass, subtype: String?, kind: EntryKind, text: String,
        verification: Verification, approval: Approval, author: String, sourceRef: String?,
        sourceNote: String?, derivedFrom: String?, legacyId: String?, category: String?,
        mechanisms: Set<Mechanism>, tags: Set<String>,
    ) = ArchiveEntry(
        id = id, originClass = origin, subtype = subtype, kind = kind, text = text,
        textSha256 = sha256Hex(text), language = "sv", verification = verification, approval = approval,
        visibility = Visibility.PRIVATE, author = author, sourceRef = sourceRef, sourceNote = sourceNote,
        derivedFrom = derivedFrom, legacyId = legacyId, category = category, mechanisms = mechanisms, tags = tags,
    )

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.arr(key: String): JsonArray = this[key]?.jsonArray ?: JsonArray(emptyList())
    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    private fun flatten(e: JsonElement): String = when (e) {
        is JsonPrimitive -> e.contentOrNull ?: ""
        is JsonArray -> e.joinToString(" ") { flatten(it) }
        is JsonObject -> e.values.joinToString(" ") { flatten(it) }
        JsonNull -> ""
    }.trim()
}
