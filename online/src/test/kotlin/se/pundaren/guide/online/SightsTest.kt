package se.pundaren.guide.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SightsTest {
    private class Canned(private val reply: (String) -> String) : HttpPort {
        val urls = mutableListOf<String>()
        var body: String? = null
        override fun get(url: String, headers: Map<String, String>): String { urls += url; return reply(url) }
        override fun post(url: String, headers: Map<String, String>, body: String): String { urls += url; this.body = body; return reply(url) }
    }

    @Test fun `Wikipedias platser får sitt slag, och orter, socknar och sjöar sållas bort`() {
        val cases = mapOf(
            ("Styggforsen" to "vattenfall i Rättviks kommun") to SightKind.WATERFALL,
            ("Storstupets naturreservat" to "naturreservat i Orsa kommun") to SightKind.NATURE,
            ("Rövarbergsgrottan" to null) to SightKind.CAVE,
            ("Skinnaränget" to "naturreservat i Dalarnas län") to SightKind.NATURE,
            ("Orsa kyrka" to "kyrkobyggnad i Orsa kommun") to SightKind.ATTRACTION,
            ("Mora Bryggeri" to null) to SightKind.ATTRACTION,
            ("Älvdalens skjutfält" to "militärt övningsfält") to SightKind.MILITARY,
            ("Orsa kommun" to "kommun i Dalarnas län, Sverige") to null,
            ("Orsa socken" to "socken i Dalarna") to null,
            ("Orsasjön" to null) to null,
            ("Stackmora" to "tätort i Orsa kommun, Dalarnas län") to null,
            ("Jonas Gifting" to "svensk korpral och indelt soldat") to null,
            ("Maggås" to null) to null,
            ("Våmhus församling" to "historisk församling i Västerås stift") to null,
        )
        cases.forEach { (place, kind) -> assertEquals(place.first, kind, Wikipedia.kindOf(place.first, place.second)) }
    }

    @Test fun `platssökningen läser rubrik, läge och beskrivning`() {
        val http = Canned {
            """{"query":{"pages":[
              {"pageid":770531,"title":"Styggforsen","description":"vattenfall i Rättviks kommun","coordinates":[{"lat":60.99,"lon":15.20,"primary":true}]},
              {"pageid":2,"title":"Orsa kommun","description":"kommun i Dalarnas län, Sverige","coordinates":[{"lat":61.1,"lon":14.6}]},
              {"pageid":3,"title":"Storstupet (ravin)","coordinates":[{"lat":61.3,"lon":14.3}]},
              {"pageid":4,"title":"Rövarbergsgrottan"}
            ]}}"""
        }
        val found = Wikipedia(http).nearby(61.0, 15.0)
        assertEquals(listOf("Styggforsen", "Storstupet"), found.map { it.name })
        assertEquals(Sight("wikipedia/770531", "Styggforsen", SightKind.WATERFALL, 60.99, 15.20, "sv:Styggforsen", "vattenfall i Rättviks kommun", null), found[0])
        assertEquals("sv:Storstupet (ravin)", found[1].wikipedia)
        assertTrue("ggscoord=61.0%7C15.0" in http.urls.single() && "ggsradius=10000" in http.urls.single())
    }

    @Test fun `artikelns inledning och bild hämtas ur taggen från OpenStreetMap`() {
        val http = Canned { """{"title":"Styggforsen","extract":"Styggforsen är ett vattenfall.","thumbnail":{"source":"https://upload.wikimedia.org/x.jpg","width":330}}""" }
        assertEquals(WikiSummary("Styggforsen", "Styggforsen är ett vattenfall.", "https://upload.wikimedia.org/x.jpg"), Wikipedia(http).summary("sv:Styggforsen"))
        assertEquals("https://sv.wikipedia.org/api/rest_v1/page/summary/Styggforsen", http.urls.single())
        assertNull(Wikipedia(Canned { "<html>" }).summary("sv:Styggforsen"))
        assertNull(Wikipedia(Canned { throw OnlineException(404, "saknas") }).summary("en:Missing page"))
    }

    @Test fun `OpenStreetMaps fynd får slag efter taggarna, och samma plats räknas en gång`() {
        val body = """{"elements":[
          {"type":"node","id":1,"lat":61.30,"lon":14.30,"tags":{"natural":"cave_entrance","name":"Rövarbergsgrottan"}},
          {"type":"way","id":2,"center":{"lat":60.99,"lon":15.20},"tags":{"waterway":"waterfall","name":"Styggforsen","wikipedia":"sv:Styggforsen","ele":"230"}},
          {"type":"node","id":3,"lat":60.9901,"lon":15.2001,"tags":{"waterway":"waterfall","name":"Styggforsen"}},
          {"type":"node","id":4,"lat":61.1,"lon":14.5,"tags":{"military":"bunker"}},
          {"type":"node","id":5,"lat":61.2,"lon":14.6,"tags":{"man_made":"tower","tower:type":"observation","name":"Pilkalampinoppi"}},
          {"type":"node","id":6,"lat":61.2,"lon":14.7,"tags":{"historic":"ruins","name":"Gamla hyttan","description":"Rester av en hytta."}},
          {"type":"node","id":7,"lat":61.2,"lon":14.8,"tags":{"amenity":"cafe","name":"Fiket"}},
          {"type":"node","id":8,"tags":{"natural":"cave_entrance"}}
        ]}"""
        val http = Canned { body }
        val found = Overpass(http).sights(listOf(61.12 to 14.62, 60.9 to 15.1), 8_000, SightKind.entries.toSet())
        assertEquals(listOf("Rövarbergsgrottan", "Styggforsen", "Namnlös försvarsanläggning", "Pilkalampinoppi", "Gamla hyttan"), found.map { it.name })
        assertEquals(listOf(SightKind.CAVE, SightKind.WATERFALL, SightKind.MILITARY, SightKind.VIEW, SightKind.RUIN), found.map { it.kind })
        assertEquals("way/2", found[1].id)
        assertEquals("230", found[1].elevation)
        // Den med artikel och ovanligast slag räknas som intressantast; en namnlös bunker hamnar under de namngivna.
        assertTrue(found[1].interest > found[4].interest && found[0].interest > found[2].interest)
        val query = java.net.URLDecoder.decode(http.body!!.removePrefix("data="), Charsets.UTF_8)
        assertTrue(query.startsWith("[out:json]") && "(around:8000,61.12000,14.62000,60.90000,15.10000)" in query)
        // Bara valda slag frågas efter.
        val caves = Overpass.query(listOf(61.0 to 14.0), 5_000, setOf(SightKind.CAVE))
        assertTrue("cave_entrance" in caves && "waterfall" !in caves)
        assertTrue(Overpass(Canned { error("ska inte anropas") }).sights(emptyList(), 5_000, SightKind.entries.toSet()).isEmpty())
    }

    @Test fun `punkterna längs vägen täcker hela sträckan utan att bli för många`() {
        val line = (0..100).map { 61.0 + it * 0.01 to 14.6 }   // ungefär 111 km rakt norrut
        val samples = RouteSamples.along(line)
        assertTrue(samples.size in 8..10)
        assertEquals(line.first(), samples.first())
        assertEquals(line.last(), samples.last())
        // Med tio kilometers sökradie runt varje punkt får det aldrig vara mer än två mil mellan dem.
        assertTrue(samples.zipWithNext { a, b -> RouteSamples.meters(a, b) }.all { it <= 16_000.0 })
        assertEquals(listOf(61.0 to 14.6), RouteSamples.along(listOf(61.0 to 14.6)))
        assertEquals(1_112.0, RouteSamples.meters(61.0 to 14.6, 61.01 to 14.6), 2.0)
    }
}
