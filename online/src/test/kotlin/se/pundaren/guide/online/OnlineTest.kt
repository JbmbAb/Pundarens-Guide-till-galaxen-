package se.pundaren.guide.online

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Nätet finns inte i testerna: porten svarar med inspelade svar i den form dokumentationen visar. */
private class FakeHttp(private val reply: (String) -> String) : HttpPort {
    val urls = mutableListOf<String>()
    var headers: Map<String, String> = emptyMap()
    var body: String? = null
    override fun get(url: String, headers: Map<String, String>): String { urls += url; return reply(url) }
    override fun post(url: String, headers: Map<String, String>, body: String): String {
        urls += url; this.headers = headers; this.body = body
        return reply(url)
    }
}

class OnlineTest {
    private val tripJson = """
        {"Trip":[{"LegList":{"Leg":[
          {"Origin":{"name":"Orsa busstation","type":"ST","extId":"740021511","time":"07:10:00","date":"2026-11-28"},
           "Destination":{"name":"Mora resecentrum","type":"ST","extId":"740000205","time":"07:35:00","date":"2026-11-28"},
           "Product":{"name":"Länstrafik - Buss 141","num":"141"},
           "idx":"0","name":"Länstrafik - Buss 141","type":"JNY","direction":"Mora resecentrum"},
          {"Origin":{"name":"Mora resecentrum","time":"07:35:00","date":"2026-11-28"},
           "Destination":{"name":"Mora station","time":"07:41:00","date":"2026-11-28"},
           "type":"WALK","dist":350},
          {"Origin":{"name":"Mora station","time":"07:50:00","date":"2026-11-28"},
           "Destination":{"name":"Borlänge Centralstation","time":"09:05:00","date":"2026-11-28"},
           "name":"Regional Tåg 43","type":"JNY","direction":"Stockholm Centralstation"}
        ]}},
        {"LegList":{"Leg":{"Origin":{"name":"Orsa busstation","time":"08:10:00","date":"2026-11-28"},
           "Destination":{"name":"Mora resecentrum","time":"08:35:00","date":"2026-11-28"},
           "name":"Länstrafik - Buss 141","type":"JNY"}}}
        ],"scrF":"x"}
    """.trimIndent()

    @Test fun `en resa läses med byten, promenad och tider`() {
        val http = FakeHttp { tripJson }
        val trips = ResRobot("NYCKEL", http).trips(TransitPlace.Stop("740021511"), TransitPlace.Point(60.4858, 15.4372))
        assertEquals(2, trips.size)
        val first = trips[0]
        assertEquals(3, first.legs.size)
        assertEquals(1, first.changes)
        assertEquals(LocalDateTime.of(2026, 11, 28, 7, 10), first.departs)
        assertEquals(LocalDateTime.of(2026, 11, 28, 9, 5), first.arrives)
        assertEquals("Länstrafik - Buss 141", first.legs[0].line)
        assertTrue(first.legs[1].walk)
        assertEquals(350, first.legs[1].distanceMeters)
        // Ett ensamt delsträcksobjekt i stället för en lista ska också gå att läsa.
        assertEquals(1, trips[1].legs.size)
        assertEquals(0, trips[1].changes)
        val url = http.urls.single()
        assertTrue(url.startsWith("https://api.resrobot.se/v2.1/trip?"))
        assertTrue("originId=740021511" in url && "destCoordLat=60.4858" in url && "destCoordLong=15.4372" in url && "accessId=NYCKEL" in url)
    }

    @Test fun `hållplatser läses i båda formerna och sökningen är oskarp`() {
        val direct = """{"StopLocation":[{"id":"A=1@O=Orsa busstation@","extId":"740021511","name":"Orsa busstation","lon":14.618,"lat":61.120,"weight":3100,"products":136}]}"""
        val wrapped = """{"stopLocationOrCoordLocation":[{"StopLocation":{"extId":"740021511","name":"Orsa busstation","lon":"14.618","lat":"61.120","dist":212}}]}"""
        val http = FakeHttp { url -> if ("location.name" in url) direct else wrapped }
        val robot = ResRobot("NYCKEL", http)
        val found = robot.findStops("Orsa buss").single()
        assertEquals(TransitStop("740021511", "Orsa busstation", 61.120, 14.618), found)
        val near = robot.nearbyStops(61.12, 14.62).single()
        assertEquals(212, near.distanceMeters)
        assertEquals(61.120, near.lat!!, 1e-9)
        assertTrue("input=Orsa%20buss%3F" in http.urls[0])
        assertTrue("originCoordLat=61.12&originCoordLong=14.62" in http.urls[1])
        assertTrue(robot.findStops("   ").isEmpty())
    }

    @Test fun `avgångar läses med försening, inställd tur och läge`() {
        val body = """
            {"timestamp":"2026-11-28T07:00:00","query":{"queryTime":"2026-11-28T07:00:00","query":"740021511"},
             "stops":[{"id":"740021511","name":"Orsa busstation"}],
             "departures":[
              {"scheduled":"2026-11-28T07:10:00","realtime":"2026-11-28T07:14:00","delay":240,"canceled":false,
               "route":{"name":null,"designation":"141","transport_mode":"BUS","direction":"Mora resecentrum","destination":{"id":"740000205","name":"Mora resecentrum"}},
               "scheduled_platform":{"id":"1","designation":"B"},"realtime_platform":{"id":"1","designation":"C"},"is_realtime":true},
              {"scheduled":"2026-11-28T07:40:00","realtime":"2026-11-28T07:40:00","delay":0,"canceled":true,
               "route":{"name":"Buss 104","designation":"","transport_mode":"BUS","direction":"","destination":{"name":"Skattungbyn"}},
               "scheduled_platform":null,"realtime_platform":null,"is_realtime":false},
              {"realtime":"trasig rad utan tidtabellstid"}
             ]}
        """.trimIndent()
        val http = FakeHttp { body }
        val departures = TrafiklabRealtime("RT", http).departures("740021511")
        assertEquals(2, departures.size)
        assertEquals(Departure(LocalDateTime.of(2026, 11, 28, 7, 10), LocalDateTime.of(2026, 11, 28, 7, 14), 240, false, "141", "Mora resecentrum", "BUS", "C", true), departures[0])
        assertTrue(departures[1].canceled)
        assertEquals("Buss 104", departures[1].line)
        assertEquals("Skattungbyn", departures[1].destination)
        assertNull(departures[1].platform)
        assertEquals("https://realtime-api.trafiklab.se/v1/departures/740021511?key=RT", http.urls.single())
    }

    @Test fun `Realtime slår upp hållplatser själv, utan ResRobot`() {
        val body = """{"timestamp":"2026-11-28T07:00:00","query":{"queryTime":"2026-11-28T07:00:00","query":"Mora"},
          "stop_groups":[
            {"id":"740000205","name":"Mora resecentrum","area_type":"RIKSHALLPLATS","average_daily_stop_times":120.5,"transport_modes":["BUS","TRAIN"],
             "stops":[{"id":"9021","name":"Mora resecentrum","lat":61.0049,"lon":14.5453}]},
            {"id":"740000206","name":"Mora strand","stops":[]},
            {"name":"saknar id"}
          ]}"""
        val http = FakeHttp { body }
        val stops = TrafiklabRealtime("RT", http).findStops(" Mora resecentrum ")
        assertEquals(listOf(TransitStop("740000205", "Mora resecentrum", 61.0049, 14.5453), TransitStop("740000206", "Mora strand", null, null)), stops)
        assertEquals("https://realtime-api.trafiklab.se/v1/stops/name/Mora%20resecentrum?key=RT", http.urls.single())
        assertTrue(TrafiklabRealtime("RT", http).findStops("  ").isEmpty())
    }

    @Test fun `ett svar som inte är json ger ett fel utan adress`() {
        val http = FakeHttp { "<html>Service unavailable</html>" }
        val error = runCatching { ResRobot("HEMLIG-NYCKEL", http).findStops("Orsa") }.exceptionOrNull() as OnlineException
        assertFalse("HEMLIG-NYCKEL" in error.message.orEmpty())
        assertFalse("https" in error.message.orEmpty())
    }

    private val options = listOf(
        ClaudeChooser.Option("RO-06", "RIKARD HAR FÖRNEKAT", "Rikard har förnekat inblandning i tre händelser."),
        ClaudeChooser.Option("NE-04", "TILLFÄLLENA STRYKS", "De strukna tillfällena har rätt att överklaga."),
        ClaudeChooser.Option("OV-01", "UTTALANDET HAR DIARIEFÖRTS", "Uttalandet har diarieförts."),
    )

    @Test fun `Claude får listan och meningen, och bara ett id ur listan godtas`() {
        val http = FakeHttp { """{"content":[{"type":"thinking","thinking":"OV-01 vore trist"},{"type":"text","text":"RO-06"}],"stop_reason":"end_turn"}""" }
        val chosen = ClaudeChooser("sk-ant-HEMLIG", http).choose("Jag var inte ens där", options, recent = listOf("NE-04"))
        assertEquals("RO-06", chosen)
        assertEquals("https://api.anthropic.com/v1/messages", http.urls.single())
        assertEquals("sk-ant-HEMLIG", http.headers["x-api-key"])
        assertEquals("2026-10-21", http.headers["anthropic-version"])
        val sent = Json.parseToJsonElement(http.body!!).jsonObject
        assertEquals("claude-opus-5-5", sent["model"]!!.jsonPrimitive.content)
        assertEquals("low", sent["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertFalse("nyckeln får aldrig ligga i frågans kropp", "sk-ant-HEMLIG" in http.body!!)
        val system = sent["system"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(options.all { "${it.id} | ${it.headline} | ${it.opening}" in system })
        val question = sent["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonPrimitive.content
        assertTrue("<mening>Jag var inte ens där</mening>" in question)
        assertTrue("NE-04" in question)
    }

    @Test fun `ett svar utan giltigt id, en vägran eller en tom mening ger inget val`() {
        fun answer(text: String, stop: String = "end_turn") =
            ClaudeChooser("k", FakeHttp { """{"content":[{"type":"text","text":"$text"}],"stop_reason":"$stop"}""" }).choose("Något hände", options)
        assertNull(answer("Jag väljer XX-99."))
        assertNull(answer("RO-06", stop = "refusal"))
        assertEquals("NE-04", answer("Beslut: NE-04."))
        // Ett id som bara är en del av ett annat ord räknas inte.
        assertNull(answer("RO-066"))
        val silent = FakeHttp { error("ska inte anropas") }
        assertNull(ClaudeChooser("k", silent).choose("   ", options))
        assertTrue(silent.urls.isEmpty())
    }

    @Test fun `bara https godtas av den riktiga porten`() {
        val error = runCatching { JdkHttps().get("http://api.resrobot.se/v2.1/trip?accessId=HEMLIG") }.exceptionOrNull() as OnlineException
        assertFalse("HEMLIG" in error.message.orEmpty())
    }
}
