package se.pundaren.guide.engine.json

import com.squareup.moshi.Moshi

object GuideMoshi {
    val moshi: Moshi = Moshi.Builder().build()

    private val quotes = QuoteFileJsonAdapter(moshi)
    private val rules = RulesFileJsonAdapter(moshi)
    private val examples = ExamplesFileJsonAdapter(moshi)
    private val lexicon = LexiconFileJsonAdapter(moshi)
    private val navigation = NavigationFileJsonAdapter(moshi)
    private val geoJson = GeoJsonFeatureCollectionJsonAdapter(moshi)

    fun quotes(json: String): QuoteFile = quotes.fromJson(json) ?: QuoteFile()

    fun rules(json: String): RulesFile = rules.fromJson(json) ?: RulesFile()

    fun examples(json: String): ExamplesFile = examples.fromJson(json) ?: ExamplesFile()

    fun lexicon(json: String): LexiconFile = lexicon.fromJson(json) ?: LexiconFile()

    fun navigation(json: String): NavigationFile = navigation.fromJson(json) ?: NavigationFile()

    fun geoJson(json: String): GeoJsonFeatureCollection =
        geoJson.fromJson(json) ?: GeoJsonFeatureCollection()
}
