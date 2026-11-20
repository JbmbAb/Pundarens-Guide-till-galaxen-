package se.pundaren.guide.engine.places

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import se.pundaren.guide.engine.json.GuideMoshi
import se.pundaren.guide.engine.model.BoundingBox
import se.pundaren.guide.engine.model.Place
import se.pundaren.guide.engine.model.PlaceLayer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

interface GeoJsonStore {
    suspend fun read(layer: PlaceLayer): String
}

interface PlacesRepository {
    suspend fun getPlaces(bbox: BoundingBox, layers: Set<PlaceLayer>): List<Place>
}

class GeoJsonParser {
    fun parse(json: String, layer: PlaceLayer): List<Place> {
        val collection = GuideMoshi.geoJson(json)
        return collection.features.orEmpty().mapIndexedNotNull { index, feature ->
            val geometry = feature.geometry ?: return@mapIndexedNotNull null
            if (geometry.type != null && geometry.type != "Point") return@mapIndexedNotNull null
            val coordinates = geometry.coordinates ?: return@mapIndexedNotNull null
            if (coordinates.size < 2) return@mapIndexedNotNull null
            val properties = feature.properties
            Place(
                id = properties?.osmId?.takeIf { it.isNotBlank() } ?: "${layer.name}:$index",
                name = properties?.name?.takeIf { it.isNotBlank() },
                layer = layer,
                category = properties?.category,
                latitude = coordinates[1],
                longitude = coordinates[0],
                openingHours = properties?.openingHours,
                openLate = properties?.openLate,
                fee = properties?.fee,
                wheelchair = properties?.wheelchair,
                access = properties?.access,
            )
        }
    }
}

class BundledGeoJsonPlacesRepository(
    private val store: GeoJsonStore,
    private val parser: GeoJsonParser = GeoJsonParser(),
) : PlacesRepository {
    private val mutex = Mutex()
    private var cache: Map<PlaceLayer, List<Place>>? = null

    override suspend fun getPlaces(bbox: BoundingBox, layers: Set<PlaceLayer>): List<Place> {
        val all = load()
        return layers.flatMap { layer -> all[layer].orEmpty() }
            .filter { bbox.contains(it.latitude, it.longitude) }
    }

    private suspend fun load(): Map<PlaceLayer, List<Place>> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: PlaceLayer.entries.associateWith { layer ->
                parser.parse(store.read(layer), layer)
            }.also { cache = it }
        }
    }
}

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val earthRadius = 6_371_000.0
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaPhi = Math.toRadians(lat2 - lat1)
    val deltaLambda = Math.toRadians(lon2 - lon1)
    val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
        cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadius * c).roundToInt()
}
