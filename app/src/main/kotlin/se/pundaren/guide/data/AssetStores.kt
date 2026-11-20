package se.pundaren.guide.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.pundaren.guide.engine.model.ArchiveBundle
import se.pundaren.guide.engine.model.PlaceLayer
import se.pundaren.guide.engine.places.GeoJsonStore

class AssetArchiveSource(private val context: Context) {
    fun bundle(): ArchiveBundle = ArchiveBundle(
        quotesJson = read("quotes.json"),
        rulesJson = read("rules.json"),
        examplesJson = read("examples.json"),
        lexiconJson = read("lexicon.json"),
        navigationJson = read("navigation_comments.json"),
    )

    private fun read(name: String): String = context.assets.open(name).bufferedReader().use { it.readText() }
}

class AssetGeoJsonStore(private val context: Context) : GeoJsonStore {
    override suspend fun read(layer: PlaceLayer): String = withContext(Dispatchers.IO) {
        context.assets.open(layer.assetName).bufferedReader().use { it.readText() }
    }
}
