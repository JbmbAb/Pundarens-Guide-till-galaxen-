package se.pundaren.guide.prototyp.sanning.rutt

import android.location.Location
import com.stadiamaps.ferrostar.core.location.NavigationLocationProviding
import kotlin.math.cos
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import uniffi.ferrostar.LocationBias
import uniffi.ferrostar.Route
import uniffi.ferrostar.advanceLocationSimulation
import uniffi.ferrostar.locationSimulationFromRoute

/**
 * Simulerad position längs rutten (som Ferrostars SimulatedLocationProvider, T-16), med valfria
 * avvikelseförsök: vid givna simuleringssteg skickas tre positioner 250 m vinkelrätt ut från
 * rutten, sedan fortsätter simuleringen. Används för G2-kriteriet "avvikelse, minst 3 försök".
 */
class ProvPositionskalla(
    private val logg: (String) -> Unit,
) : NavigationLocationProviding {

    data class Korning(val route: Route, val takt: Int, val avvikelseVid: Set<Int>)

    private val korning = MutableStateFlow<Korning?>(null)
    @Volatile private var senaste: Location? = null

    fun starta(route: Route, takt: Int = 4, avvikelseVid: Set<Int> = emptySet()) {
        korning.value = Korning(route, takt, avvikelseVid)
    }

    fun stoppa() { korning.value = null }

    override suspend fun lastLocation(): Location? = senaste

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun locationUpdates(intervalMillis: Long): Flow<Location> = korning.flatMapLatest { k ->
        if (k == null) return@flatMapLatest emptyFlow()
        flow {
            var s = locationSimulationFromRoute(k.route, 10.0, LocationBias.None)
            var i = 0
            var forsok = 0
            while (true) {
                delay(1000L / k.takt)
                val n = advanceLocationSimulation(s)
                if (n == s) break
                i++
                if (i in k.avvikelseVid) {
                    forsok++
                    val a = s.currentLocation.coordinates; val b = n.currentLocation.coordinates
                    val (lat, lon) = sidled(a.lat, a.lng, b.lat, b.lng, 250.0)
                    logg("AVVIKELSE försök $forsok: tre positioner 250 m från rutten vid steg $i")
                    repeat(3) { r ->
                        emit(plats(lat + r * 0.0001, lon).also { senaste = it })
                        delay(1000L / k.takt)
                    }
                }
                s = n
                emit(plats(s.currentLocation.coordinates.lat, s.currentLocation.coordinates.lng).also { senaste = it })
            }
            logg("SIMULERING slut efter $i steg")
        }
    }

    private fun plats(lat: Double, lon: Double) = Location("prov").apply {
        latitude = lat; longitude = lon; accuracy = 5f; time = System.currentTimeMillis()
    }

    private fun sidled(lat: Double, lon: Double, lat2: Double, lon2: Double, meter: Double): Pair<Double, Double> {
        val k = 111_320.0; val c = cos(Math.toRadians(lat))
        val dx = (lon2 - lon) * k * c; val dy = (lat2 - lat) * k
        val l = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-9)
        return (lat + (dx / l * meter) / k) to (lon + (-dy / l * meter) / (k * c))
    }
}
