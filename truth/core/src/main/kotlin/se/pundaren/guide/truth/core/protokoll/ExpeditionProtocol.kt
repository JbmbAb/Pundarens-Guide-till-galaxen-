package se.pundaren.guide.truth.core.protokoll

import se.pundaren.guide.truth.api.Arrived
import se.pundaren.guide.truth.api.BatteryLow
import se.pundaren.guide.truth.api.ConvoyContactLost
import se.pundaren.guide.truth.api.ConvoyGap
import se.pundaren.guide.truth.api.DayPhase
import se.pundaren.guide.truth.api.EmergencyActivated
import se.pundaren.guide.truth.api.EmergencyCleared
import se.pundaren.guide.truth.api.EmergencySource
import se.pundaren.guide.truth.api.ManeuverCompleted
import se.pundaren.guide.truth.api.ManeuverUpcoming
import se.pundaren.guide.truth.api.OffRoute
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.api.PositionQualityChanged
import se.pundaren.guide.truth.api.Rerouted
import se.pundaren.guide.truth.api.RouteStarted
import se.pundaren.guide.truth.api.Stationary
import se.pundaren.guide.truth.api.TimeOfDayChanged
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.api.TruthEvent
import se.pundaren.guide.truth.core.geo.Geo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Protokollets två lager (04, avsnitt 2): fakta ur Hard Truth Core och det Guiden sa. */
enum class ProtocolLayer { TRUTH, SATIRE }

data class ProtocolEntry(
    val atUtc: Instant,
    val layer: ProtocolLayer,
    val text: String,
    /** För SATIRE: radens id i arkivet. För TRUTH: händelsens id. */
    val sourceRef: String?,
)

/**
 * Expeditionsprotokollet (byggplanen): resans riktiga logg i myndighetsform. Bara tillägg.
 *
 * Fakta och satir hålls isär rad för rad. En TRUTH-rad kan bara skapas ur en sanningshändelse
 * ([truth]); det finns ingen väg att skriva fri text i sanningslagret. Det Guiden sa förs in som
 * SATIRE med arkivets rad-id, så att varje replik går att spåra.
 */
class ExpeditionProtocol(val expeditionId: String) {
    private val _entries = ArrayList<ProtocolEntry>()
    val entries: List<ProtocolEntry> get() = _entries.toList()

    private var maneuvers = 0

    /** För in en sanningshändelse. Händelser som inte hör hemma i ett protokoll ger ingen rad. */
    @Synchronized
    fun truth(event: TruthEvent) {
        if (event is ManeuverCompleted) { maneuvers++; return }
        val text = describe(event) ?: return
        _entries += ProtocolEntry(event.wallClockUtc, ProtocolLayer.TRUTH, text, event.eventId)
    }

    /** För in en replik som Guiden faktiskt presenterade. */
    @Synchronized
    fun satire(atUtc: Instant, text: String, lineId: String) {
        _entries += ProtocolEntry(atUtc, ProtocolLayer.SATIRE, text, lineId)
    }

    private fun describe(event: TruthEvent): String? = when (event) {
        is RouteStarted -> "Expeditionen inleddes mot ${event.destinationName}. Fastställd sträcka ${Geo.formatDistance(event.distanceMeters.toDouble())}, " +
            "beräknad restid ${minutes(event.etaSeconds)}, färdsätt: ${mode(event.travelMode)}."
        is Arrived -> "Expeditionen avslutades vid ${event.destinationName} efter ${minutes(event.totalSeconds)} och " +
            "${Geo.formatDistance(event.totalMeters.toDouble())}. Antal genomförda manövrer: $maneuvers."
        is OffRoute -> "Avvikelse från fastställd rutt konstaterades: ${event.deviationMeters} m."
        is Rerouted -> "Ny rutt fastställdes. Återstående sträcka ${Geo.formatDistance(event.newDistanceMeters.toDouble())}, beräknad restid ${minutes(event.newEtaSeconds)}."
        is PositionQualityChanged -> when (event.quality) {
            PositionQuality.GOOD -> "Positionen bedömdes som aktuell (±${event.accuracyMeters.roundToInt()} m)."
            PositionQuality.DEGRADED -> "Positionen bedömdes som osäker (±${event.accuracyMeters.roundToInt()} m, ${event.fixAgeSeconds.roundToInt()} s gammal)."
            PositionQuality.LOST -> "Aktuell position saknades. Senaste position var ${event.fixAgeSeconds.roundToInt()} s gammal."
        }
        is BatteryLow -> "Batterinivån noterades till ${event.percent} %."
        is EmergencyActivated -> "Nödläge aktiverades av ${if (event.source == EmergencySource.USER) "användaren" else "en annan deltagare"}. All satir undertrycktes."
        is EmergencyCleared -> "Nödläget avslutades."
        is Stationary -> "Stillastående i ${minutes(event.durationSeconds)} noterades."
        is TimeOfDayChanged -> "Dygnsfasen övergick till ${when (event.phase) { DayPhase.DAY -> "dag"; DayPhase.DUSK -> "skymning"; DayPhase.NIGHT -> "natt" }}."
        is ConvoyGap -> "Avståndet till ${event.peerAlias} noterades till ${Geo.formatDistance(event.distanceMeters.toDouble())} (positionen ${event.peerFixAgeSeconds} s gammal)."
        is ConvoyContactLost -> "Kontakten med ${event.peerAlias} förlorades. Senast sedd för ${minutes(event.lastSeenAgeSeconds)} sedan."
        is ManeuverUpcoming, is ManeuverCompleted -> null
    }

    /** Protokollet som text. Varje rad är märkt [SANT] eller [GUIDEN]. */
    @Synchronized
    fun render(zone: ZoneId): String = buildString {
        val day = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val time = DateTimeFormatter.ofPattern("HH.mm.ss")
        appendLine("EXPEDITIONSPROTOKOLL $expeditionId")
        _entries.firstOrNull()?.let { appendLine("Upprättat ${day.format(it.atUtc.atZone(zone))}. Tider i ${zone.id}.") }
        appendLine("Rader märkta [SANT] kommer ur sanningslagret. Rader märkta [GUIDEN] är satir ur arkivet.")
        appendLine()
        for (e in _entries) {
            val stamp = time.format(e.atUtc.atZone(zone))
            when (e.layer) {
                ProtocolLayer.TRUTH -> appendLine("$stamp [SANT]   ${e.text}")
                ProtocolLayer.SATIRE -> appendLine("$stamp [GUIDEN] «${e.text}» (rad ${e.sourceRef})")
            }
        }
    }

    private fun minutes(seconds: Int): String {
        val m = (seconds / 60.0).roundToInt()
        return when {
            seconds < 60 -> "$seconds s"
            m < 60 -> "$m min"
            else -> "${m / 60} h ${m % 60} min"
        }
    }

    private fun mode(mode: TravelMode) = when (mode) {
        TravelMode.FOOT -> "till fots"
        TravelMode.PASSENGER -> "passagerare"
        TravelMode.CAR -> "bil"
        TravelMode.BICYCLE -> "cykel"
        TravelMode.SNOWMOBILE -> "snöskoter"
        TravelMode.ATV -> "fyrhjuling"
    }
}

/**
 * Perspektivvyn (byggplanen): en restids andel av universums ålder, uträknad på riktigt.
 * Universums ålder enligt Planck 2018: 13,787 miljarder år.
 */
object Perspective {
    const val UNIVERSE_AGE_YEARS = 13.787e9
    private const val SECONDS_PER_YEAR = 365.25 * 86_400.0
    const val UNIVERSE_AGE_SECONDS = UNIVERSE_AGE_YEARS * SECONDS_PER_YEAR

    fun shareOfUniverseAge(seconds: Double): Double = seconds / UNIVERSE_AGE_SECONDS

    /** "2,2 × 10⁻¹⁵" med svenskt decimalkomma. */
    fun scientific(value: Double): String {
        if (value == 0.0) return "0"
        val exponent = Math.floor(Math.log10(Math.abs(value))).toInt()
        val mantissa = value / Math.pow(10.0, exponent.toDouble())
        return "${Geo.dec(mantissa, 1)} × 10${superscript(exponent)}"
    }

    fun describe(seconds: Double): String =
        "${(seconds / 60.0).roundToInt()} minuter är ${scientific(shareOfUniverseAge(seconds))} av universums ålder."

    private fun superscript(n: Int): String {
        val digits = "⁰¹²³⁴⁵⁶⁷⁸⁹"
        return (if (n < 0) "⁻" else "") + Math.abs(n).toString().map { digits[it - '0'] }.joinToString("")
    }
}
