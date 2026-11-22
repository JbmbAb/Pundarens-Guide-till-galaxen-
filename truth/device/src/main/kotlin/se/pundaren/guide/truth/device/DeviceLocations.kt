package se.pundaren.guide.truth.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import com.stadiamaps.ferrostar.core.location.NavigationLocationProviding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.core.geo.Geo
import uniffi.ferrostar.LocationBias
import uniffi.ferrostar.Route
import uniffi.ferrostar.advanceLocationSimulation
import uniffi.ferrostar.locationSimulationFromRoute

/**
 * Enhetens positioner, en ström för både navigeringen (Ferrostar) och sanningskärnan.
 *
 * Verklig position kommer bara från GNSS via Androids egen `LocationManager` och
 * `GPS_PROVIDER` (K-17, B-59). "Fused"-leverantören används inte: på Samsungs enheter drivs den
 * av Play-tjänsterna.
 *
 * En provtur ersätter den verkliga positionen med en simulerad färd längs rutten (T-16). Det är
 * uttryckligen ett provläge och syns i sanningsremsan.
 */
internal class DeviceLocations(
    context: Context,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit,
) : NavigationLocationProviding {

    private val appContext = context.applicationContext
    private val manager: LocationManager? = appContext.getSystemService(LocationManager::class.java)

    private val out = MutableSharedFlow<Location>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val locations: Flow<Location> = out.asSharedFlow()

    @Volatile private var latest: Location? = null

    private val _simulating = MutableStateFlow(false)
    val simulating: StateFlow<Boolean> = _simulating.asStateFlow()

    private var gnssListener: LocationListener? = null
    private var simulation: Job? = null

    override suspend fun lastLocation(): Location? = latest

    override fun locationUpdates(intervalMillis: Long): Flow<Location> = locations

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Finns GNSS-hårdvara och är leverantören påslagen? */
    fun gnssAvailable(): Boolean = runCatching { manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true }.getOrDefault(false)

    /** Börjar lyssna på GNSS. Gör ingenting utan behörighet eller om lyssnaren redan finns. */
    @SuppressLint("MissingPermission")
    fun startGnss(): Boolean {
        val lm = manager ?: return false
        if (gnssListener != null) return true
        if (!hasPermission()) return false
        if (lm.allProviders?.contains(LocationManager.GPS_PROVIDER) != true) {
            log("POSITION: enheten saknar GPS-leverantör")
            return false
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { if (!_simulating.value) publish(location) }
            @Deprecated("Krävs före API 30") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) { log("POSITION: $provider påslagen") }
            override fun onProviderDisabled(provider: String) { log("POSITION: $provider avstängd") }
        }
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
        gnssListener = listener
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { known ->
            // En gammal sparad position får inte se färsk ut: åldern följer med via elapsedRealtimeNanos.
            if (!_simulating.value) publish(known)
        }
        log("POSITION: lyssnar på GNSS (GPS_PROVIDER), påslagen=${gnssAvailable()}")
        return true
    }

    fun stopGnss() {
        gnssListener?.let { runCatching { manager?.removeUpdates(it) } }
        gnssListener = null
    }

    /**
     * Startar en provtur: simulerad position längs [route] i [speedMps], [timeFactor] gånger
     * snabbare än verklig tid. Vid stegen i [deviateAt] skickas tre positioner 250 m vid sidan av
     * rutten (G2: avvikelse, minst tre försök).
     */
    fun startSimulation(route: Route, speedMps: Double, timeFactor: Int, deviateAt: Set<Int> = emptySet()) {
        simulation?.cancel()
        val first = route.geometry.firstOrNull() ?: return
        _simulating.value = true
        // Ferrostar läser senaste positionen när navigeringen startar. Den ska vara ruttens början,
        // inte den förra turens slut (falsk avvikelse vid start, etapp2-protokollet §6).
        latest = location(GeoPoint(first.lat, first.lng), speedMps * timeFactor, null)
        simulation = scope.launch {
            var state = locationSimulationFromRoute(route, speedMps, LocationBias.None)
            var step = 0
            var attempt = 0
            var previous = GeoPoint(first.lat, first.lng)
            publish(latest!!)
            while (isActive) {
                delay(1_000L / timeFactor)
                val next = advanceLocationSimulation(state)
                if (next == state) break
                step++
                val a = state.currentLocation.coordinates
                val b = next.currentLocation.coordinates
                if (step in deviateAt) {
                    attempt++
                    val aside = Geo.destination(GeoPoint(a.lat, a.lng), Geo.bearingDegrees(GeoPoint(a.lat, a.lng), GeoPoint(b.lat, b.lng)) + 90.0, 250.0)
                    log("AVVIKELSE försök $attempt: tre positioner 250 m från rutten vid steg $step")
                    repeat(3) { r ->
                        publish(location(GeoPoint(aside.lat + r * 0.0001, aside.lon), speedMps * timeFactor, null))
                        delay(1_000L / timeFactor)
                    }
                }
                state = next
                val here = GeoPoint(b.lat, b.lng)
                publish(location(here, speedMps * timeFactor, if (here != previous) Geo.bearingDegrees(previous, here) else null))
                previous = here
            }
            log("SIMULERING slut efter $step steg")
        }
    }

    /** Avslutar provturen. Nästa position kommer från GNSS. Sant om en provtur pågick. */
    fun stopSimulation(): Boolean {
        simulation?.cancel()
        simulation = null
        if (!_simulating.value) return false
        _simulating.value = false
        latest = null
        return true
    }

    private fun publish(location: Location) {
        latest = location
        out.tryEmit(location)
    }

    private fun location(p: GeoPoint, speedMps: Double, bearing: Double?) = Location(SIMULATED_PROVIDER).apply {
        latitude = p.lat
        longitude = p.lon
        accuracy = 5f
        speed = speedMps.toFloat()
        if (bearing != null) this.bearing = bearing.toFloat()
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    fun close() {
        stopGnss()
        simulation?.cancel()
    }

    companion object {
        const val SIMULATED_PROVIDER = "provtur"
    }
}
