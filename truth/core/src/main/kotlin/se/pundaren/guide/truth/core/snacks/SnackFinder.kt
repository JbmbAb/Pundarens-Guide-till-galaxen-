package se.pundaren.guide.truth.core.snacks

import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.core.drift.ImprobabilityDrive
import se.pundaren.guide.truth.core.geo.Geo
import se.pundaren.guide.truth.core.platser.Place
import se.pundaren.guide.truth.core.platser.PlaceIndex
import se.pundaren.guide.truth.core.platser.PlaceKind
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

enum class OpenState { OPEN, CLOSED, UNKNOWN }

/** Ett matställe med sina sanna värden: restid dit, omväg och om det har öppet vid ankomst. */
data class SnackStop(
    val place: Place,
    /** Restid från nuvarande position till matstället, sekunder. */
    val secondsToPlace: Double,
    /** Extra restid jämfört med att åka raka vägen till målet. Null när det inte finns något mål. */
    val detourSeconds: Double?,
    val openAtArrival: OpenState,
)

/**
 * Sannolikhet för snacks (byggplanen): matställen inom vald omväg och om de har öppet vid ankomst.
 * "Okänd" när öppettider saknas – och det gör de i dagens offlinepaket, eftersom kartfilen inte bär
 * `opening_hours`. Ingen sannolikhet hittas på: saknas öppettider för alla är svaret "okänd".
 */
object SnackFinder {
    private val KINDS = setOf(PlaceKind.FOOD, PlaceKind.SHOP, PlaceKind.FUEL)

    /** Matställen som kan ligga inom omvägen, närmast först. Begränsas före restidsmatrisen. */
    fun candidates(index: PlaceIndex, from: GeoPoint, to: GeoPoint?, maxDetourSeconds: Double, mode: TravelMode, limit: Int = 25): List<Place> {
        val reach = maxDetourSeconds * ImprobabilityDrive.topSpeedMps(mode)
        return index.places.asSequence()
            .filter { it.kind in KINDS }
            .map { p ->
                val extra = if (to == null) Geo.distanceMeters(from, p.point)
                else Geo.distanceMeters(from, p.point) + Geo.distanceMeters(p.point, to) - Geo.distanceMeters(from, to)
                p to extra
            }
            .filter { it.second <= reach }
            .sortedWith(compareBy({ it.second }, { it.first.id }))
            .take(limit).map { it.first }.toList()
    }

    /**
     * Rangordnar kandidaterna efter omväg (eller restid när inget mål finns). [toPlace] är restiden
     * från positionen till varje kandidat, [placeToDestination] från varje kandidat till målet och
     * [directSeconds] raka vägen till målet. Kandidater utan väg eller med för lång omväg faller bort.
     */
    fun rank(
        candidates: List<Place>,
        toPlace: List<Double?>,
        placeToDestination: List<Double?>?,
        directSeconds: Double?,
        maxDetourSeconds: Double,
        nowLocal: LocalDateTime,
    ): List<SnackStop> {
        require(candidates.size == toPlace.size) { "en restid per kandidat" }
        return candidates.indices.mapNotNull { i ->
            val there = toPlace[i] ?: return@mapNotNull null
            val detour = if (placeToDestination != null && directSeconds != null) {
                val onward = placeToDestination.getOrNull(i) ?: return@mapNotNull null
                (there + onward - directSeconds).coerceAtLeast(0.0)
            } else null
            if ((detour ?: there) > maxDetourSeconds) return@mapNotNull null
            SnackStop(candidates[i], there, detour, OpeningHours.stateAt(candidates[i].openingHours, nowLocal.plusSeconds(there.toLong())))
        }.sortedWith(compareBy({ it.detourSeconds ?: it.secondsToPlace }, { it.place.id }))
    }

    /** Det sanna värdet under skämtet, i klartext. */
    fun summary(stops: List<SnackStop>): String {
        if (stops.isEmpty()) return "Sannolikhet för snacks: inga matställen inom omvägen."
        val known = stops.filter { it.openAtArrival != OpenState.UNKNOWN }
        val open = known.count { it.openAtArrival == OpenState.OPEN }
        return when {
            known.isEmpty() -> "Sannolikhet för snacks: okänd. ${stops.size} matställen inom omvägen, öppettider saknas."
            else -> "Snacks: $open av ${known.size} matställen med kända öppettider har öppet vid ankomst" +
                (if (known.size < stops.size) ", ${stops.size - known.size} okända." else ".")
        }
    }
}

/**
 * Försiktig läsare av OSM-taggen `opening_hours`. Förstår bara de enklaste formerna: `24/7` och
 * regler som `Mo-Fr 08:00-17:00; Sa 10:00-14:00; Su off`. Allt annat (helgdagar, månader, solens
 * tider, kommentarer, tider över midnatt) ger UNKNOWN. Hellre okänt än fel.
 */
object OpeningHours {
    private val days = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    private val rule = Regex("""^((?:Mo|Tu|We|Th|Fr|Sa|Su)(?:-(?:Mo|Tu|We|Th|Fr|Sa|Su))?(?:,(?:Mo|Tu|We|Th|Fr|Sa|Su)(?:-(?:Mo|Tu|We|Th|Fr|Sa|Su))?)*)? ?(off|closed|\d{2}:\d{2}-\d{2}:\d{2}(?:,\d{2}:\d{2}-\d{2}:\d{2})*)$""")

    fun stateAt(spec: String?, at: LocalDateTime): OpenState {
        val text = spec?.trim().orEmpty()
        if (text.isEmpty()) return OpenState.UNKNOWN
        if (text == "24/7") return OpenState.OPEN
        val today = at.dayOfWeek
        var result: OpenState? = null
        for (part in text.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            val match = rule.matchEntire(part) ?: return OpenState.UNKNOWN
            val daySpec = match.groupValues[1]
            val applies = daySpec.isEmpty() || daysOf(daySpec)?.contains(today) ?: return OpenState.UNKNOWN
            if (!applies) continue
            val times = match.groupValues[2]
            result = if (times == "off" || times == "closed") OpenState.CLOSED else {
                var open = false
                for (span in times.split(',')) {
                    val from = time(span.substringBefore('-')) ?: return OpenState.UNKNOWN
                    val to = time(span.substringAfter('-')) ?: return OpenState.UNKNOWN
                    if (!to.isAfter(from)) return OpenState.UNKNOWN // över midnatt: tolkas inte
                    if (!at.toLocalTime().isBefore(from) && at.toLocalTime().isBefore(to)) open = true
                }
                if (open) OpenState.OPEN else OpenState.CLOSED
            }
        }
        // En senare regel för samma dag ersätter en tidigare, som i OSM. Dagar utan regel är stängda.
        return result ?: OpenState.CLOSED
    }

    private fun daysOf(spec: String): Set<DayOfWeek>? {
        val out = HashSet<DayOfWeek>()
        for (piece in spec.split(',')) {
            val a = days.indexOf(piece.substringBefore('-'))
            val b = days.indexOf(piece.substringAfter('-'))
            if (a < 0 || b < 0) return null
            var i = a
            while (true) {
                out += DayOfWeek.of(i + 1)
                if (i == b) break
                i = (i + 1) % 7
            }
        }
        return out
    }

    private fun time(text: String): LocalTime? {
        val h = text.substringBefore(':').toIntOrNull() ?: return null
        val m = text.substringAfter(':').toIntOrNull() ?: return null
        if (h == 24 && m == 0) return LocalTime.MAX
        return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
    }
}
