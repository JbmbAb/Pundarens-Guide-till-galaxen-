package se.pundaren.guide.truth.core.platser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.truth.api.GeoPoint

class PlaceIndexTest {
    private fun feature(id: String, name: String, kind: String, sub: String, lat: Double, lon: Double, hours: String? = null) =
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,$lat]},"properties":{"id":"$id","name":"$name","kind":"$kind","subKind":"$sub"${if (hours != null) ""","opening_hours":"$hours"""" else ""}}}"""

    private val json = """{"type":"FeatureCollection","features":[
        ${feature("node/1", "Orsa", "SETTLEMENT", "town", 61.1201175, 14.6222536)},
        ${feature("node/2", "Älvdalen", "SETTLEMENT", "town", 61.2278, 14.0394)},
        ${feature("node/3", "Orsa Grönklitt", "SETTLEMENT", "hamlet", 61.2077132, 14.5377551)},
        ${feature("node/4", "Orsa Pizzeria", "FOOD", "restaurant", 61.1190, 14.6190, "Mo-Su 11:00-21:00")},
        ${feature("node/5", "Mora", "SETTLEMENT", "town", 61.0086787, 14.5442519)},
        ${feature("node/6", "Kiosken i Mora", "SHOP", "kiosk", 61.0050, 14.5400)},
        {"type":"Feature","geometry":{"type":"Point","coordinates":[14.0,61.0]},"properties":{"id":"node/7","name":"","kind":"FOOD","subKind":"cafe"}},
        {"type":"Feature","geometry":{"type":"LineString","coordinates":[[14.0,61.0],[14.1,61.1]]},"properties":{"id":"way/8","name":"En väg","kind":"OTHER","subKind":"x"}}
    ]}"""

    private val index = PlaceIndex.parse(json)
    private val orsa = GeoPoint(61.1201175, 14.6222536)

    @Test fun `läser punkter med namn och hoppar över resten`() {
        assertEquals(6, index.places.size)
        val pizzeria = index.places.single { it.id == "node/4" }
        assertEquals(PlaceKind.FOOD, pizzeria.kind); assertEquals("restaurant", pizzeria.subKind)
        assertEquals("Mo-Su 11:00-21:00", pizzeria.openingHours)
        assertEquals(61.1190, pizzeria.point.lat, 1e-9); assertEquals(14.6190, pizzeria.point.lon, 1e-9)
        assertNull(index.places.single { it.id == "node/1" }.openingHours)
    }

    @Test fun `sökning bryr sig inte om versaler eller prickar`() {
        assertEquals(listOf("Älvdalen"), index.search("alvdalen").map { it.name })
        assertEquals(listOf("Älvdalen"), index.search("ÄLVD").map { it.name })
        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("finns inte").isEmpty())
    }

    @Test fun `exakt träff först, sedan börjar-med, orter före annat`() {
        assertEquals(listOf("Orsa", "Orsa Grönklitt", "Orsa Pizzeria"), index.search("orsa", near = orsa).map { it.name })
        assertEquals(listOf("Mora", "Kiosken i Mora"), index.search("mora").map { it.name })
        assertEquals(listOf("Orsa Pizzeria"), index.search("orsa", kinds = setOf(PlaceKind.FOOD)).map { it.name })
    }

    @Test fun `närmast först inom en radie`() {
        val near = index.nearest(orsa, setOf(PlaceKind.FOOD, PlaceKind.SHOP), maxMeters = 20_000.0)
        assertEquals(listOf("node/4", "node/6"), near.map { it.first.id })
        assertTrue(near[0].second < 300.0); assertTrue(near[1].second in 12_000.0..14_000.0)
        assertEquals(listOf("node/4"), index.nearest(orsa, setOf(PlaceKind.FOOD, PlaceKind.SHOP), maxMeters = 1_000.0).map { it.first.id })
    }
}
