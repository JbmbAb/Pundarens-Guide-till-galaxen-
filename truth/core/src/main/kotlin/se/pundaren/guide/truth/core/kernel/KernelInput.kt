package se.pundaren.guide.truth.core.kernel

import se.pundaren.guide.truth.api.GeoPoint
import java.time.Instant

/**
 * Tidsstämpel som `:truth:device` läser av en gång per indata. Kärnan läser aldrig någon klocka
 * själv, så samma indata ger samma händelser (testbart på JVM).
 */
data class Stamp(
    /** Androids monotona klocka (SystemClock.elapsedRealtime()). Avgör ordning och aktualitet. */
    val monotonicMillis: Long,
    /** Väggklocka. Bara för loggning, visning och solens läge. */
    val wallClockUtc: Instant,
)

enum class NavPhase { IDLE, NAVIGATING, ARRIVED }

/**
 * Ett ögonblick ur navigeringsmotorn (Ferrostar), översatt till rena värden av `:truth:device`.
 * Kärnan känner inte till Ferrostar.
 */
data class NavTick(
    val stamp: Stamp,
    val phase: NavPhase,
    /** Ökar med ett varje gång en ny rutt börjar gälla: vid start och efter varje omräkning. */
    val routeGeneration: Int,
    val stepsRemaining: Int,
    /** Den visuella instruktionens text för nästa manöver, ordagrant ur ruttmotorn. */
    val instructionText: String? = null,
    val maneuverType: String? = null,
    val maneuverModifier: String? = null,
    val streetName: String? = null,
    val distanceToManeuverMeters: Double? = null,
    /** Ruttmotorns egen restid till manövern (bygger på ruttens förväntade fart). */
    val secondsToManeuverByRoute: Double? = null,
    /** Meter kvar tills nästa talinstruktion utlöses, eller null om det är okänt. */
    val metersToNextSpokenTrigger: Double? = null,
    val distanceRemainingMeters: Double? = null,
    val secondsRemaining: Double? = null,
    val distanceTraveledMeters: Double? = null,
    /** Avstånd från rutten i meter när positionen avviker, annars null. */
    val deviationMeters: Double? = null,
    /** Navigeringsmotorn räknar om rutten just nu. */
    val recalculating: Boolean = false,
    val snappedPosition: GeoPoint? = null,
    val bearingDegrees: Double? = null,
    /** Manövrer som återstår på rutten, räknade ur ruttmotorns steg. */
    val leftTurnsRemaining: Int = 0,
    val rightTurnsRemaining: Int = 0,
    val roundaboutsRemaining: Int = 0,
)

/** En position från enhetens GNSS (eller från provturens simulering). */
data class PositionFix(
    val point: GeoPoint,
    val accuracyMeters: Double,
    val speedMps: Double? = null,
    val bearingDegrees: Double? = null,
    val altitudeMeters: Double? = null,
)

/** Den andra deltagarens senaste position i en pågående delning, med ärlig ålder (07, 3.5). */
data class ConvoyPeerFix(val point: GeoPoint, val ageSeconds: Int)

/**
 * Tröskelvärden i kärnan. Kandidatvärden, inte frysta (G0-grindbeslutet). De prövas i G5.
 */
data class KernelConfig(
    /** Sämre noggrannhet än så här ger DEGRADED. */
    val degradedAccuracyMeters: Double = 30.0,
    /** Äldre position än så här ger DEGRADED. */
    val degradedAfterSeconds: Double = 6.0,
    /** Äldre position än så här ger LOST. */
    val lostAfterSeconds: Double = 20.0,
    /** Under den här farten räknas enheten som stillastående. */
    val stationarySpeedMps: Double = 0.5,
    /** Över den här farten räknas enheten som i rörelse igen. */
    val movingSpeedMps: Double = 1.0,
    /** Stationary-händelser skickas vid dessa varaktigheter, därefter med [stationaryRepeatSeconds] mellanrum. */
    val stationaryThresholdsSeconds: List<Int> = listOf(60, 300, 900),
    val stationaryRepeatSeconds: Int = 1_800,
    /** BatteryLow skickas när nivån passerar dessa värden nedåt utan laddning. */
    val batteryLowThresholds: List<Int> = listOf(20, 15, 10, 5),
    /** Kortaste tid efter ett utlöst besked innan det räknas som färdigspelat när rösten inte svarar. */
    val instructionSettleMillis: Long = 1_500,
    /** Längsta tid läget REROUTING hålls efter en ny rutt om inget besked kommer. */
    val rerouteMaxHoldMillis: Long = 30_000,
    val recentReroutesKept: Int = 5,
    /** Antagen fart när varken positionen eller rutten ger någon. */
    val footSpeedMps: Double = 1.4,
    val vehicleSpeedMps: Double = 8.0,
    /** Plats för solens läge innan första positionen finns, t.ex. offlinepaketets mittpunkt. */
    val fallbackPosition: GeoPoint? = null,
    /** Äldre än så här och kontakten med den andra i konvojen räknas som bruten (07, 3.5: då ritas ingen prick). */
    val convoyContactLostAfterSeconds: Int = 900,
    /** ConvoyGap skickas när avståndet till den andra passerar det här värdet uppåt, till fots respektive i fordon. */
    val convoyGapMetersOnFoot: Int = 300,
    val convoyGapMetersInVehicle: Int = 2_000,
) {
    companion object {
        const val STATUS = "KANDIDAT_OFRYST"
    }
}
