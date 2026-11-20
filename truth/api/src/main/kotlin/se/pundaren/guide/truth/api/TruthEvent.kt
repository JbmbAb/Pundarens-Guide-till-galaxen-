package se.pundaren.guide.truth.api

import java.time.Instant

/**
 * Händelsekontraktet i 05. Status: [CONTRACT_STATUS].
 *
 * Bussen är enkelriktad: Hard Truth Core -> händelser -> Illogical Engine.
 * Det här paketet innehåller bara oföränderliga värdetyper och ett läsgränssnitt.
 * Det finns ingen publiceringsmetod här (03, G-3); publiceringen ska ligga
 * `internal` i `:truth:core`, som inte byggs i den här etappen.
 */
const val CONTRACT_SCHEMA_VERSION: Int = 1

/** 05 är inte fryst (G0-grindbeslutet). Ändringar får bara vara tillägg tills listan fryses. */
const val CONTRACT_STATUS: String = "KANDIDAT_OFRYST"

/** Gemensamt kuvert (05, avsnitt 2). Alla implementationer är `data class` med bara `val`. */
sealed interface TruthEvent {
    /** ULID eller annat unikt, sorterbart id. */
    val eventId: String
    /** Ordning och aktualitet avgörs bara av detta värde (motsvarar SystemClock.elapsedRealtime()). */
    val monotonicMillis: Long
    /** Bara för loggning och visning. Används aldrig för att avgöra aktualitet. */
    val wallClockUtc: Instant
    val expeditionId: String?
    val schemaVersion: Int
}

/** Prioritet i SafetyGate enligt kolumnen i 05, avsnitt 3. */
enum class EventPriority { LOW, NORMAL, HIGH, CRITICAL, EMERGENCY }

/** Färdsätt enligt 04, avsnitt 2. v1 omfattar bara FOOT och PASSENGER (F-05). */
enum class TravelMode { FOOT, PASSENGER, CAR, BICYCLE, SNOWMOBILE, ATV }

enum class PositionQuality { GOOD, DEGRADED, LOST }
enum class DayPhase { DAY, DUSK, NIGHT }
enum class EmergencySource { USER, PEER }

/** 1 */
data class RouteStarted(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    val destinationName: String,
    val distanceMeters: Int,
    val etaSeconds: Int,
    val travelMode: TravelMode,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** 2 – kritisk. */
data class ManeuverUpcoming(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    val maneuverId: String,
    val type: String,
    val modifier: String?,
    val distanceMeters: Int,
    val secondsToManeuver: Double,
    val streetName: String?,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** 3 */
data class ManeuverCompleted(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    val maneuverId: String,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** 4 – kritisk. */
data class OffRoute(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    val deviationMeters: Int,
    val secondsOffRoute: Int,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** 5 */
data class Rerouted(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    val newDistanceMeters: Int,
    val newEtaSeconds: Int,
    val reason: String,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** 6 */
data class Stationary(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    val durationSeconds: Int,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** 7 */
data class Arrived(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    val destinationName: String,
    val totalSeconds: Int,
    val totalMeters: Int,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** 8 */
data class PositionQualityChanged(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    val accuracyMeters: Double,
    val fixAgeSeconds: Double,
    val quality: PositionQuality,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** 9 */
data class BatteryLow(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    val percent: Int,
    val estimatedMinutesLeft: Int?,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** 10 */
data class ConvoyGap(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    val peerAlias: String,
    val distanceMeters: Int,
    val peerFixAgeSeconds: Int,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** 11 – kritisk. */
data class ConvoyContactLost(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    val peerAlias: String,
    val lastSeenAgeSeconds: Int,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** 12 – nödläge. */
data class EmergencyActivated(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    val source: EmergencySource,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** 13 – tillägg utöver byggplanens tolv (B-21). */
data class EmergencyCleared(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** 14 – tillägg utöver byggplanens tolv (B-21). */
data class TimeOfDayChanged(
    override val eventId: String,
    override val monotonicMillis: Long,
    override val wallClockUtc: Instant,
    override val expeditionId: String?,
    val phase: DayPhase,
    override val schemaVersion: Int = CONTRACT_SCHEMA_VERSION,
) : TruthEvent

/** Prioritet per händelsetyp enligt 05, avsnitt 3. */
fun TruthEvent.priority(): EventPriority = when (this) {
    is RouteStarted -> EventPriority.NORMAL
    is ManeuverUpcoming -> EventPriority.CRITICAL
    is ManeuverCompleted -> EventPriority.NORMAL
    is OffRoute -> EventPriority.CRITICAL
    is Rerouted -> EventPriority.HIGH
    is Stationary -> EventPriority.LOW
    is Arrived -> EventPriority.NORMAL
    is PositionQualityChanged -> if (quality == PositionQuality.LOST) EventPriority.HIGH else EventPriority.NORMAL
    is BatteryLow -> EventPriority.HIGH
    is ConvoyGap -> EventPriority.HIGH
    is ConvoyContactLost -> EventPriority.CRITICAL
    is EmergencyActivated -> EventPriority.EMERGENCY
    is EmergencyCleared -> EventPriority.NORMAL
    is TimeOfDayChanged -> EventPriority.LOW
}
