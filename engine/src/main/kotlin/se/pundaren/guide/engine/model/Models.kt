package se.pundaren.guide.engine.model

enum class Provenance {
    ORIGINAL,
    BEARBETAD,
    REDAKTIONELL,
    KONSTRUERAD,
    VARIATION,
    FORSLAG,
    UNSPECIFIED,
    ;

    val label: String
        get() = when (this) {
            ORIGINAL -> "Original"
            BEARBETAD -> "Bearbetad"
            REDAKTIONELL -> "Redaktionell"
            KONSTRUERAD -> "Konstruerad"
            VARIATION -> "Variation"
            FORSLAG -> "Förslag"
            UNSPECIFIED -> "Utan proveniens"
        }

    val isUsersOwnQuote: Boolean
        get() = this == ORIGINAL

    val isGenerated: Boolean
        get() = when (this) {
            KONSTRUERAD, VARIATION, FORSLAG -> true
            ORIGINAL, BEARBETAD, REDAKTIONELL, UNSPECIFIED -> false
        }
}

fun parseProvenance(raw: String?): Provenance = when (raw?.trim()?.lowercase()) {
    "original" -> Provenance.ORIGINAL
    "bearbetad" -> Provenance.BEARBETAD
    "redaktionell" -> Provenance.REDAKTIONELL
    "konstruerad" -> Provenance.KONSTRUERAD
    "variation" -> Provenance.VARIATION
    "förslag", "forslag" -> Provenance.FORSLAG
    null, "" -> Provenance.UNSPECIFIED
    else -> Provenance.UNSPECIFIED
}

enum class TurnDirection {
    LEFT,
    RIGHT,
    STRAIGHT,
    ARRIVED,
}

enum class MapMode(val label: String) {
    PRACTICAL("Praktisk"),
    POETIC("Poetisk"),
    IMPROBABILITY("Osannolik"),
}

enum class AntiNavIntensity(val label: String) {
    POLITE("Artig"),
    PASSIVE_AGGRESSIVE("Passivt aggressiv"),
    MARVIN("Marvin"),
}

enum class RikardSensitivity(val label: String) {
    MANIC("Manisk"),
    DEPRESSED("Deprimerad"),
    SENTIENT("Medveten"),
}

enum class PoeticSensitivity(val label: String) {
    VOGON("Vogonpoesi"),
    EXISTENTIAL("Existentiell ångest"),
    LITERALIST("Bokstavlig"),
}

enum class InputKind {
    OBSERVATION,
    NAVIGATION,
    BABEL,
    DONT_PANIC,
}

enum class Mechanism(val prose: String) {
    ANSVARSFORSKJUTNING("Ansvar flyttat."),
    AMNESBYTE("Tonfall ifrågasatt."),
    MOTSAGELSE("Motsägelse presenterad som självklar."),
    SJALVBILDSFORSVAR("Invändningen omtolkad till ett angrepp på textens avsändare."),
    CIRKULAR_ARGUMENTATION("Samtalet cirklar utan svar."),
    OMVAND_SKULD("Omvänd skuld."),
}

enum class BabelMode(
    val id: String,
    val title: String,
    val mechanisms: List<Mechanism>,
    val ruleIds: List<String>,
) {
    ANSVARSFORSKJUTAREN(
        id = "ansvarsforskjutaren",
        title = "Ansvarsförskjutaren",
        mechanisms = listOf(Mechanism.ANSVARSFORSKJUTNING, Mechanism.OMVAND_SKULD),
        ruleIds = listOf("BS-01", "BS-02", "BS-04"),
    ),
    EVIGA_CIRKELN(
        id = "eviga-cirkeln",
        title = "Den eviga cirkeln",
        mechanisms = listOf(Mechanism.CIRKULAR_ARGUMENTATION, Mechanism.AMNESBYTE),
        ruleIds = listOf("BS-08", "BS-12", "BS-15"),
    ),
    VERKLIGHETSREVISORN(
        id = "verklighetsrevisorn",
        title = "Verklighetsrevisorn",
        mechanisms = listOf(Mechanism.MOTSAGELSE),
        ruleIds = listOf("BS-03", "BS-07", "BS-06"),
    ),
    SARADE_HJALTEN(
        id = "sarade-hjalten",
        title = "Den sårade hjälten",
        mechanisms = listOf(Mechanism.SJALVBILDSFORSVAR, Mechanism.OMVAND_SKULD),
        ruleIds = listOf("BS-05"),
    ),
    KOSMISK(
        id = "kosmisk-totalforvirring",
        title = "Kosmisk totalförvirring",
        mechanisms = Mechanism.entries.toList(),
        ruleIds = listOf("BS-09", "BS-10", "BS-11", "BS-14", "BS-16"),
    ),
    ;

    companion object {
        fun fromId(id: String?): BabelMode = entries.firstOrNull { it.id == id } ?: ANSVARSFORSKJUTAREN
    }
}

enum class PlaceLayer(val assetName: String, val label: String) {
    TOILETS("toilets.geojson", "Toaletter"),
    REST_AREAS("rest_areas.geojson", "Rastplatser"),
    MUNCHIE_STATIONS("munchie_stations.geojson", "Munchie-stationer"),
}

data class ProbabilityDrive(
    val chaos: Float = 0.4f,
    val rikardFrequency: Float = 0.45f,
    val rikardSensitivity: RikardSensitivity = RikardSensitivity.SENTIENT,
    val antiNavIntensity: AntiNavIntensity = AntiNavIntensity.PASSIVE_AGGRESSIVE,
    val poeticSensitivity: PoeticSensitivity = PoeticSensitivity.EXISTENTIAL,
    val mapMode: MapMode = MapMode.PRACTICAL,
    val seed: Long? = null,
)

data class FactPayload(
    val distanceMeters: Int? = null,
    val direction: TurnDirection? = null,
    val streetName: String? = null,
    val placeName: String? = null,
    val placeCategory: String? = null,
    val gpsUncertain: Boolean = false,
    val recalculating: Boolean = false,
)

data class GlitchEffect(val durationMillis: Int = 400)

data class PresentedQuote(
    val id: String,
    val text: String,
    val provenance: Provenance,
    val title: String? = null,
)

data class SatiricalLabel(val label: String, val value: String)

data class GalacticAnalysis(
    val answeredPercent: Int,
    val mechanisms: List<Mechanism>,
    val originalQuestionAnswered: Boolean,
    val rikardVerdict: String,
    val prose: String,
    val labels: List<SatiricalLabel>,
    val note: String,
)

data class EngineInput(
    val kind: InputKind,
    val text: String = "",
    val facts: FactPayload? = null,
    val modeId: String? = null,
)

data class EngineOutput(
    val commentary: String,
    val facts: FactPayload?,
    val usedQuoteIds: List<String>,
    val isGeneratedVariation: Boolean,
    val rikardInvolved: Boolean,
    val glitch: GlitchEffect?,
    val quotes: List<PresentedQuote> = emptyList(),
    val analysis: GalacticAnalysis? = null,
    val modeName: String? = null,
    val sourceId: String? = null,
    val sourceProvenance: Provenance? = null,
    val transmissionNumber: Int? = null,
)

data class ValuePair(val id: String, val a: String, val b: String)

data class ArchiveQuote(
    val id: String,
    val title: String?,
    val text: String,
    val provenance: Provenance,
    val themes: List<String>,
    val sourceNote: String?,
    val originalId: String?,
    val approved: Boolean?,
    val author: String?,
    val normalizedText: String?,
    val normalizedNote: String?,
    val legacyId: String?,
    val summarizesQuoteId: String?,
)

data class BabelRule(
    val id: String,
    val function: String,
    val relatedQuoteIds: List<String>,
    val thematicQuoteIds: List<String>,
    val provenance: Provenance,
    val note: String?,
)

data class BabelPattern(
    val id: String,
    val title: String,
    val status: String?,
    val isEstablishedPsychologicalFact: Boolean,
    val description: String?,
    val babelStonerRule: String?,
    val sourceNote: String?,
)

data class DialogueTurn(val role: String, val text: String)

data class BabelExample(
    val id: String,
    val title: String?,
    val provenance: Provenance,
    val text: String?,
    val original: String?,
    val response: String?,
    val turns: List<DialogueTurn>,
    val result: String?,
    val labels: List<SatiricalLabel>,
    val analysisNote: String?,
    val sourceNote: String?,
)

data class LexiconEntry(
    val id: String,
    val term: String,
    val text: String,
    val provenance: Provenance,
    val partOfSpeech: String?,
    val sourceNote: String?,
    val author: String?,
    val isMainVersion: Boolean,
)

data class NavComment(
    val id: String,
    val text: String,
    val situation: String,
    val usesRikard: Boolean,
    val neverContradictsRoute: Boolean,
    val provenance: Provenance,
)

data class LoadedArchive(
    val compilationNote: String?,
    val disclaimers: List<String>,
    val valuePairs: List<ValuePair>,
    val quotes: List<ArchiveQuote>,
    val rules: List<BabelRule>,
    val patterns: List<BabelPattern>,
    val examples: List<BabelExample>,
    val lexicon: List<LexiconEntry>,
    val navigationComments: List<NavComment>,
) {
    fun originals(): List<ArchiveQuote> = quotes.filter { it.provenance == Provenance.ORIGINAL }

    fun byId(id: String): ArchiveQuote? = quotes.firstOrNull { it.id == id }

    companion object {
        val EMPTY = LoadedArchive(
            compilationNote = null,
            disclaimers = emptyList(),
            valuePairs = emptyList(),
            quotes = emptyList(),
            rules = emptyList(),
            patterns = emptyList(),
            examples = emptyList(),
            lexicon = emptyList(),
            navigationComments = emptyList(),
        )
    }
}

data class ArchiveBundle(
    val quotesJson: String? = null,
    val rulesJson: String? = null,
    val examplesJson: String? = null,
    val lexiconJson: String? = null,
    val navigationJson: String? = null,
)

data class ReverseAnalysis(
    val satireSuppressed: Boolean,
    val supportReferral: String?,
    val summary: String,
    val unanswered: List<String>,
    val contradictions: List<QuotedPair>,
    val patternNotes: List<String>,
    val disclaimer: String,
)

data class QuotedPair(val first: String, val second: String)

data class BoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude in south..north && longitude in west..east

    companion object {
        fun world(): BoundingBox = BoundingBox(-90.0, -180.0, 90.0, 180.0)

        fun around(latitude: Double, longitude: Double, delta: Double): BoundingBox =
            BoundingBox(latitude - delta, longitude - delta, latitude + delta, longitude + delta)
    }
}

data class Place(
    val id: String,
    val name: String?,
    val layer: PlaceLayer,
    val category: String?,
    val latitude: Double,
    val longitude: Double,
    val openingHours: String?,
    val openLate: Boolean?,
    val fee: String?,
    val wheelchair: String?,
    val access: String?,
)

fun factualCategory(category: String?, layer: PlaceLayer): String = when (category) {
    "toilets" -> "Toalett"
    "rest_area" -> "Rastplats"
    "services" -> "Serviceplats"
    "fast_food" -> "Gatukök"
    "convenience" -> "Närbutik"
    "kiosk" -> "Kiosk"
    "fuel" -> "Mack"
    null, "" -> when (layer) {
        PlaceLayer.TOILETS -> "Toalett"
        PlaceLayer.REST_AREAS -> "Rastplats"
        PlaceLayer.MUNCHIE_STATIONS -> "Matställe"
    }
    else -> category
}

fun factualPlaceName(place: Place): String = place.name?.takeIf { it.isNotBlank() }
    ?: factualCategory(place.category, place.layer)

fun improbabilityAlias(category: String?): String = when (category) {
    "kiosk", "convenience" -> "Diplomatisk förrådsstation"
    "fast_food" -> "Evighetens frityr"
    "fuel" -> "Bränsleorakel"
    "toilets" -> "Nödutgång för värdighet"
    "rest_area", "services" -> "Handduksmoln"
    else -> "Osannolik punkt"
}
