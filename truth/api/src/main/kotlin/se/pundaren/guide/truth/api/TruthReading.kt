package se.pundaren.guide.truth.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Läsgränssnitt (03, 4.1). Det finns medvetet ingen metod för att publicera,
 * och inget gränssnitt här tar emot text, svansar eller något från tramslagret (G-3, G-5).
 */
interface TruthEventStream {
    val events: Flow<TruthEvent>
}

interface TruthStateReader {
    val navigation: StateFlow<NavigationSnapshot>
    val position: StateFlow<PositionSnapshot>
    val safety: StateFlow<SafetySnapshot>
}

/** Ljudutgång. Okänd räknas som delad (06, R-12). */
enum class AudioOutput { DEVICE_SPEAKER, WIRED_HEADPHONES, BLUETOOTH, CAR_SPEAKER, UNKNOWN }

/** Ögonblicksbild av navigeringen. Ägs av Hard Truth Core. Bara värden. */
data class NavigationSnapshot(
    val activeRoute: Boolean,
    val travelMode: TravelMode,
    val speedKmh: Double,
    /** Sekunder till nästa manöver, eller null om ingen manöver är känd. */
    val secondsToNextManeuver: Double?,
    /** Millisekunder till nästa talinstruktion (kanal B), eller null om ingen är planerad. */
    val millisToNextSpokenInstruction: Long?,
    /** Kanal B spelar just nu. */
    val instructionSpeaking: Boolean,
    /** Från OffRoute till och med att första nya beskedet efter Rerouted har spelats färdigt (06, R-05). */
    val rerouting: Boolean,
    /** monotonicMillis för de senaste omräkningarna (06, R-10). */
    val recentRerouteMonotonicMillis: List<Long> = emptyList(),
)

data class PositionSnapshot(
    val quality: PositionQuality,
    val accuracyMeters: Double?,
    val fixAgeSeconds: Double?,
    /** När sanningsremsan började visa osäkerheten, eller null om den inte visas (06, R-06). */
    val uncertaintyShownSinceMonotonicMillis: Long?,
)

data class SafetySnapshot(
    val emergencyActive: Boolean,
    val emergencyClearedAtMonotonicMillis: Long?,
    val batteryPercent: Int,
    val convoyActive: Boolean,
    val convoyContactLost: Boolean,
    val audioOutput: AudioOutput,
)
