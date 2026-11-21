package se.pundaren.guide.truth.core.geo

import se.pundaren.guide.truth.api.DayPhase
import se.pundaren.guide.truth.api.GeoPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Solens läge enligt NOAA:s förenklade algoritm (Meeus). Noggrannhet cirka en minut för
 * soluppgång och solnedgång på svenska breddgrader. Ren matematik: tid och plats in, värde ut.
 */
object Sun {
    /** Zenitvinkel vid soluppgång och solnedgång: 90° + refraktion och solskivans radie. */
    private const val RISE_SET_ZENITH = 90.833

    /** Borgerlig skymning slutar när solen står 6° under horisonten. */
    private const val CIVIL_TWILIGHT_ELEVATION = -6.0

    sealed interface DayLight {
        /** Vanligt dygn med upp- och nedgång. */
        data class RiseAndSet(val sunriseUtc: Instant, val sunsetUtc: Instant) : DayLight
        data object MidnightSun : DayLight
        data object PolarNight : DayLight
    }

    private data class Solar(val declinationRad: Double, val equationOfTimeMinutes: Double)

    private fun solar(at: Instant): Solar {
        val jd = at.epochSecond / 86_400.0 + 2_440_587.5
        val t = (jd - 2_451_545.0) / 36_525.0
        val l0 = Math.toRadians(((280.46646 + t * (36_000.76983 + 0.0003032 * t)) % 360.0 + 360.0) % 360.0)
        val m = Math.toRadians(357.52911 + t * (35_999.05029 - 0.0001537 * t))
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * m) * (0.019993 - 0.000101 * t) + sin(3 * m) * 0.000289
        val trueLong = Math.toDegrees(l0) + c
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val lambda = Math.toRadians(trueLong - 0.00569 - 0.00478 * sin(omega))
        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = Math.toRadians(eps0 + 0.00256 * cos(omega))
        val decl = asin(sin(eps) * sin(lambda))
        val y = tan(eps / 2).let { it * it }
        val eqT = 4.0 * Math.toDegrees(
            y * sin(2 * l0) - 2 * e * sin(m) + 4 * e * y * sin(m) * cos(2 * l0) -
                0.5 * y * y * sin(4 * l0) - 1.25 * e * e * sin(2 * m),
        )
        return Solar(decl, eqT)
    }

    /** Solens höjd över horisonten i grader (utan refraktionskorrektion). */
    fun elevationDegrees(at: Instant, p: GeoPoint): Double {
        val s = solar(at)
        val minutesUtc = (at.epochSecond % 86_400) / 60.0
        val trueSolarMinutes = ((minutesUtc + s.equationOfTimeMinutes + 4.0 * p.lon) % 1440.0 + 1440.0) % 1440.0
        val hourAngle = Math.toRadians(trueSolarMinutes / 4.0 - 180.0)
        val lat = Math.toRadians(p.lat)
        val cosZenith = sin(lat) * sin(s.declinationRad) + cos(lat) * cos(s.declinationRad) * cos(hourAngle)
        return 90.0 - Math.toDegrees(acos(cosZenith.coerceIn(-1.0, 1.0)))
    }

    /** Soluppgång och solnedgång (UTC) för datumet [dateUtc] på platsen. */
    fun dayLight(dateUtc: LocalDate, p: GeoPoint): DayLight {
        val noon = dateUtc.atTime(12, 0).toInstant(ZoneOffset.UTC).minusSeconds((p.lon * 240.0).toLong())
        val s = solar(noon)
        val lat = Math.toRadians(p.lat)
        val cosH0 = cos(Math.toRadians(RISE_SET_ZENITH)) / (cos(lat) * cos(s.declinationRad)) - tan(lat) * tan(s.declinationRad)
        if (cosH0 > 1.0) return DayLight.PolarNight
        if (cosH0 < -1.0) return DayLight.MidnightSun
        val h0 = Math.toDegrees(acos(cosH0))
        val solarNoonMinutes = 720.0 - 4.0 * p.lon - s.equationOfTimeMinutes
        val midnight = dateUtc.atStartOfDay().toInstant(ZoneOffset.UTC)
        fun at(minutes: Double) = midnight.plusSeconds(Math.round(minutes * 60.0))
        return DayLight.RiseAndSet(at(solarNoonMinutes - 4.0 * h0), at(solarNoonMinutes + 4.0 * h0))
    }

    /** Dag, skymning (även gryning) eller natt. */
    fun phase(at: Instant, p: GeoPoint): DayPhase {
        val elevation = elevationDegrees(at, p)
        return when {
            elevation > 90.0 - RISE_SET_ZENITH -> DayPhase.DAY
            elevation > CIVIL_TWILIGHT_ELEVATION -> DayPhase.DUSK
            else -> DayPhase.NIGHT
        }
    }

    /**
     * Sekunder dagsljus som återstår vid tidpunkten [at]: tid till solnedgången om solen är uppe,
     * 0 om den är nere, och null vid midnattssol (ingen solnedgång det dygnet).
     */
    fun daylightSecondsLeft(at: Instant, p: GeoPoint): Long? {
        val date = at.atOffset(ZoneOffset.UTC).toLocalDate()
        // Solnedgången kan höra till föregående eller nästa UTC-datum beroende på longitud.
        for (d in listOf(date.minusDays(1), date, date.plusDays(1))) {
            when (val light = dayLight(d, p)) {
                is DayLight.RiseAndSet ->
                    if (!at.isBefore(light.sunriseUtc) && at.isBefore(light.sunsetUtc)) return light.sunsetUtc.epochSecond - at.epochSecond
                DayLight.MidnightSun -> if (d == date) return null
                DayLight.PolarNight -> {}
            }
        }
        return 0
    }
}
