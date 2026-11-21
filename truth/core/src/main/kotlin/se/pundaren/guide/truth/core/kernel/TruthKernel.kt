package se.pundaren.guide.truth.core.kernel

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import se.pundaren.guide.truth.api.Arrived
import se.pundaren.guide.truth.api.AudioOutput
import se.pundaren.guide.truth.api.BatteryLow
import se.pundaren.guide.truth.api.DayPhase
import se.pundaren.guide.truth.api.EmergencyActivated
import se.pundaren.guide.truth.api.EmergencyCleared
import se.pundaren.guide.truth.api.EmergencySource
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.GuidancePhase
import se.pundaren.guide.truth.api.GuidanceSnapshot
import se.pundaren.guide.truth.api.ManeuverCompleted
import se.pundaren.guide.truth.api.ManeuverUpcoming
import se.pundaren.guide.truth.api.MapSnapshot
import se.pundaren.guide.truth.api.NavigationSnapshot
import se.pundaren.guide.truth.api.OffRoute
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.api.PositionQualityChanged
import se.pundaren.guide.truth.api.PositionSnapshot
import se.pundaren.guide.truth.api.Rerouted
import se.pundaren.guide.truth.api.RouteStarted
import se.pundaren.guide.truth.api.SafetySnapshot
import se.pundaren.guide.truth.api.Stationary
import se.pundaren.guide.truth.api.TimeOfDayChanged
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.api.TruthDisplayReader
import se.pundaren.guide.truth.api.TruthEvent
import se.pundaren.guide.truth.api.TruthEventStream
import se.pundaren.guide.truth.api.TruthStateReader
import se.pundaren.guide.truth.core.TruthInput
import se.pundaren.guide.truth.core.geo.Geo
import se.pundaren.guide.truth.core.geo.Sun
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Hard Truth Cores tillstånd. Tar emot rena värden från `:truth:device` och gör dem till
 * händelserna i 05 och till ögonblicksbilderna som SafetyGate och skärmen läser.
 *
 * Dioden: utåt finns bara läsgränssnitten ([TruthEventStream], [TruthStateReader],
 * [TruthDisplayReader]). All inmatning är märkt [TruthInput], som bara `:truth:device` får använda.
 * Ingen metod tar emot text eller typer från tramslagret (03, G-5).
 *
 * Kärnan läser ingen klocka och gör ingen I/O. Samma indata ger samma händelser.
 */
class TruthKernel(private val config: KernelConfig = KernelConfig()) :
    TruthEventStream, TruthStateReader, TruthDisplayReader {

    private val _events = MutableSharedFlow<TruthEvent>(
        replay = 0, extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<TruthEvent> = _events.asSharedFlow()

    private val _navigation = MutableStateFlow(
        NavigationSnapshot(
            activeRoute = false, travelMode = TravelMode.FOOT, speedKmh = 0.0, secondsToNextManeuver = null,
            millisToNextSpokenInstruction = null, instructionSpeaking = false, rerouting = false,
        ),
    )
    override val navigation: StateFlow<NavigationSnapshot> = _navigation.asStateFlow()

    private val _position = MutableStateFlow(PositionSnapshot(PositionQuality.LOST, null, null, null))
    override val position: StateFlow<PositionSnapshot> = _position.asStateFlow()

    private val _safety = MutableStateFlow(
        SafetySnapshot(
            emergencyActive = false, emergencyClearedAtMonotonicMillis = null, batteryPercent = 100,
            convoyActive = false, convoyContactLost = false, audioOutput = AudioOutput.UNKNOWN,
        ),
    )
    override val safety: StateFlow<SafetySnapshot> = _safety.asStateFlow()

    private val _guidance = MutableStateFlow(GuidanceSnapshot.idle())
    override val guidance: StateFlow<GuidanceSnapshot> = _guidance.asStateFlow()

    private val _map = MutableStateFlow(MapSnapshot.EMPTY)
    override val map: StateFlow<MapSnapshot> = _map.asStateFlow()

    // --- Rutt ---
    private var mode = TravelMode.FOOT
    private var active = false
    private var arrived = false
    private var expeditionId: String? = null
    private var destinationName: String? = null
    private var destination: GeoPoint? = null
    private var routeLine: List<GeoPoint> = emptyList()
    private var routeStartedAtMono = 0L
    private var routeStartDistanceMeters = 0
    private var lastTick: NavTick? = null

    // --- Avvikelse och omräkning (06, R-05) ---
    private var offRouteSinceMono: Long? = null
    private var awaitingInstructionAfterReroute = false
    private var rerouteAtMono = 0L
    private var instructionAfterRerouteAtMono: Long? = null
    private var recentReroutes: List<Long> = emptyList()
    private var instructionSpeaking = false

    // --- Position ---
    private var lastFix: PositionFix? = null
    private var lastFixMono: Long? = null
    private var speedMps = 0.0
    private var speedKnown = false
    private var quality = PositionQuality.LOST
    private var uncertaintySinceMono: Long? = null
    private var stationarySinceMono: Long? = null
    private var stationaryEmitted = 0

    // --- Säkerhet ---
    private var batteryPercent = 100
    private var batteryCharging = false
    private var batteryLowReported = emptySet<Int>()
    private var emergencyActive = false
    private var emergencyClearedAtMono: Long? = null
    private var audioOutput = AudioOutput.UNKNOWN
    private var dayPhase: DayPhase? = null
    private var anchorPoint: GeoPoint? = config.fallbackPosition

    private var sequence = 0L

    // ------------------------------------------------------------------------------------
    // Inmatning. Bara :truth:device.
    // ------------------------------------------------------------------------------------

    /** Färdsättet som gäller för nästa rutt och för SafetyGates regler. */
    @TruthInput @Synchronized
    fun travelMode(newMode: TravelMode) {
        if (active) return // färdsättet byts inte under en rutt
        mode = newMode
        publish(null)
    }

    /** En rutt har räknats ut och navigeringen börjar. */
    @TruthInput @Synchronized
    fun routeStarted(
        stamp: Stamp,
        destinationName: String,
        destination: GeoPoint,
        travelMode: TravelMode,
        distanceMeters: Double,
        etaSeconds: Double,
        line: List<GeoPoint>,
    ) {
        mode = travelMode
        active = true
        arrived = false
        expeditionId = "X-%013d".format(Locale.ROOT, stamp.wallClockUtc.toEpochMilli())
        this.destinationName = destinationName
        this.destination = destination
        routeLine = line
        routeStartedAtMono = stamp.monotonicMillis
        routeStartDistanceMeters = distanceMeters.roundToInt()
        lastTick = null
        offRouteSinceMono = null
        awaitingInstructionAfterReroute = false
        instructionAfterRerouteAtMono = null
        recentReroutes = emptyList()
        emit(
            RouteStarted(
                id(stamp), stamp.monotonicMillis, stamp.wallClockUtc, expeditionId, destinationName,
                distanceMeters.roundToInt(), etaSeconds.roundToInt(), travelMode,
            ),
        )
        publish(stamp)
    }

    /** Ruttens linje efter en omräkning. Händelsen Rerouted skickas när nästa [navTick] har den nya generationen. */
    @TruthInput @Synchronized
    fun routeReplaced(stamp: Stamp, line: List<GeoPoint>) {
        if (!active) return
        routeLine = line
        publish(stamp)
    }

    /** Navigeringen avbröts av användaren. Ingen händelse finns för det i 05; tillståndet nollställs. */
    @TruthInput @Synchronized
    fun routeStopped(stamp: Stamp) {
        active = false
        arrived = false
        lastTick = null
        routeLine = emptyList()
        destination = null
        destinationName = null
        offRouteSinceMono = null
        awaitingInstructionAfterReroute = false
        publish(stamp)
    }

    @TruthInput @Synchronized
    fun navTick(tick: NavTick) {
        val stamp = tick.stamp
        val prev = lastTick
        if (!active) {
            lastTick = tick
            return
        }
        if (prev != null && tick.routeGeneration > prev.routeGeneration) {
            emit(
                Rerouted(
                    id(stamp), stamp.monotonicMillis, stamp.wallClockUtc, expeditionId,
                    (tick.distanceRemainingMeters ?: 0.0).roundToInt(), (tick.secondsRemaining ?: 0.0).roundToInt(), "avvikelse",
                ),
            )
            recentReroutes = (recentReroutes + stamp.monotonicMillis).takeLast(config.recentReroutesKept)
            offRouteSinceMono = null
            awaitingInstructionAfterReroute = true
            rerouteAtMono = stamp.monotonicMillis
            instructionAfterRerouteAtMono = null
        } else if (prev != null && prev.phase == NavPhase.NAVIGATING && tick.routeGeneration == prev.routeGeneration &&
            tick.stepsRemaining < prev.stepsRemaining
        ) {
            emit(ManeuverCompleted(id(stamp), stamp.monotonicMillis, stamp.wallClockUtc, expeditionId, maneuverId(prev)))
        }

        val deviation = tick.deviationMeters
        if (tick.phase == NavPhase.NAVIGATING && deviation != null && offRouteSinceMono == null && !awaitingInstructionAfterReroute) {
            offRouteSinceMono = stamp.monotonicMillis
            emit(OffRoute(id(stamp), stamp.monotonicMillis, stamp.wallClockUtc, expeditionId, deviation.roundToInt(), 0))
        } else if (deviation == null && !tick.recalculating && offRouteSinceMono != null) {
            offRouteSinceMono = null // tillbaka på rutten utan omräkning
        }

        lastTick = tick
        if (tick.phase == NavPhase.ARRIVED && prev?.phase != NavPhase.ARRIVED) {
            emit(
                Arrived(
                    id(stamp), stamp.monotonicMillis, stamp.wallClockUtc, expeditionId, destinationName.orEmpty(),
                    ((stamp.monotonicMillis - routeStartedAtMono) / 1000).toInt(),
                    (tick.distanceTraveledMeters ?: routeStartDistanceMeters.toDouble()).roundToInt(),
                ),
            )
            active = false
            arrived = true
            offRouteSinceMono = null
            awaitingInstructionAfterReroute = false
        }
        settleReroute(stamp)
        publish(stamp)
    }

    /** Ruttmotorn utlöste en talinstruktion (kanal B). Skickas oavsett om någon röst finns. */
    @TruthInput @Synchronized
    fun instructionTriggered(stamp: Stamp) {
        val tick = lastTick
        if (!active || tick == null) return
        emit(
            ManeuverUpcoming(
                id(stamp), stamp.monotonicMillis, stamp.wallClockUtc, expeditionId, maneuverId(tick),
                tick.maneuverType ?: "unknown", tick.maneuverModifier,
                (tick.distanceToManeuverMeters ?: 0.0).roundToInt(), secondsToManeuver(tick) ?: 0.0, tick.streetName,
            ),
        )
        if (awaitingInstructionAfterReroute && instructionAfterRerouteAtMono == null) instructionAfterRerouteAtMono = stamp.monotonicMillis
        publish(stamp)
    }

    /** Rösten rapporterar om ett besked spelas. */
    @TruthInput @Synchronized
    fun instructionSpeaking(stamp: Stamp, speaking: Boolean) {
        instructionSpeaking = speaking
        settleReroute(stamp)
        publish(stamp)
    }

    /**
     * En ny position. [fixMonotonicMillis] är när positionen togs (Location.elapsedRealtimeNanos),
     * så att en gammal sparad position inte ser färsk ut bara för att den levereras nu.
     */
    @TruthInput @Synchronized
    fun positionFix(stamp: Stamp, fix: PositionFix, fixMonotonicMillis: Long = stamp.monotonicMillis) {
        val previous = lastFix
        val previousMono = lastFixMono
        if (previousMono != null && fixMonotonicMillis < previousMono) return // äldre än den vi redan har
        val derived = if (previous != null && previousMono != null) {
            val dt = (fixMonotonicMillis - previousMono) / 1000.0
            if (dt in 0.2..10.0) Geo.distanceMeters(previous.point, fix.point) / dt else null
        } else null
        val raw = fix.speedMps ?: derived
        if (raw != null) {
            speedMps = if (speedKnown) 0.6 * speedMps + 0.4 * raw else raw
            speedKnown = true
        }
        lastFix = fix
        lastFixMono = fixMonotonicMillis
        evaluateQuality(stamp)
        publish(stamp)
    }

    /** Plats för solens läge innan första positionen finns (offlinepaketets mittpunkt). */
    @TruthInput @Synchronized
    fun anchor(point: GeoPoint) {
        anchorPoint = point
    }

    /** Anropas ungefär en gång per sekund. Driver allt som beror på att tiden går. */
    @TruthInput @Synchronized
    fun clockTick(stamp: Stamp) {
        evaluateQuality(stamp)
        evaluateStationary(stamp)
        evaluateDayPhase(stamp)
        settleReroute(stamp)
        publish(stamp)
    }

    @TruthInput @Synchronized
    fun battery(stamp: Stamp, percent: Int, charging: Boolean) {
        val before = batteryPercent
        batteryPercent = percent.coerceIn(0, 100)
        batteryCharging = charging
        if (charging) {
            batteryLowReported = batteryLowReported.filter { it >= batteryPercent }.toSet()
        } else {
            val crossed = config.batteryLowThresholds.filter { batteryPercent <= it && it !in batteryLowReported }
            // Rapportera bara när nivån faktiskt sjunkit förbi en tröskel, eller första gången vi ser en låg nivå.
            if (crossed.isNotEmpty() && (before > batteryPercent || batteryLowReported.isEmpty())) {
                batteryLowReported = batteryLowReported + crossed
                emit(BatteryLow(id(stamp), stamp.monotonicMillis, stamp.wallClockUtc, expeditionId, batteryPercent, null))
            }
        }
        publish(stamp)
    }

    @TruthInput @Synchronized
    fun emergency(stamp: Stamp, activate: Boolean, source: EmergencySource = EmergencySource.USER) {
        if (activate == emergencyActive) return
        emergencyActive = activate
        if (activate) {
            emit(EmergencyActivated(id(stamp), stamp.monotonicMillis, stamp.wallClockUtc, expeditionId, source))
        } else {
            emergencyClearedAtMono = stamp.monotonicMillis
            emit(EmergencyCleared(id(stamp), stamp.monotonicMillis, stamp.wallClockUtc, expeditionId))
        }
        publish(stamp)
    }

    @TruthInput @Synchronized
    fun audioOutput(output: AudioOutput) {
        audioOutput = output
        publish(null)
    }

    // ------------------------------------------------------------------------------------
    // Härledning
    // ------------------------------------------------------------------------------------

    private fun evaluateQuality(stamp: Stamp) {
        val fix = lastFix
        val fixMono = lastFixMono
        val now = if (fix == null || fixMono == null) PositionQuality.LOST else {
            val age = (stamp.monotonicMillis - fixMono) / 1000.0
            when {
                age > config.lostAfterSeconds -> PositionQuality.LOST
                age > config.degradedAfterSeconds || fix.accuracyMeters > config.degradedAccuracyMeters -> PositionQuality.DEGRADED
                else -> PositionQuality.GOOD
            }
        }
        if (now == quality) return
        quality = now
        uncertaintySinceMono = if (now == PositionQuality.GOOD) null else stamp.monotonicMillis
        if (fix != null && fixMono != null) { // ingen händelse innan första positionen har kommit
            emit(
                PositionQualityChanged(
                    id(stamp), stamp.monotonicMillis, stamp.wallClockUtc, expeditionId,
                    fix.accuracyMeters, (stamp.monotonicMillis - fixMono) / 1000.0, now,
                ),
            )
        }
        if (now == PositionQuality.LOST) { speedKnown = false; speedMps = 0.0 }
    }

    private fun evaluateStationary(stamp: Stamp) {
        val still = quality != PositionQuality.LOST && speedKnown && speedMps < config.stationarySpeedMps
        if (still) {
            val since = stationarySinceMono ?: stamp.monotonicMillis.also { stationarySinceMono = it; stationaryEmitted = 0 }
            val duration = ((stamp.monotonicMillis - since) / 1000).toInt()
            if (duration >= stationaryThreshold(stationaryEmitted)) {
                stationaryEmitted++
                emit(Stationary(id(stamp), stamp.monotonicMillis, stamp.wallClockUtc, expeditionId, duration))
            }
        } else if (quality == PositionQuality.LOST || speedMps > config.movingSpeedMps) {
            stationarySinceMono = null
            stationaryEmitted = 0
        }
    }

    private fun stationaryThreshold(index: Int): Int {
        val fixed = config.stationaryThresholdsSeconds
        return fixed.getOrNull(index) ?: ((fixed.lastOrNull() ?: 0) + (index - fixed.size + 1) * config.stationaryRepeatSeconds)
    }

    private fun evaluateDayPhase(stamp: Stamp) {
        val where = lastFix?.point ?: anchorPoint ?: return
        val now = Sun.phase(stamp.wallClockUtc, where)
        val before = dayPhase
        dayPhase = now
        if (before != null && before != now) {
            emit(TimeOfDayChanged(id(stamp), stamp.monotonicMillis, stamp.wallClockUtc, expeditionId, now))
        }
    }

    /** R-05: läget REROUTING släpps när första beskedet efter den nya rutten har spelats färdigt. */
    private fun settleReroute(stamp: Stamp) {
        if (!awaitingInstructionAfterReroute) return
        val triggeredAt = instructionAfterRerouteAtMono
        val done = triggeredAt != null && !instructionSpeaking &&
            stamp.monotonicMillis - triggeredAt >= config.instructionSettleMillis
        val heldTooLong = stamp.monotonicMillis - rerouteAtMono >= config.rerouteMaxHoldMillis
        if (done || heldTooLong) awaitingInstructionAfterReroute = false
    }

    private fun maneuverId(tick: NavTick) = "R${tick.routeGeneration}-S${tick.stepsRemaining}"

    private fun assumedSpeed() = if (mode == TravelMode.FOOT) config.footSpeedMps else config.vehicleSpeedMps

    /** Den kortaste av ruttens egen restid och restiden i nuvarande fart: hellre för tyst än för pratig. */
    private fun secondsToManeuver(tick: NavTick): Double? {
        val distance = tick.distanceToManeuverMeters ?: return null
        val bySpeed = if (speedKnown && speedMps > 0.3) distance / speedMps else null
        return listOfNotNull(tick.secondsToManeuverByRoute, bySpeed).minOrNull() ?: (distance / assumedSpeed())
    }

    private fun millisToNextSpoken(tick: NavTick): Long? {
        val meters = tick.metersToNextSpokenTrigger ?: return null
        val distance = tick.distanceToManeuverMeters
        val byRoute = tick.secondsToManeuverByRoute
        val expected = if (distance != null && byRoute != null && byRoute > 0.0 && distance > 0.0) distance / byRoute else assumedSpeed()
        val effective = max(if (speedKnown) speedMps else 0.0, expected)
        return (meters / effective * 1000.0).toLong()
    }

    private fun publish(stamp: Stamp?) {
        val tick = lastTick
        val navigating = active && tick != null
        val offRoute = offRouteSinceMono != null || (navigating && tick?.recalculating == true)
        val rerouting = active && (offRoute || awaitingInstructionAfterReroute)
        _navigation.value = NavigationSnapshot(
            activeRoute = active,
            travelMode = mode,
            speedKmh = if (speedKnown) speedMps * 3.6 else 0.0,
            secondsToNextManeuver = if (navigating) secondsToManeuver(tick!!) else null,
            millisToNextSpokenInstruction = if (navigating) millisToNextSpoken(tick!!) else null,
            instructionSpeaking = instructionSpeaking,
            rerouting = rerouting,
            recentRerouteMonotonicMillis = recentReroutes,
        )
        val fixMono = lastFixMono
        _position.value = PositionSnapshot(
            quality = quality,
            accuracyMeters = lastFix?.accuracyMeters,
            fixAgeSeconds = if (stamp != null && fixMono != null) (stamp.monotonicMillis - fixMono) / 1000.0 else _position.value.fixAgeSeconds,
            uncertaintyShownSinceMonotonicMillis = uncertaintySinceMono,
        )
        _safety.value = SafetySnapshot(
            emergencyActive = emergencyActive,
            emergencyClearedAtMonotonicMillis = emergencyClearedAtMono,
            batteryPercent = batteryPercent,
            convoyActive = false,
            convoyContactLost = false,
            audioOutput = audioOutput,
        )
        val phase = when {
            arrived -> GuidancePhase.ARRIVED
            !active -> GuidancePhase.IDLE
            offRoute -> GuidancePhase.REROUTING
            else -> GuidancePhase.NAVIGATING
        }
        val secondsRemaining = tick?.secondsRemaining
        _guidance.value = GuidanceSnapshot(
            phase = phase,
            travelMode = mode,
            destinationName = destinationName,
            instructionText = if (navigating) tick?.instructionText else null,
            maneuverType = if (navigating) tick?.maneuverType else null,
            maneuverModifier = if (navigating) tick?.maneuverModifier else null,
            distanceToManeuverMeters = if (navigating) tick?.distanceToManeuverMeters else null,
            distanceRemainingMeters = if (navigating) tick?.distanceRemainingMeters else null,
            secondsRemaining = if (navigating) secondsRemaining else null,
            etaWallClockUtc = if (navigating && secondsRemaining != null) tick!!.stamp.wallClockUtc.plusSeconds(secondsRemaining.toLong()) else null,
            stepsRemaining = if (navigating) tick!!.stepsRemaining else 0,
        )
        _map.value = MapSnapshot(
            position = lastFix?.point,
            snappedPosition = if (navigating) tick?.snappedPosition else null,
            bearingDegrees = (if (navigating) tick?.bearingDegrees else null) ?: lastFix?.bearingDegrees,
            routeLine = if (active || arrived) routeLine else emptyList(),
            destination = if (active || arrived) destination else null,
        )
    }

    private fun emit(event: TruthEvent) {
        _events.tryEmit(event)
    }

    private fun id(stamp: Stamp) = "EV-%013d-%06d".format(Locale.ROOT, stamp.wallClockUtc.toEpochMilli(), sequence++)
}
