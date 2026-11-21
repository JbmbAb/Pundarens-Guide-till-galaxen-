package se.pundaren.guide.truth.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.truth.api.DayPhase
import se.pundaren.guide.truth.api.GeoPoint
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

class GeoTest {
    private val orsa = GeoPoint(61.1201175, 14.6222536)
    private val mora = GeoPoint(61.0086787, 14.5442519)
    private val stockholm = GeoPoint(59.3293, 18.0686)

    @Test fun `avstånd och kurs Orsa till Mora`() {
        // Fågelvägen Orsa–Mora är drygt 13 km åt sydsydväst.
        val d = Geo.distanceMeters(orsa, mora)
        assertEquals(13_080.0, d, 60.0)
        val b = Geo.bearingDegrees(orsa, mora)
        assertEquals(198.7, b, 1.0)
        assertEquals("söder", Geo.compassSv(b))
        assertEquals(d, Geo.distanceMeters(mora, orsa), 1e-6)
    }

    @Test fun `en breddgrad är cirka 111 km och destination går tillbaka`() {
        assertEquals(111_195.0, Geo.distanceMeters(GeoPoint(60.0, 15.0), GeoPoint(61.0, 15.0)), 5.0)
        val p = Geo.destination(orsa, 73.0, 2_500.0)
        assertEquals(2_500.0, Geo.distanceMeters(orsa, p), 0.01)
        assertEquals(73.0, Geo.bearingDegrees(orsa, p), 0.01)
    }

    @Test fun `väderstreck i åtta steg`() {
        assertEquals("norr", Geo.compassSv(0.0)); assertEquals("norr", Geo.compassSv(359.0)); assertEquals("norr", Geo.compassSv(22.4))
        assertEquals("nordost", Geo.compassSv(22.5)); assertEquals("öster", Geo.compassSv(90.0))
        assertEquals("sydväst", Geo.compassSv(225.0)); assertEquals("nordväst", Geo.compassSv(-45.0))
    }

    @Test fun `koordinater i grader och decimalminuter`() {
        // 61,1201175° = 61° 07,207′; 14,6222536° = 14° 37,335′
        assertEquals("N 61° 07,207′  E 14° 37,335′", Geo.formatDegreesMinutes(orsa))
        assertEquals("61,12012° N, 14,62225° E", Geo.formatDecimal(orsa))
        // Avrundning som går över en hel minut och en hel grad
        assertEquals("N 61° 00,000′  E 15° 00,000′", Geo.formatDegreesMinutes(GeoPoint(60.9999999, 14.9999999)))
        assertEquals("S 33° 51,500′  W 151° 12,500′", Geo.formatDegreesMinutes(GeoPoint(-33.8583333, -151.2083333)))
    }

    @Test fun `avstånd i klartext`() {
        assertEquals("40 m", Geo.formatDistance(42.0)); assertEquals("990 m", Geo.formatDistance(994.0))
        assertEquals("1,0 km", Geo.formatDistance(996.0)); assertEquals("2,5 km", Geo.formatDistance(2_540.0))
        assertEquals("16 km", Geo.formatDistance(15_730.0))
    }

    /** Kontrollpunkter för SWEREF 99 TM ur Lantmäteriets formelsamling för Gauss-Krügers projektion. */
    @Test fun `SWEREF 99 TM stämmer med Lantmäteriets kontrollpunkter`() {
        val kontroll = listOf(
            Triple(GeoPoint(55.0, 12.75), 6_097_106.672, 356_083.438),
            Triple(GeoPoint(55.0, 14.25), 6_095_048.642, 452_024.069),
            Triple(GeoPoint(57.0, 12.75), 6_319_636.937, 363_331.554),
            Triple(GeoPoint(57.0, 19.5), 6_326_392.707, 773_251.054),
        )
        for ((p, n, e) in kontroll) {
            val tm = Sweref99.toTm(p)
            assertEquals("N för $p", n, tm.northing, 0.001)
            assertEquals("E för $p", e, tm.easting, 0.001)
        }
    }

    @Test fun `SWEREF 99 TM på medelmeridianen och i Orsa`() {
        assertEquals(500_000.0, Sweref99.toTm(GeoPoint(61.0, 15.0)).easting, 1e-6)
        val o = Sweref99.toTm(orsa)
        // Orsa ligger väster om 15° E och drygt 6 777 km norr om ekvatorn.
        assertTrue(o.easting in 479_000.0..480_500.0)
        assertTrue(o.northing in 6_776_000.0..6_779_000.0)
        assertTrue(Regex("""N \d{7}  E \d{6}""").matches(Sweref99.format(orsa)))
    }

    @Test fun `soluppgång och solnedgång i Stockholm vid solstånden`() {
        // Almanacksvärden: 21 juni upp 03.31, ned 22.08 (sommartid); 21 december upp 08.43, ned 14.48 (normaltid).
        val sommar = Sun.dayLight(LocalDate.of(2026, 6, 21), stockholm) as Sun.DayLight.RiseAndSet
        assertNear(Instant.parse("2026-06-21T01:31:00Z"), sommar.sunriseUtc, 240)
        assertNear(Instant.parse("2026-06-21T20:08:00Z"), sommar.sunsetUtc, 240)
        val vinter = Sun.dayLight(LocalDate.of(2026, 12, 21), stockholm) as Sun.DayLight.RiseAndSet
        assertNear(Instant.parse("2026-12-21T07:43:00Z"), vinter.sunriseUtc, 240)
        assertNear(Instant.parse("2026-12-21T13:48:00Z"), vinter.sunsetUtc, 240)
    }

    @Test fun `polarnatt och midnattssol i Kiruna`() {
        val kiruna = GeoPoint(67.8558, 20.2253)
        assertEquals(Sun.DayLight.PolarNight, Sun.dayLight(LocalDate.of(2026, 12, 21), kiruna))
        assertEquals(Sun.DayLight.MidnightSun, Sun.dayLight(LocalDate.of(2026, 6, 21), kiruna))
        assertEquals(0L, Sun.daylightSecondsLeft(Instant.parse("2026-12-21T11:00:00Z"), kiruna))
        assertEquals(null, Sun.daylightSecondsLeft(Instant.parse("2026-06-21T11:00:00Z"), kiruna))
    }

    @Test fun `dag skymning och natt i Orsa den 21 november`() {
        val light = Sun.dayLight(LocalDate.of(2026, 11, 21), orsa) as Sun.DayLight.RiseAndSet
        // Kort dag: mellan fem och sju timmar.
        val length = light.sunsetUtc.epochSecond - light.sunriseUtc.epochSecond
        assertTrue("dagslängd $length", length in 5 * 3600..7 * 3600)
        assertEquals(DayPhase.DAY, Sun.phase(Instant.parse("2026-11-21T10:30:00Z"), orsa))
        assertEquals(DayPhase.DUSK, Sun.phase(light.sunsetUtc.plusSeconds(15 * 60), orsa))
        assertEquals(DayPhase.NIGHT, Sun.phase(Instant.parse("2026-11-21T22:00:00Z"), orsa))
        val left = Sun.daylightSecondsLeft(light.sunsetUtc.minusSeconds(3600), orsa)!!
        assertEquals(3600.0, left.toDouble(), 2.0)
        assertEquals(0L, Sun.daylightSecondsLeft(light.sunsetUtc.plusSeconds(60), orsa))
    }

    @Test fun `solen står i zenit vid ekvatorn vid vårdagjämningen`() {
        // 20 mars 2026 kl. 12 UTC vid longitud 0: nära 90° (inom ett par grader).
        val e = Sun.elevationDegrees(Instant.parse("2026-03-20T12:07:00Z"), GeoPoint(0.0, 0.0))
        assertTrue("höjd $e", e > 88.0)
    }

    private fun assertNear(expected: Instant, actual: Instant, toleranceSeconds: Long) =
        assertTrue("väntade $expected ± $toleranceSeconds s, fick $actual", abs(expected.epochSecond - actual.epochSecond) <= toleranceSeconds)
}
