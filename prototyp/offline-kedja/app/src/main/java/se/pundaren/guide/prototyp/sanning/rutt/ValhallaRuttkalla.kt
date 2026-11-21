package se.pundaren.guide.prototyp.sanning.rutt

import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.valhalla.valhalla.Valhalla
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uniffi.ferrostar.Route
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.createOsrmResponseParser

/**
 * Ferrostars ruttkälla för lokal ruttning (CustomRouteProvider, T-11): valhalla-mobile räknar
 * rutten mot tar-utdraget på enheten och svarar i OSRM-format, som läses med Ferrostars egen
 * OSRM-parser (T-12). Inget nätverk. Sanningslagret: Illogical Engine har ingen väg hit.
 */
class ValhallaRuttkalla(
    private val valhalla: Valhalla,
    @Volatile var profil: Profil,
    private val logg: (String, String?) -> Unit,
) : CustomRouteProvider {

    enum class Profil(val costing: String) { PASSAGERARE("auto"), TILL_FOTS("pedestrian") }

    private val parser = createOsrmResponseParser(6u)

    override suspend fun getRoutes(userLocation: UserLocation, waypoints: List<Waypoint>): List<Route> =
        withContext(Dispatchers.IO) {
            val punkter = listOf(userLocation.coordinates.lat to userLocation.coordinates.lng) +
                waypoints.map { it.coordinate.lat to it.coordinate.lng }
            val fraga = ruttFraga(punkter, profil.costing)
            val t0 = System.nanoTime()
            val svar = valhalla.routeRaw(fraga)
            val ms = (System.nanoTime() - t0) / 1_000_000
            val rutter = parser.parseResponse(svar.toByteArray())
            logg("RUTT ${profil.name} ${punkter.size} punkter: ${ms} ms i valhalla-mobile, " +
                "${rutter.size} rutt(er), ${rutter.firstOrNull()?.steps?.size ?: 0} steg, " +
                "%.2f km".format((rutter.firstOrNull()?.distance ?: 0.0) / 1000), svar)
            rutter
        }

    companion object {
        /** Samma fråga som verkstadens ruttprov (tools/verkstad/04-ruttprov.py). */
        fun ruttFraga(punkter: List<Pair<Double, Double>>, costing: String): String {
            val loc = JSONArray()
            punkter.forEach { (lat, lon) -> loc.put(JSONObject().put("lat", lat).put("lon", lon)) }
            return JSONObject()
                .put("locations", loc)
                .put("costing", costing)
                .put("format", "osrm")
                .put("language", "sv-SE")
                .put("voice_instructions", true)
                .put("banner_instructions", true)
                .put("units", "kilometers")
                .put("directions_options", JSONObject().put("language", "sv-SE").put("units", "kilometers"))
                .toString()
        }
    }
}
