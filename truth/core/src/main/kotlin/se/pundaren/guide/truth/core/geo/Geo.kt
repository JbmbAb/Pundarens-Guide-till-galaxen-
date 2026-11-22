package se.pundaren.guide.truth.core.geo

import se.pundaren.guide.truth.api.GeoPoint
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Avstånd, riktning och koordinatformat på sfären (WGS84-punkter). Ren matematik, inga tillstånd. */
object Geo {
    /** Jordens medelradie (IUGG), meter. */
    const val EARTH_RADIUS_M = 6_371_008.8

    /** Storcirkelavstånd i meter (haversine). */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat)
        val dp = p2 - p1; val dl = Math.toRadians(b.lon - a.lon)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Startkurs från [from] till [to] i grader medurs från norr, 0 ≤ kurs < 360. */
    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val p1 = Math.toRadians(from.lat); val p2 = Math.toRadians(to.lat)
        val dl = Math.toRadians(to.lon - from.lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Punkten [meters] bort från [from] i kursen [bearingDegrees]. */
    fun destination(from: GeoPoint, bearingDegrees: Double, meters: Double): GeoPoint {
        val d = meters / EARTH_RADIUS_M
        val b = Math.toRadians(bearingDegrees)
        val p1 = Math.toRadians(from.lat); val l1 = Math.toRadians(from.lon)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return GeoPoint(Math.toDegrees(p2), (Math.toDegrees(l2) + 540.0) % 360.0 - 180.0)
    }

    private val compass = listOf("norr", "nordost", "öster", "sydost", "söder", "sydväst", "väster", "nordväst")

    /** Väderstreck på svenska i åtta steg. */
    fun compassSv(bearingDegrees: Double): String =
        compass[(floor(((bearingDegrees % 360.0 + 360.0) % 360.0 + 22.5) / 45.0).toInt()) % 8]

    /** Decimalgrader med fem decimaler (cirka en meter), t.ex. "61,12012° N, 14,62225° E". */
    fun formatDecimal(p: GeoPoint): String =
        "${dec(abs(p.lat), 5)}° ${if (p.lat >= 0) "N" else "S"}, ${dec(abs(p.lon), 5)}° ${if (p.lon >= 0) "E" else "W"}"

    /**
     * Grader och decimalminuter, formatet som är lättast att läsa upp i telefon:
     * "N 61° 07,207′  E 14° 37,335′".
     */
    fun formatDegreesMinutes(p: GeoPoint): String = "${ddm(p.lat, 'N', 'S', 2)}  ${ddm(p.lon, 'E', 'W', 2)}"

    private fun ddm(value: Double, pos: Char, neg: Char, degDigits: Int): String {
        val hemi = if (value >= 0) pos else neg
        var totalThousandths = Math.round(abs(value) * 60_000.0) // tusendels minuter
        val deg = totalThousandths / 60_000
        totalThousandths -= deg * 60_000
        val min = totalThousandths / 1000
        val frac = totalThousandths % 1000
        return "%c %0${degDigits}d° %02d,%03d′".format(java.util.Locale.ROOT, hemi, deg, min, frac)
    }

    /** Avstånd i klartext: meter under en kilometer, annars kilometer med en decimal. */
    fun formatDistance(meters: Double): String = when {
        meters < 0 -> "–"
        meters < 995 -> "${(Math.round(meters / 10.0) * 10).coerceAtLeast(0)} m"
        meters < 9_950 -> "${dec(meters / 1000.0, 1)} km"
        else -> "${Math.round(meters / 1000.0)} km"
    }

    /** Decimaltal med svenskt decimalkomma, oberoende av enhetens språkinställning. */
    fun dec(value: Double, decimals: Int): String = String.format(java.util.Locale.ROOT, "%.${decimals}f", value).replace('.', ',')
}
