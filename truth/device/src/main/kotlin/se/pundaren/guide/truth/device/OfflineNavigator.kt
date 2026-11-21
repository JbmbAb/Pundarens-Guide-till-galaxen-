@file:OptIn(TruthInput::class)

package se.pundaren.guide.truth.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.os.SystemClock
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationState
import com.stadiamaps.ferrostar.core.SpokenInstructionObserver
import com.stadiamaps.ferrostar.core.http.HttpClientProvider
import com.stadiamaps.ferrostar.core.http.IResponse
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.pundaren.guide.truth.api.EmergencySource
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.InstructionVoice
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.api.TruthDisplayReader
import se.pundaren.guide.truth.api.TruthEventStream
import se.pundaren.guide.truth.api.TruthStateReader
import se.pundaren.guide.truth.core.TruthInput
import se.pundaren.guide.truth.core.kernel.KernelConfig
import se.pundaren.guide.truth.core.kernel.NavPhase
import se.pundaren.guide.truth.core.kernel.NavTick
import se.pundaren.guide.truth.core.kernel.PositionFix
import se.pundaren.guide.truth.core.kernel.Stamp
import se.pundaren.guide.truth.core.kernel.TruthKernel
import se.pundaren.guide.truth.core.paket.PaketInstallerare
import se.pundaren.guide.truth.core.platser.PlaceIndex
import se.pundaren.guide.truth.core.tal.InstructionAnnouncer
import se.pundaren.guide.truth.core.tal.SvenskTalTvatt
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.DeviationKind
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteDeviation
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.SpokenInstruction
import uniffi.ferrostar.TripState
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep
import java.io.File
import java.time.Instant

/** Navigatorns läge, för skärmen. */
sealed interface NavigatorStatus {
    data object Starting : NavigatorStatus
    data class NoPackage(val reason: String) : NavigatorStatus
    data class Ready(val packageVersion: String) : NavigatorStatus
    data class Failed(val reason: String) : NavigatorStatus
}

/** En uträknad rutt som ännu inte har startats. */
class RoutePlan internal constructor(
    val destination: GeoPoint,
    val destinationName: String,
    val origin: GeoPoint,
    val travelMode: TravelMode,
    val distanceMeters: Double,
    val seconds: Double,
    val line: List<GeoPoint>,
    val steps: Int,
    internal val route: Route,
)

/** Provtur: simulerad position längs rutten i stället för GNSS. */
data class TestDrive(
    /** Gånger snabbare än verklig tid. 1 = verklig fart. */
    val timeFactor: Int = 1,
    /** Simuleringssteg där positionen kastas 250 m åt sidan (avvikelseprov). */
    val deviateAt: Set<Int> = emptySet(),
)

/**
 * Hard Truth Core på enheten: offlinepaketet, valhalla-mobile, Ferrostar, GNSS och batteri.
 *
 * Utåt finns bara läsgränssnitten ([events], [state], [display]) och de kommandon `:app` behöver:
 * välj färdsätt, räkna rutt, starta och avbryta navigering och nödläget. Inget kommando tar emot
 * text eller något från tramslagret (03, G-5). Rösten nås bara genom den utgående porten
 * [InstructionVoice] (kanal B).
 */
class OfflineNavigator(
    context: Context,
    private val voice: InstructionVoice,
    private val log: (String) -> Unit,
) : AutoCloseable {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val kernel = TruthKernel(KernelConfig())
    private val announcer = InstructionAnnouncer(voice, log)
    private val locations = DeviceLocations(appContext, scope, log)

    val events: TruthEventStream get() = kernel
    val state: TruthStateReader get() = kernel
    val display: TruthDisplayReader get() = kernel

    private val _status = MutableStateFlow<NavigatorStatus>(NavigatorStatus.Starting)
    val status: StateFlow<NavigatorStatus> = _status.asStateFlow()

    private val _places = MutableStateFlow(PlaceIndex.EMPTY)
    /** Platserna i det aktiva offlinepaketet. Tom om paketet saknar `platser.geojson`. */
    val places: StateFlow<PlaceIndex> = _places.asStateFlow()

    private val _activePackage = MutableStateFlow<PaketInstallerare.Paket?>(null)
    val activePackage: StateFlow<PaketInstallerare.Paket?> = _activePackage.asStateFlow()

    /** Sant under en provtur (simulerad position). Visas i sanningsremsan. */
    val simulating: StateFlow<Boolean> get() = locations.simulating

    private var valhalla: Valhalla? = null
    private var routes: ValhallaRouteProvider? = null
    private var core: FerrostarCore? = null

    private var generation = 0
    private var geometryKey: String? = null
    private var wasCalculating = false
    private var navigating = false

    private val battery = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            kernel.battery(now(), level * 100 / scale, charging)
        }
    }

    private val speech = object : SpokenInstructionObserver {
        private val muted = MutableStateFlow(false)
        override val muteState: StateFlow<Boolean> = muted

        override fun onSpokenInstructionTrigger(spokenInstruction: SpokenInstruction) {
            val outcome = announcer.announce(spokenInstruction.text)
            val stamp = now()
            if (outcome is InstructionAnnouncer.Outcome.Spoken) {
                // Kärnan får veta direkt att ett besked spelas, innan rösten hunnit rapportera det.
                kernel.instructionSpeaking(stamp, true)
                scope.launch {
                    delay(400)
                    if (!voice.instructionSpeaking.value) kernel.instructionSpeaking(now(), false) // ingen röst svarade
                }
            }
            kernel.instructionTriggered(stamp)
        }

        override fun stopAndClearQueue() = announcer.stop()

        override fun setMuted(isMuted: Boolean) {
            muted.value = isMuted
            announcer.muted = isMuted
            if (isMuted) announcer.stop()
        }
    }

    init {
        appContext.registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        scope.launch { locations.locations.collect(::onLocation) }
        scope.launch { voice.instructionSpeaking.collect { kernel.instructionSpeaking(now(), it) } }
        scope.launch {
            while (isActive) {
                delay(1_000)
                kernel.clockTick(now())
            }
        }
    }

    // ------------------------------------------------------------------------------------
    // Uppstart
    // ------------------------------------------------------------------------------------

    /**
     * Installerar offlinepaketet om ett nytt finns (först `inkommande/` i appens externa katalog,
     * dit man kan lägga ett paket med `adb push`; sedan APK:ns tillgångar under `paket/`), och startar
     * ruttmotorn mot det aktiva paketet. Ett avvisat paket rör aldrig det som redan är aktivt.
     */
    suspend fun prepare() {
        _status.value = NavigatorStatus.Starting
        val installer = PaketInstallerare(File(appContext.filesDir, "offline"))
        val paket = withContext(Dispatchers.IO) {
            val incoming = File(appContext.getExternalFilesDir(null), "inkommande")
            // Appen skapar mappen själv. En mapp som skapats utifrån (adb shell mkdir) ägs av en annan
            // användare och går inte att läsa härifrån (sett på målenheten 2026-11-21).
            incoming.mkdirs()
            val source: PaketInstallerare.Kalla? = when {
                File(incoming, "manifest.json").isFile -> {
                    log("PAKET läses från $incoming")
                    PaketInstallerare.Kalla { File(incoming, it).inputStream() }
                }
                appContext.assets.list("paket")?.contains("manifest.json") == true -> {
                    log("PAKET läses från APK:ns tillgångar")
                    PaketInstallerare.Kalla { appContext.assets.open("paket/$it") }
                }
                else -> null
            }
            if (source == null) installer.aktivt() else when (
                val outcome = runCatching { installer.installera(source) }.getOrElse { PaketInstallerare.Utfall.Avvisat(it.toString()) }
            ) {
                is PaketInstallerare.Utfall.Installerat -> {
                    log(
                        "PAKET ${outcome.paket.version} ${if (outcome.redanAktivt) "redan aktivt" else "installerat atomärt"} på ${outcome.millisekunder} ms: " +
                            outcome.paket.manifest.filer.joinToString { "${it.namn} ${it.storlek / 1024} kB" },
                    )
                    // Paketet ligger nu i appens interna lagring; kopian i inkommande/ behövs inte längre.
                    if (File(incoming, "manifest.json").isFile && incoming.deleteRecursively()) log("PAKET inkommande/ tömd")
                    outcome.paket
                }
                is PaketInstallerare.Utfall.Avvisat -> {
                    log("PAKET AVVISAT: ${outcome.skal}")
                    installer.aktivt()
                }
            }
        }
        if (paket == null) {
            log("PAKET saknas: inget aktivt paket, inget i APK:n och inget läsbart manifest i ${File(appContext.getExternalFilesDir(null), "inkommande")}")
            _status.value = NavigatorStatus.NoPackage("Inget offlinepaket är installerat.")
            return
        }
        _activePackage.value = paket
        center(paket)?.let(kernel::anchor)

        try {
            val (engine, index) = withContext(Dispatchers.IO) {
                val started = System.nanoTime()
                val config = ValhallaConfigFactory.usingTileExtract(paket.fil("valhalla_tiles.tar").absolutePath)
                val v = Valhalla(appContext, config)
                log("VALHALLA startad på ${(System.nanoTime() - started) / 1_000_000} ms")
                val places = if (paket.harFil(PLACES_FILE)) {
                    runCatching { PlaceIndex.parse(paket.fil(PLACES_FILE).readText()) }
                        .onFailure { log("PLATSER kunde inte läsas: $it") }.getOrDefault(PlaceIndex.EMPTY)
                } else PlaceIndex.EMPTY
                log("PLATSER ${places.places.size} st ur ${if (paket.harFil(PLACES_FILE)) PLACES_FILE else "inget (paketet saknar platsfil)"}")
                v to places
            }
            valhalla = engine
            _places.value = index
            val provider = ValhallaRouteProvider(engine, log)
            routes = provider
            val navigation = FerrostarCore(provider, NoNetwork(log), locations, navigationConfig())
            navigation.spokenInstructionObserver = speech
            core = navigation
            scope.launch { navigation.state.collect(::onNavigationState) }
            _status.value = NavigatorStatus.Ready(paket.version)
        } catch (e: Throwable) {
            log("FEL vid start av ruttmotorn: $e")
            _status.value = NavigatorStatus.Failed("Ruttmotorn kunde inte startas: ${e.message}")
        }
    }

    /** Paketets mittpunkt ur manifestets `omrade.bbox_wgs84` (väst, syd, öst, nord). */
    private fun center(paket: PaketInstallerare.Paket): GeoPoint? =
        paket.manifest.omradeBbox?.let { bbox -> GeoPoint((bbox[1] + bbox[3]) / 2, (bbox[0] + bbox[2]) / 2) }

    private fun navigationConfig() = NavigationControllerConfig(
        WaypointAdvanceMode.WaypointWithinRange(100.0),
        stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
        stepAdvanceDistanceToEndOfStep(10u, 32u),
        RouteDeviationTracking.StaticThreshold(15u, 50.0),
        CourseFiltering.SNAP_TO_ROUTE,
    )

    // ------------------------------------------------------------------------------------
    // Kommandon för :app
    // ------------------------------------------------------------------------------------

    /** Börjar lyssna på GNSS om behörigheten finns. Anropas efter att användaren har svarat på frågan. */
    fun startLocation(): Boolean = locations.startGnss()

    fun hasLocationPermission(): Boolean = locations.hasPermission()

    fun setTravelMode(mode: TravelMode) {
        require(mode == TravelMode.FOOT || mode == TravelMode.PASSENGER) { "v1 omfattar till fots och passagerare (F-05)" }
        kernel.travelMode(mode)
    }

    /** Tystar eller släpper på beskedsrösten. Sanningsremsan påverkas inte. */
    fun setInstructionsMuted(muted: Boolean) = speech.setMuted(muted)

    /**
     * Räknar en rutt till [destination] utan att starta den. [from] anges vid provtur; annars
     * används den senaste verkliga positionen.
     */
    suspend fun plan(destination: GeoPoint, destinationName: String, from: GeoPoint? = null): Result<RoutePlan> {
        val navigation = core ?: return Result.failure(IllegalStateException("Ruttmotorn är inte redo."))
        val provider = routes ?: return Result.failure(IllegalStateException("Ruttmotorn är inte redo."))
        val origin = from ?: display.map.value.position
            ?: return Result.failure(IllegalStateException("Ingen position än. Vänta på GNSS eller välj provtur."))
        val mode = state.navigation.value.travelMode
        provider.travelMode = mode
        return runCatching {
            val start = UserLocation(GeographicCoordinate(origin.lat, origin.lon), 5.0, null, Instant.now(), null)
            val found = navigation.getRoutes(start, listOf(Waypoint(GeographicCoordinate(destination.lat, destination.lon), WaypointKind.BREAK, null)))
            val route = found.firstOrNull() ?: error("Ingen rutt hittades.")
            RoutePlan(
                destination = destination, destinationName = destinationName, origin = origin, travelMode = mode,
                distanceMeters = route.distance, seconds = route.steps.sumOf { it.duration },
                line = route.geometry.map { GeoPoint(it.lat, it.lng) }, steps = route.steps.size, route = route,
            )
        }.onFailure { log("FEL i ruttning: $it") }
    }

    /** Startar navigeringen längs [plan]. Med [testDrive] simuleras positionen längs rutten. */
    fun start(plan: RoutePlan, testDrive: TestDrive? = null) {
        val navigation = core ?: return
        stop()
        routes?.travelMode = plan.travelMode
        kernel.travelMode(plan.travelMode)
        announcer.newTrip()
        generation = 1
        geometryKey = key(plan.route.geometry)
        wasCalculating = false
        navigating = true
        if (testDrive != null) {
            val speed = if (plan.travelMode == TravelMode.FOOT) FOOT_TEST_SPEED_MPS else VEHICLE_TEST_SPEED_MPS
            locations.startSimulation(plan.route, speed, testDrive.timeFactor.coerceIn(1, 20), testDrive.deviateAt)
        }
        kernel.routeStarted(now(), plan.destinationName, plan.destination, plan.travelMode, plan.distanceMeters, plan.seconds, plan.line)
        navigation.startNavigation(plan.route)
        log(
            "NAVIGERING startad (${plan.travelMode}) till ${plan.destinationName}: %.2f km, ${plan.steps} steg, %s".format(
                plan.distanceMeters / 1000, if (testDrive != null) "provtur ×${testDrive.timeFactor}, avvikelseförsök vid ${testDrive.deviateAt}" else "verklig position",
            ),
        )
    }

    /** Avbryter navigeringen och en eventuell provtur. */
    fun stop() {
        if (!navigating && !locations.simulating.value) return
        core?.stopNavigation()
        announcer.stop()
        locations.stopSimulation()
        navigating = false
        geometryKey = null
        kernel.routeStopped(now())
        log("STOPP")
    }

    /** Nödläget (Don't Panic, långt tryck). Undertrycker all satir via SafetyGate. */
    fun setEmergency(active: Boolean) = kernel.emergency(now(), active, EmergencySource.USER)

    /** Restider från [from] till flera mål, sekunder per mål eller null där ingen väg finns. */
    suspend fun travelSeconds(from: GeoPoint, targets: List<GeoPoint>, mode: TravelMode): List<Double?> =
        routes?.travelSeconds(from, targets, mode) ?: List(targets.size) { null }

    // ------------------------------------------------------------------------------------
    // Från enheten till kärnan
    // ------------------------------------------------------------------------------------

    private fun onLocation(location: Location) {
        val fixMono = location.elapsedRealtimeNanos / 1_000_000
        kernel.positionFix(
            now(),
            PositionFix(
                point = GeoPoint(location.latitude, location.longitude),
                accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else 999.0,
                speedMps = if (location.hasSpeed()) location.speed.toDouble() else null,
                bearingDegrees = if (location.hasBearing()) location.bearing.toDouble() else null,
                altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            ),
            fixMonotonicMillis = if (fixMono > 0) fixMono else SystemClock.elapsedRealtime(),
        )
    }

    private fun onNavigationState(navState: NavigationState) {
        if (!navigating) return
        val stamp = now()
        if (navState.isCalculatingNewRoute && !wasCalculating) {
            announcer.rerouteStarted()
            log("OMRÄKNING pågår")
        }
        wasCalculating = navState.isCalculatingNewRoute
        when (val trip = navState.tripState) {
            is TripState.Navigating -> {
                val key = key(navState.routeGeometry)
                if (key != null && key != geometryKey) {
                    generation++
                    geometryKey = key
                    kernel.routeReplaced(stamp, navState.routeGeometry.map { GeoPoint(it.lat, it.lng) })
                    log("NY RUTT (generation $generation): ${trip.remainingSteps.size} steg, %.2f km kvar".format(trip.progress.distanceRemaining / 1000))
                }
                kernel.navTick(tick(stamp, trip, navState.isCalculatingNewRoute))
            }
            is TripState.Complete -> {
                kernel.navTick(
                    NavTick(stamp = stamp, phase = NavPhase.ARRIVED, routeGeneration = generation, stepsRemaining = 0, distanceTraveledMeters = trip.summary.distanceTraveled),
                )
                log("FRAMME (Complete)")
                navigating = false
                locations.stopSimulation()
            }
            else -> {}
        }
    }

    private fun tick(stamp: Stamp, trip: TripState.Navigating, recalculating: Boolean): NavTick {
        val current = trip.remainingSteps.firstOrNull()
        val upcoming = trip.remainingSteps.getOrNull(1)
        val visual = trip.visualInstruction?.primaryContent
        val toManeuver = trip.progress.distanceToNextManeuver
        // Nästa talinstruktion i det här steget är den med störst utlösningsavstånd som ännu inte passerats.
        // Finns ingen kvar utlöses nästa först när steget byts, alltså vid manövern.
        val nextTrigger = current?.spokenInstructions.orEmpty().map { it.triggerDistanceBeforeManeuver }.filter { it < toManeuver - 1.0 }.maxOrNull()
        val deviation = (trip.deviation as? RouteDeviation.Deviation)?.kind?.let { kind ->
            when (kind) {
                is DeviationKind.CompletelyOffRoute -> kind.deviationFromRouteLine
                is DeviationKind.OffStepOnRoute -> kind.deviationFromStepLine
                else -> 0.0
            }
        }
        return NavTick(
            stamp = stamp,
            phase = NavPhase.NAVIGATING,
            routeGeneration = generation,
            stepsRemaining = trip.remainingSteps.size,
            // Samma tvätt som för talbeskedet, så att remsan och rösten säger samma sak.
            instructionText = (upcoming?.instruction?.takeIf { it.isNotBlank() } ?: visual?.text)?.let(SvenskTalTvatt::tvatta),
            maneuverType = visual?.maneuverType?.name?.lowercase()?.replace('_', ' '),
            maneuverModifier = visual?.maneuverModifier?.name?.lowercase()?.replace('_', ' '),
            streetName = upcoming?.roadName?.takeIf { it.isNotBlank() },
            distanceToManeuverMeters = toManeuver,
            secondsToManeuverByRoute = trip.progress.durationToNextManeuver.takeIf { it.isFinite() && it >= 0.0 },
            metersToNextSpokenTrigger = if (nextTrigger != null) toManeuver - nextTrigger else toManeuver,
            distanceRemainingMeters = trip.progress.distanceRemaining,
            secondsRemaining = trip.progress.durationRemaining,
            distanceTraveledMeters = trip.summary.distanceTraveled,
            deviationMeters = deviation,
            recalculating = recalculating,
            snappedPosition = GeoPoint(trip.snappedUserLocation.coordinates.lat, trip.snappedUserLocation.coordinates.lng),
            bearingDegrees = trip.snappedUserLocation.courseOverGround?.degrees?.toDouble(),
        )
    }

    private fun key(geometry: List<GeographicCoordinate>): String? {
        if (geometry.isEmpty()) return null
        val first = geometry.first(); val mid = geometry[geometry.size / 2]; val last = geometry.last()
        return "${geometry.size}:${first.lat},${first.lng}:${mid.lat},${mid.lng}:${last.lat},${last.lng}"
    }

    private fun now() = Stamp(SystemClock.elapsedRealtime(), Instant.now())

    override fun close() {
        runCatching { appContext.unregisterReceiver(battery) }
        core?.stopNavigation()
        locations.close()
        scope.cancel()
        valhalla?.close()
    }

    /**
     * Ferrostar kräver en HTTP-klient i konstruktorn men använder den inte med en egen ruttkälla.
     * Den här vägrar alltid, så ett eventuellt anrop syns i loggen i stället för att gå ut på nätet.
     */
    private class NoNetwork(private val log: (String) -> Unit) : HttpClientProvider {
        override suspend fun call(request: uniffi.ferrostar.RouteRequest): IResponse {
            log("FEL: Ferrostar försökte göra ett HTTP-anrop; stoppat")
            throw IllegalStateException("Appen har inget nätverk för ruttning")
        }
    }

    companion object {
        const val PLACES_FILE = "platser.geojson"

        /**
         * Provturens fart: 45 km/h som passagerare, 5 km/h till fots. Avsiktligt under 50 km/h, där
         * SafetyGates stressregel (R-10, kandidatvärde) annars tystar i stort sett alla svansar.
         */
        const val VEHICLE_TEST_SPEED_MPS = 12.5
        const val FOOT_TEST_SPEED_MPS = 1.4
    }
}
