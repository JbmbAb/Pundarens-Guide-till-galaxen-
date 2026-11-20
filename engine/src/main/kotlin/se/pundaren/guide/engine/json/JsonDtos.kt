package se.pundaren.guide.engine.json

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QuoteFile(
    val schemaVersion: Int? = null,
    val compilation: CompilationDto? = null,
    val valuePairs: List<ValuePairDto>? = null,
    val quotes: List<QuoteDto>? = null,
    val originals: List<QuoteDto>? = null,
    val variations: List<QuoteDto>? = null,
)

@JsonClass(generateAdapter = true)
data class CompilationDto(
    val title: String? = null,
    val date: String? = null,
    val note: String? = null,
)

@JsonClass(generateAdapter = true)
data class ValuePairDto(
    val id: String? = null,
    val a: String? = null,
    val b: String? = null,
)

@JsonClass(generateAdapter = true)
data class NormalizedDto(
    val text: String? = null,
    val isOriginal: Boolean? = null,
    val note: String? = null,
)

@JsonClass(generateAdapter = true)
data class QuoteDto(
    val id: String? = null,
    val title: String? = null,
    val text: String? = null,
    val type: String? = null,
    val category: String? = null,
    val provenance: String? = null,
    val language: String? = null,
    val themes: List<String>? = null,
    val valuePairs: List<String>? = null,
    val source: String? = null,
    val sourceNote: String? = null,
    val legacyId: String? = null,
    val normalized: NormalizedDto? = null,
    val summarizesQuoteId: String? = null,
    val originalId: String? = null,
    val author: String? = null,
    val inspiredBy: List<String>? = null,
    val generatedBy: String? = null,
    val mechanism: String? = null,
    val approved: Boolean? = null,
    val status: String? = null,
)

@JsonClass(generateAdapter = true)
data class RulesFile(
    val schemaVersion: Int? = null,
    val disclaimer: String? = null,
    val rules: List<RuleDto>? = null,
    val patterns: List<PatternDto>? = null,
)

@JsonClass(generateAdapter = true)
data class RuleDto(
    val id: String? = null,
    val function: String? = null,
    val basis: String? = null,
    val relatedQuoteIds: List<String>? = null,
    val relatedPatternIds: List<String>? = null,
    val thematicQuoteIds: List<String>? = null,
    val note: String? = null,
    val provenance: String? = null,
    val author: String? = null,
    val sourceNote: String? = null,
)

@JsonClass(generateAdapter = true)
data class PatternDto(
    val id: String? = null,
    val title: String? = null,
    val section: String? = null,
    val status: String? = null,
    val isEstablishedPsychologicalFact: Boolean? = null,
    val description: String? = null,
    val mechanism: String? = null,
    val babelStonerRule: String? = null,
    val relatedRuleIds: List<String>? = null,
    val relatedExampleIds: List<String>? = null,
    val relatedQuoteIds: List<String>? = null,
    val sourceNote: String? = null,
)

@JsonClass(generateAdapter = true)
data class ExamplesFile(
    val schemaVersion: Int? = null,
    val disclaimer: String? = null,
    val examples: List<ExampleDto>? = null,
)

@JsonClass(generateAdapter = true)
data class ExampleDto(
    val id: String? = null,
    val title: String? = null,
    val provenance: String? = null,
    val section: String? = null,
    val text: String? = null,
    val sourceNote: String? = null,
    val turns: List<TurnDto>? = null,
    val result: String? = null,
    val original: String? = null,
    val babelStonerMode: String? = null,
    val response: String? = null,
    val logicAnalysis: LogicAnalysisDto? = null,
    val author: String? = null,
)

@JsonClass(generateAdapter = true)
data class TurnDto(
    val role: String? = null,
    val text: String? = null,
)

@JsonClass(generateAdapter = true)
data class LogicAnalysisDto(
    val note: String? = null,
    val labels: List<LogicLabelDto>? = null,
)

@JsonClass(generateAdapter = true)
data class LogicLabelDto(
    val label: String? = null,
    val value: String? = null,
)

@JsonClass(generateAdapter = true)
data class LexiconFile(
    val schemaVersion: Int? = null,
    val entries: List<LexiconDto>? = null,
)

@JsonClass(generateAdapter = true)
data class LexiconDto(
    val id: String? = null,
    val term: String? = null,
    val partOfSpeech: String? = null,
    val text: String? = null,
    val date: String? = null,
    val isMainVersion: Boolean? = null,
    val replacesOlderShortDefinitions: Boolean? = null,
    val sourceNote: String? = null,
    val provenance: String? = null,
    val author: String? = null,
)

@JsonClass(generateAdapter = true)
data class NavigationFile(
    val schemaVersion: Int? = null,
    val disclaimer: String? = null,
    val comments: List<NavCommentDto>? = null,
)

@JsonClass(generateAdapter = true)
data class NavCommentDto(
    val id: String? = null,
    val text: String? = null,
    val usesRikard: Boolean? = null,
    val situation: String? = null,
    val neverContradictsRoute: Boolean? = null,
    val provenance: String? = null,
    val author: String? = null,
    val sourceNote: String? = null,
)

@JsonClass(generateAdapter = true)
data class GeoJsonFeatureCollection(
    val type: String? = null,
    val features: List<GeoJsonFeature>? = null,
)

@JsonClass(generateAdapter = true)
data class GeoJsonFeature(
    val type: String? = null,
    val geometry: GeoJsonGeometry? = null,
    val properties: GeoJsonProperties? = null,
)

@JsonClass(generateAdapter = true)
data class GeoJsonGeometry(
    val type: String? = null,
    val coordinates: List<Double>? = null,
)

@JsonClass(generateAdapter = true)
data class GeoJsonProperties(
    @Json(name = "osm_id") val osmId: String? = null,
    val category: String? = null,
    val name: String? = null,
    @Json(name = "opening_hours") val openingHours: String? = null,
    val fee: String? = null,
    val wheelchair: String? = null,
    val access: String? = null,
    @Json(name = "open_late") val openLate: Boolean? = null,
)
