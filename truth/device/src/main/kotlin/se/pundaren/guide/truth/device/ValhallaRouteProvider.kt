package se.pundaren.guide.truth.device

import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.valhalla.valhalla.Valhalla
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.TravelMode
import uniffi.ferrostar.Route
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.createOsrmResponseParser

/**
 * Ferrostars ruttkälla för lokal ruttning (CustomRouteProvider, T-11): valhalla-mobile räknar
 * rutten mot tar-utdraget på enheten och svarar i OSRM-format, som läses med Ferrostars egen
 * OSRM-parser (T-12). Inget nätverk. Flyttad från etapp 2-prototypens ValhallaRuttkalla.
 */
internal class ValhallaRouteProvider(
    private val valhalla: Valhalla,
    private val log: (String) -> Unit,
) : CustomRouteProvider {

    /** Färdsättet för nästa ruttfråga. v1: till fots eller som passagerare (F-05). */
    @Volatile var travelMode: TravelMode = TravelMode.FOOT

    private val parser = createOsrmResponseParser(6u)

    override suspend fun getRoutes(userLocation: UserLocation, waypoints: List<Waypoint>): List<Route> =
        withContext(Dispatchers.IO) {
            val points = listOf(GeoPoint(userLocation.coordinates.lat, userLocation.coordinates.lng)) +
                waypoints.map { GeoPoint(it.coordinate.lat, it.coordinate.lng) }
            val costing = costing(travelMode)
            val started = System.nanoTime()
            val answer = valhalla.routeRaw(routeRequest(points, costing))
            val millis = (System.nanoTime() - started) / 1_000_000
            val routes = parser.parseResponse(answer.toByteArray())
            log(
                "RUTT $costing ${points.size} punkter: $millis ms i valhalla-mobile, ${routes.size} rutt(er), " +
                    "${routes.firstOrNull()?.steps?.size ?: 0} steg, %.2f km".format((routes.firstOrNull()?.distance ?: 0.0) / 1000),
            )
            routes
        }

    /**
     * Restider från en punkt till flera mål (Valhallas `sources_to_targets`, T-06). Sekunder per mål,
     * eller null där ingen väg finns. Underlag för osannolikhetsdriften och snacks (A-16).
     */
    suspend fun travelSeconds(from: GeoPoint, targets: List<GeoPoint>, mode: TravelMode): List<Double?> =
        withContext(Dispatchers.IO) {
            if (targets.isEmpty()) return@withContext emptyList()
            val answer = JSONObject(valhalla.matrixRaw(matrixRequest(from, targets, costing(mode))))
            val row = answer.getJSONArray("sources_to_targets").getJSONArray(0)
            List(targets.size) { i ->
                val cell = row.optJSONObject(i)
                if (cell == null || cell.isNull("time")) null else cell.getDouble("time")
            }
        }

    companion object {
        fun costing(mode: TravelMode): String = if (mode == TravelMode.FOOT) "pedestrian" else "auto"

        private fun location(p: GeoPoint) = JSONObject().put("lat", p.lat).put("lon", p.lon)

        /** Samma fråga som verkstadens ruttprov (tools/verkstad/04-ruttprov.py). */
        fun routeRequest(points: List<GeoPoint>, costing: String): String = JSONObject()
            .put("locations", JSONArray().apply { points.forEach { put(location(it)) } })
            .put("costing", costing)
            .put("format", "osrm")
            .put("language", "sv-SE")
            .put("voice_instructions", true)
            .put("banner_instructions", true)
            .put("units", "kilometers")
            .put("directions_options", JSONObject().put("language", "sv-SE").put("units", "kilometers"))
            .toString()

        fun matrixRequest(from: GeoPoint, targets: List<GeoPoint>, costing: String): String = JSONObject()
            .put("sources", JSONArray().put(location(from)))
            .put("targets", JSONArray().apply { targets.forEach { put(location(it)) } })
            .put("costing", costing)
            .put("units", "kilometers")
            .toString()
    }
}
