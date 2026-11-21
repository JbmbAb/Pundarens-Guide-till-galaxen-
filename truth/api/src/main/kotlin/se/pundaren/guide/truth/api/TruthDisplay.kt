package se.pundaren.guide.truth.api

import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/**
 * Det skärmen behöver för att rita sanningen: sanningsremsan, kartans rutt och positionen (02, N-4).
 * Bara läsning och bara värden, som resten av `:truth:api`. Tillägg i etapp 4; kontraktet är
 * fortfarande [CONTRACT_STATUS].
 */
interface TruthDisplayReader {
    val guidance: StateFlow<GuidanceSnapshot>
    val map: StateFlow<MapSnapshot>
    /** Batteriet, för avfärdskontrollen (etapp 5). */
    val power: StateFlow<PowerSnapshot>
}

/** Enhetens batteri som Hard Truth Core senast läste av det. */
data class PowerSnapshot(val batteryPercent: Int, val charging: Boolean)

/** En punkt i WGS84 (EPSG:4326). */
data class GeoPoint(val lat: Double, val lon: Double)

enum class GuidancePhase {
    /** Ingen rutt är aktiv. */
    IDLE,
    NAVIGATING,
    /** Från avvikelse tills en ny rutt finns. Remsan visar bara omräkningen (02, N-3). */
    REROUTING,
    ARRIVED,
}

/**
 * Sanningsremsans innehåll. Texten är Hard Truth Cores egen (Ferrostars visuella instruktion);
 * den har aldrig passerat Illogical Engine.
 */
data class GuidanceSnapshot(
    val phase: GuidancePhase,
    val travelMode: TravelMode,
    val destinationName: String?,
    /** Nästa manöver i klartext, t.ex. "Sväng vänster in på Slaggatan". */
    val instructionText: String?,
    /** OSRM-manövertyp i gemener ("turn", "roundabout", "arrive" …), för pilen. */
    val maneuverType: String?,
    /** OSRM-modifierare i gemener ("left", "slight right", "uturn" …), för pilen. */
    val maneuverModifier: String?,
    val distanceToManeuverMeters: Double?,
    val distanceRemainingMeters: Double?,
    val secondsRemaining: Double?,
    /** Beräknad ankomsttid. Bara för visning. */
    val etaWallClockUtc: Instant?,
    val stepsRemaining: Int,
    /** Sanna fakta som ingen bad om ("Fel fråga"): manövrer som återstår på rutten. */
    val leftTurnsRemaining: Int = 0,
    val rightTurnsRemaining: Int = 0,
    val roundaboutsRemaining: Int = 0,
) {
    companion object {
        fun idle(travelMode: TravelMode = TravelMode.FOOT) = GuidanceSnapshot(
            GuidancePhase.IDLE, travelMode, null, null, null, null, null, null, null, null, 0,
        )
    }
}

/** Kartans sanningsdata: senaste position och den aktiva ruttens linje. */
data class MapSnapshot(
    /** Senaste verkliga positionen, eller null om ingen finns. */
    val position: GeoPoint?,
    /** Positionen fäst mot rutten under navigering, annars null. */
    val snappedPosition: GeoPoint?,
    /** Kurs i grader medurs från norr, om känd. */
    val bearingDegrees: Double?,
    val routeLine: List<GeoPoint>,
    val destination: GeoPoint?,
) {
    companion object {
        val EMPTY = MapSnapshot(null, null, null, emptyList(), null)
    }
}

/**
 * Kanal B (03, 4.4): sanningslagrets väg till högtalaren. En utgående port: Hard Truth Core
 * anropar den, rösten svarar bara med om ett besked spelas just nu. Inget tramslager kan nå
 * sanningen härigenom, eftersom porten inte har någon metod som läser eller ändrar sanningsdata.
 */
interface InstructionVoice {
    /** Läser upp ett besked nu. Avbryter allt annat som rösten spelar (06, R-01). */
    fun speakInstruction(text: String)

    /** Tystar ett pågående besked, t.ex. när navigeringen avbryts. */
    fun stopInstruction()

    /** Sant medan ett besked spelas eller väntar på att spelas. */
    val instructionSpeaking: StateFlow<Boolean>
}
