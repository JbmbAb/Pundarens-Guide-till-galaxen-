package se.pundaren.guide.truth.core.geo

import se.pundaren.guide.truth.api.GeoPoint
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

/**
 * WGS84/SWEREF 99 latitud och longitud till SWEREF 99 TM (EPSG:3006), det plana system som svenska
 * kartor och räddningstjänst använder. Gauss-Krügers projektion med Krügers serier enligt
 * Lantmäteriets formelsamling: ellipsoid GRS 80, medelmeridian 15° E, skalfaktor 0,9996,
 * x-tillägg 0 m, y-tillägg 500 000 m. Skillnaden mellan WGS84 och SWEREF 99 är under en meter
 * och bortses från här.
 */
object Sweref99 {
    data class Plane(val northing: Double, val easting: Double)

    private const val A = 6_378_137.0
    private const val F = 1.0 / 298.257222101
    private const val CENTRAL_MERIDIAN_DEG = 15.0
    private const val SCALE = 0.9996
    private const val FALSE_NORTHING = 0.0
    private const val FALSE_EASTING = 500_000.0

    private val e2 = F * (2.0 - F)
    private val n = F / (2.0 - F)
    private val aRoof = A / (1.0 + n) * (1.0 + n * n / 4.0 + n.pow(4) / 64.0)
    private val cA = e2
    private val cB = (5.0 * e2 * e2 - e2.pow(3)) / 6.0
    private val cC = (104.0 * e2.pow(3) - 45.0 * e2.pow(4)) / 120.0
    private val cD = 1237.0 * e2.pow(4) / 1260.0
    private val beta1 = n / 2.0 - 2.0 * n * n / 3.0 + 5.0 * n.pow(3) / 16.0 + 41.0 * n.pow(4) / 180.0
    private val beta2 = 13.0 * n * n / 48.0 - 3.0 * n.pow(3) / 5.0 + 557.0 * n.pow(4) / 1440.0
    private val beta3 = 61.0 * n.pow(3) / 240.0 - 103.0 * n.pow(4) / 140.0
    private val beta4 = 49561.0 * n.pow(4) / 161280.0

    fun toTm(p: GeoPoint): Plane {
        val phi = Math.toRadians(p.lat)
        val dLambda = Math.toRadians(p.lon - CENTRAL_MERIDIAN_DEG)
        val s = sin(phi); val s2 = s * s
        val phiStar = phi - s * cos(phi) * (cA + cB * s2 + cC * s2 * s2 + cD * s2 * s2 * s2)
        val xi = atan(tan(phiStar) / cos(dLambda))
        val eta = atanh(cos(phiStar) * sin(dLambda))
        val x = SCALE * aRoof * (
            xi + beta1 * sin(2 * xi) * cosh(2 * eta) + beta2 * sin(4 * xi) * cosh(4 * eta) +
                beta3 * sin(6 * xi) * cosh(6 * eta) + beta4 * sin(8 * xi) * cosh(8 * eta)
            ) + FALSE_NORTHING
        val y = SCALE * aRoof * (
            eta + beta1 * cos(2 * xi) * sinh(2 * eta) + beta2 * cos(4 * xi) * sinh(4 * eta) +
                beta3 * cos(6 * xi) * sinh(6 * eta) + beta4 * cos(8 * xi) * sinh(8 * eta)
            ) + FALSE_EASTING
        return Plane(x, y)
    }

    /** "N 6777000  E 479700" med hela meter. */
    fun format(p: GeoPoint): String = toTm(p).let { "N ${Math.round(it.northing)}  E ${Math.round(it.easting)}" }

    private fun atanh(v: Double) = 0.5 * ln((1.0 + v) / (1.0 - v))
}
