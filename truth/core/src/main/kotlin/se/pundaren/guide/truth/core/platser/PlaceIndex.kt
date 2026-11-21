package se.pundaren.guide.truth.core.platser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.core.geo.Geo
import java.text.Normalizer

/** Slag av plats i offlinepaketets `platser.geojson`. */
enum class PlaceKind {
    /** Ort: stad, tätort, by eller gård (OSM `place`). */
    SETTLEMENT,
    /** Matställe: restaurang, snabbmat, kafé, pub (OSM `amenity`). */
    FOOD,
    /** Livsmedelsbutik, kiosk, bageri (OSM `shop`). */
    SHOP,
    /** Bensinstation. */
    FUEL,
    TOILET,
    /** Rastplats eller vindskydd. */
    REST_AREA,
    /** Det samhället är byggt för att inte se: pumpstationer, reningsverk, ställverk. */
    INFRASTRUCTURE,
    OTHER,
}

/** En plats ur offlinepaketet. Sanningsdata med källa (OSM-id). */
data class Place(
    /** OSM-id, t.ex. "node/123456". */
    val id: String,
    val name: String,
    val kind: PlaceKind,
    /** OSM-värdet, t.ex. "town", "restaurant", "supermarket". */
    val subKind: String,
    val point: GeoPoint,
    /** OSM-taggen `opening_hours` ordagrant, eller null om den saknas. */
    val openingHours: String?,
)

/**
 * Platserna i offlinepaketet. Läser en GeoJSON FeatureCollection med punkter (byggd i verkstaden
 * av `tools/verkstad/08-platser.py`). Sökningen är deterministisk och gör ingen I/O efter inläsningen.
 */
class PlaceIndex(val places: List<Place>) {

    private val folded: List<Pair<String, Place>> = places.map { fold(it.name) to it }

    /**
     * Namnsökning utan hänsyn till versaler och diakriter ("alvdalen" hittar "Älvdalen").
     * Ordning: exakt träff, börjar med, innehåller; orter före annat; därefter närmast [near].
     */
    fun search(query: String, near: GeoPoint? = null, limit: Int = 20, kinds: Set<PlaceKind>? = null): List<Place> {
        val q = fold(query)
        if (q.isEmpty()) return emptyList()
        return folded.asSequence()
            .filter { (name, place) -> name.contains(q) && (kinds == null || place.kind in kinds) }
            .sortedWith(
                compareBy<Pair<String, Place>>(
                    { (name, _) -> if (name == q) 0 else if (name.startsWith(q)) 1 else 2 },
                    { (_, place) -> settlementRank(place) },
                    { (_, place) -> near?.let { Geo.distanceMeters(it, place.point) } ?: 0.0 },
                    { (name, _) -> name },
                    { (_, place) -> place.id },
                ),
            )
            .map { it.second }
            .take(limit)
            .toList()
    }

    /** De närmaste platserna av givna slag, närmast först, inom [maxMeters] fågelvägen. */
    fun nearest(from: GeoPoint, kinds: Set<PlaceKind>, maxMeters: Double, limit: Int = 50): List<Pair<Place, Double>> =
        places.asSequence()
            .filter { it.kind in kinds }
            .map { it to Geo.distanceMeters(from, it.point) }
            .filter { it.second <= maxMeters }
            .sortedWith(compareBy({ it.second }, { it.first.id }))
            .take(limit)
            .toList()

    private fun settlementRank(p: Place): Int = if (p.kind != PlaceKind.SETTLEMENT) 9 else when (p.subKind) {
        "city" -> 0; "town" -> 1; "village" -> 2; "hamlet" -> 3; else -> 4
    }

    companion object {
        val EMPTY = PlaceIndex(emptyList())

        fun fold(s: String): String =
            Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ")

        /** Läser `platser.geojson`. Poster utan namn eller koordinater hoppas över. */
        fun parse(geoJson: String): PlaceIndex {
            val root = Json.parseToJsonElement(geoJson).jsonObject
            val features = root["features"]?.jsonArray ?: return EMPTY
            val out = ArrayList<Place>(features.size)
            for (f in features) {
                val o = f as? JsonObject ?: continue
                val props = o["properties"] as? JsonObject ?: continue
                val geometry = o["geometry"] as? JsonObject ?: continue
                if ((geometry["type"] as? JsonPrimitive)?.content != "Point") continue
                val c = geometry["coordinates"]?.jsonArray ?: continue
                if (c.size < 2) continue
                val name = (props["name"] as? JsonPrimitive)?.content?.trim().orEmpty()
                val id = (props["id"] as? JsonPrimitive)?.content ?: continue
                if (name.isEmpty()) continue
                val kind = runCatching { PlaceKind.valueOf((props["kind"] as? JsonPrimitive)?.content.orEmpty()) }.getOrDefault(PlaceKind.OTHER)
                out += Place(
                    id = id,
                    name = name,
                    kind = kind,
                    subKind = (props["subKind"] as? JsonPrimitive)?.content.orEmpty(),
                    point = GeoPoint(c[1].jsonPrimitive.double, c[0].jsonPrimitive.double),
                    openingHours = (props["opening_hours"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() },
                )
            }
            return PlaceIndex(out)
        }
    }
}
