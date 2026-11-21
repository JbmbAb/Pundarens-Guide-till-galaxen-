package se.pundaren.guide.truth.core.nodkort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.PositionQuality

class EmergencyCardTest {
    private val orsa = GeoPoint(61.1201175, 14.6222536)
    private val mora = GeoPoint(61.0086787, 14.5442519)

    @Test fun `kortet har koordinater i tre format, riktning hem och batteri`() {
        val card = EmergencyCard.build(orsa, 4.4, 2.2, PositionQuality.GOOD, home = mora, batteryPercent = 63)
        assertEquals("N 61° 07,207′  E 14° 37,335′", card.degreesMinutes)
        assertEquals("61,12012° N, 14,62225° E", card.decimalDegrees)
        assertTrue(card.sweref99tm!!.startsWith("N 6777") || card.sweref99tm!!.startsWith("N 6776") || card.sweref99tm!!.startsWith("N 6778"))
        assertEquals("söder", card.homeCompass)
        assertEquals(199, card.homeBearingDegrees)
        assertEquals(13_080.0, card.homeDistanceMeters!!.toDouble(), 60.0)
        val lines = card.lines()
        assertEquals(EmergencyCard.NOT_112, lines.first())
        assertTrue(lines.any { it == "Noggrannhet: ±4 m, 2 s gammal." })
        assertTrue(lines.any { it.startsWith("Hem: 13 km åt söder (199°), fågelvägen.") })
        assertEquals("Batteri: 63 %.", lines.last())
    }

    @Test fun `utan position säger kortet det rakt ut`() {
        val card = EmergencyCard.build(null, null, null, PositionQuality.LOST, home = mora, batteryPercent = 9)
        assertFalse(card.hasPosition)
        val lines = card.lines()
        assertTrue(lines.contains("Position: okänd. Ingen position har tagits emot."))
        assertTrue(lines.contains("Hem: sparat, men riktning och avstånd kräver en position."))
        assertTrue(lines.none { it.contains("null") })
    }

    @Test fun `gammal eller osäker position märks tydligt`() {
        val old = EmergencyCard.build(orsa, 80.0, 190.0, PositionQuality.LOST, home = null, batteryPercent = 50)
        assertTrue(old.lines().contains("Noggrannhet: ±80 m, 3 min gammal. Positionen är gammal och kan vara fel."))
        assertTrue(old.lines().contains("Hem: inte sparat."))
        val unsure = EmergencyCard.build(orsa, 45.0, 3.0, PositionQuality.DEGRADED, home = null, batteryPercent = 50)
        assertTrue(unsure.lines().contains("Noggrannhet: ±45 m, 3 s gammal. Positionen är osäker."))
    }

    @Test fun `kortet säger alltid att det inte ersätter 112`() {
        for (q in PositionQuality.entries) {
            assertTrue(EmergencyCard.build(orsa, 5.0, 1.0, q, null, 80).lines().first().contains("ersätter inte 112"))
        }
    }
}
