package se.pundaren.guide.truth.core.handduk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.truth.api.Arrived
import se.pundaren.guide.truth.api.EmergencyActivated
import se.pundaren.guide.truth.api.EmergencySource
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.ManeuverCompleted
import se.pundaren.guide.truth.api.OffRoute
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.api.RouteStarted
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.core.drift.ImprobabilityDrive
import se.pundaren.guide.truth.core.platser.Place
import se.pundaren.guide.truth.core.platser.PlaceIndex
import se.pundaren.guide.truth.core.platser.PlaceKind
import se.pundaren.guide.truth.core.protokoll.ExpeditionProtocol
import se.pundaren.guide.truth.core.protokoll.Perspective
import se.pundaren.guide.truth.core.protokoll.ProtocolLayer
import se.pundaren.guide.truth.core.snacks.OpenState
import se.pundaren.guide.truth.core.snacks.OpeningHours
import se.pundaren.guide.truth.core.snacks.SnackFinder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Etapp 5: varje skämt ska bära last. Testerna gäller de sanna värdena under skämten (G6). */
class LoadBearingJokesTest {
    private val orsa = GeoPoint(61.1201175, 14.6222536)
    private val gronklitt = GeoPoint(61.2077132, 14.5377551)
    private val mora = GeoPoint(61.0086787, 14.5442519)
    private val stockholm = ZoneId.of("Europe/Stockholm")
    private val bbox = listOf(12.036, 60.645, 16.652, 62.785)

    private fun place(id: String, name: String, kind: PlaceKind, sub: String, p: GeoPoint, hours: String? = null) = Place(id, name, kind, sub, p, hours)

    // ---------------- Handduken ----------------

    @Test fun `handduken är säkrad när alla fem punkter är godkända`() {
        val check = DepartureCheck.build(
            batteryPercent = 80, charging = false, packageBbox = bbox, origin = orsa, destination = gronklitt, home = orsa,
            arrivalUtc = Instant.parse("2026-11-21T10:30:00Z"), positionQuality = PositionQuality.GOOD, zone = stockholm,
        )
        assertTrue(check.secured)
        assertEquals("Handduken är säkrad.", check.headline)
        assertEquals(listOf("batteri", "kartpaket", "hem", "dagsljus", "position"), check.items.map { it.id })
        assertEquals("80 %", check.items[0].value)
        // Solen går ned cirka 15.13 i Orsa den 21 november; ankomst 11.30 ger drygt tre timmar.
        assertTrue(check.items[3].value, Regex("""3 h \d+ min dagsljus kvar vid ankomst 11\.30""").matches(check.items[3].value))
    }

    @Test fun `varje brist syns med sitt sanna värde`() {
        val check = DepartureCheck.build(
            batteryPercent = 16, charging = false, packageBbox = bbox, origin = orsa, destination = GeoPoint(59.33, 18.07), home = null,
            arrivalUtc = Instant.parse("2026-11-21T16:00:00Z"), positionQuality = PositionQuality.LOST, zone = stockholm,
        )
        assertFalse(check.secured)
        assertEquals("Handduken är inte säkrad: 5 av 5 punkter återstår.", check.headline)
        assertEquals(List(5) { CheckStatus.WARNING }, check.items.map { it.status })
        assertEquals("16 %, under 30 %", check.items[0].value)
        assertEquals("Målet ligger utanför kartpaketet", check.items[1].value)
        assertEquals("Mörkt vid ankomst 17.00", check.items[3].value)
        assertEquals("Ingen aktuell position", check.items[4].value)
    }

    @Test fun `okänt visas som okänt och gör inte handduken säkrad`() {
        val check = DepartureCheck.build(50, false, packageBbox = null, origin = null, destination = null, home = orsa, arrivalUtc = null, positionQuality = PositionQuality.GOOD, zone = stockholm)
        assertFalse(check.secured)
        assertEquals(CheckStatus.UNKNOWN, check.items.single { it.id == "kartpaket" }.status)
        assertEquals(CheckStatus.UNKNOWN, check.items.single { it.id == "dagsljus" }.status)
        assertTrue(check.items.filter { it.status == CheckStatus.UNKNOWN }.all { it.value.startsWith("Okänd") })
        // Laddning räcker för batteripunkten även under tröskeln.
        assertEquals(CheckStatus.OK, DepartureCheck.build(10, true, bbox, orsa, mora, orsa, null, PositionQuality.GOOD, stockholm).items[0].status)
    }

    // ---------------- Osannolikhetsdriften ----------------

    private val index = PlaceIndex(
        listOf(
            place("a", "Orsa", PlaceKind.SETTLEMENT, "town", orsa),
            place("b", "Grönklitt", PlaceKind.SETTLEMENT, "hamlet", gronklitt),
            place("c", "Mora", PlaceKind.SETTLEMENT, "town", mora),
            place("d", "Gården", PlaceKind.SETTLEMENT, "isolated_dwelling", GeoPoint(61.15, 14.60)),
            place("e", "Kaféet", PlaceKind.FOOD, "cafe", GeoPoint(61.05, 14.57), "Mo-Fr 08:00-17:00; Sa 10:00-14:00; Su off"),
            place("f", "Macken", PlaceKind.FUEL, "fuel", GeoPoint(61.01, 14.55), "24/7"),
            place("g", "Sveg", PlaceKind.SETTLEMENT, "town", GeoPoint(62.0360709, 14.3598392)),
        ),
    )

    @Test fun `kandidaterna ligger inom rimligt avstånd och urvalet är seedat`() {
        val near = ImprobabilityDrive.candidates(index, orsa, maxSeconds = 1_200.0, mode = TravelMode.PASSENGER, seed = 1)
        assertEquals(setOf("b", "c", "e"), near.map { it.id }.toSet()) // Sveg för långt, gården inte en ort, macken inget mål
        assertTrue(ImprobabilityDrive.candidates(index, orsa, 1_200.0, TravelMode.FOOT, seed = 1).isEmpty()) // 20 min till fots når ingen
        val many = PlaceIndex((0 until 200).map { place("p%03d".format(it), "By $it", PlaceKind.SETTLEMENT, "village", GeoPoint(61.12 + it * 0.0005, 14.62 + 0.05)) })
        val a = ImprobabilityDrive.candidates(many, orsa, 1_200.0, TravelMode.PASSENGER, seed = 42, limit = 10)
        assertEquals(10, a.size)
        assertEquals(a, ImprobabilityDrive.candidates(many, orsa, 1_200.0, TravelMode.PASSENGER, seed = 42, limit = 10))
        assertTrue(a != ImprobabilityDrive.candidates(many, orsa, 1_200.0, TravelMode.PASSENGER, seed = 43, limit = 10))
    }

    @Test fun `målet lottas bara bland dem som verkligen nås i tid`() {
        val candidates = index.places.filter { it.id in setOf("b", "c", "e") }
        val pick = ImprobabilityDrive.pick(candidates, listOf(960.0, 912.0, null), maxSeconds = 1_000.0, seed = 7)!!
        assertTrue(pick.place.id in setOf("b", "c"))
        assertEquals(2, pick.reachable); assertEquals(3, pick.considered)
        assertEquals(pick, ImprobabilityDrive.pick(candidates, listOf(960.0, 912.0, null), 1_000.0, seed = 7))
        // Restiden är ruttmotorns, inte fågelvägens: ett mål som tar för lång tid väljs aldrig.
        repeat(50) { seed -> assertEquals("c", ImprobabilityDrive.pick(candidates, listOf(1_500.0, 912.0, 2_000.0), 1_000.0, seed.toLong())!!.place.id) }
        assertNull(ImprobabilityDrive.pick(candidates, listOf(null, 5_000.0, null), 1_000.0, seed = 1))
    }

    // ---------------- Snacks ----------------

    @Test fun `öppettider läses försiktigt och allt oklart blir okänt`() {
        val saturdayNoon = LocalDateTime.of(2026, 11, 21, 12, 0)   // en lördag
        val spec = "Mo-Fr 08:00-17:00; Sa 10:00-14:00; Su off"
        assertEquals(OpenState.OPEN, OpeningHours.stateAt(spec, saturdayNoon))
        assertEquals(OpenState.CLOSED, OpeningHours.stateAt(spec, saturdayNoon.withHour(15)))
        assertEquals(OpenState.CLOSED, OpeningHours.stateAt(spec, saturdayNoon.plusDays(1)))
        assertEquals(OpenState.OPEN, OpeningHours.stateAt(spec, saturdayNoon.plusDays(2).withHour(8)))
        assertEquals(OpenState.OPEN, OpeningHours.stateAt("24/7", saturdayNoon))
        assertEquals(OpenState.OPEN, OpeningHours.stateAt("11:00-21:00", saturdayNoon))
        assertEquals(OpenState.OPEN, OpeningHours.stateAt("Mo,We,Sa 09:00-12:00,13:00-18:00", saturdayNoon.withHour(13)))
        assertEquals(OpenState.CLOSED, OpeningHours.stateAt("Mo-Fr 08:00-17:00", saturdayNoon))
        for (unclear in listOf(null, "", "Mo-Fr 08:00-17:00; PH off", "sunrise-sunset", "Mo-Su 18:00-02:00", "May-Sep: Mo-Su 10:00-18:00", "\"efter överenskommelse\"", "Mo-Fr 8-17")) {
            assertEquals("$unclear", OpenState.UNKNOWN, OpeningHours.stateAt(unclear, saturdayNoon))
        }
    }

    @Test fun `snacks rangordnas efter omväg och säger okänd när öppettider saknas`() {
        val now = LocalDateTime.of(2026, 11, 21, 11, 30)
        val candidates = SnackFinder.candidates(index, orsa, mora, maxDetourSeconds = 600.0, mode = TravelMode.PASSENGER)
        assertEquals(listOf("f", "e"), candidates.map { it.id }.sortedDescending())
        val ordered = listOf(index.places.single { it.id == "e" }, index.places.single { it.id == "f" })
        val stops = SnackFinder.rank(ordered, toPlace = listOf(500.0, 880.0), placeToDestination = listOf(520.0, 60.0), directSeconds = 912.0, maxDetourSeconds = 600.0, nowLocal = now)
        assertEquals(listOf("f", "e"), stops.map { it.place.id })          // macken ger 28 s omväg, kaféet 108 s
        assertEquals(28.0, stops[0].detourSeconds!!, 1e-9); assertEquals(108.0, stops[1].detourSeconds!!, 1e-9)
        assertEquals(OpenState.OPEN, stops[0].openAtArrival); assertEquals(OpenState.OPEN, stops[1].openAtArrival)
        assertEquals("Snacks: 2 av 2 matställen med kända öppettider har öppet vid ankomst.", SnackFinder.summary(stops))

        val unknown = stops.map { it.copy(openAtArrival = OpenState.UNKNOWN) }
        assertEquals("Sannolikhet för snacks: okänd. 2 matställen inom omvägen, öppettider saknas.", SnackFinder.summary(unknown))
        assertEquals("Sannolikhet för snacks: inga matställen inom omvägen.", SnackFinder.summary(emptyList()))
        // För lång omväg eller ingen väg: platsen faller bort.
        assertTrue(SnackFinder.rank(ordered, listOf(500.0, null), listOf(2_000.0, 60.0), 912.0, 600.0, now).isEmpty())
    }

    // ---------------- Protokollet och perspektivet ----------------

    @Test fun `protokollet håller isär sant och satir och skrivs i myndighetsform`() {
        val t0 = Instant.parse("2026-11-21T10:00:00Z")
        val protocol = ExpeditionProtocol("X-1")
        protocol.truth(RouteStarted("E1", 0, t0, "X-1", "Grönklitt", 15_730, 960, TravelMode.PASSENGER))
        protocol.truth(ManeuverCompleted("E2", 5_000, t0.plusSeconds(5), "X-1", "M1"))
        protocol.satire(t0.plusSeconds(8), "Verkligheten är vackrare än de dunkla fantasierna.", "C-006")
        protocol.truth(OffRoute("E3", 60_000, t0.plusSeconds(60), "X-1", 250, 0))
        protocol.truth(EmergencyActivated("E4", 70_000, t0.plusSeconds(70), "X-1", EmergencySource.USER))
        protocol.truth(Arrived("E5", 962_000, t0.plusSeconds(962), "X-1", "Grönklitt", 962, 15_730))
        assertEquals(listOf(ProtocolLayer.TRUTH, ProtocolLayer.SATIRE, ProtocolLayer.TRUTH, ProtocolLayer.TRUTH, ProtocolLayer.TRUTH), protocol.entries.map { it.layer })
        val text = protocol.render(stockholm)
        assertTrue(text.startsWith("EXPEDITIONSPROTOKOLL X-1\nUpprättat 2026-11-21. Tider i Europe/Stockholm."))
        assertTrue(text.contains("11.00.00 [SANT]   Expeditionen inleddes mot Grönklitt. Fastställd sträcka 16 km, beräknad restid 16 min, färdsätt: passagerare."))
        assertTrue(text.contains("11.00.08 [GUIDEN] «Verkligheten är vackrare än de dunkla fantasierna.» (rad C-006)"))
        assertTrue(text.contains("[SANT]   Avvikelse från fastställd rutt konstaterades: 250 m."))
        assertTrue(text.contains("[SANT]   Nödläge aktiverades av användaren. All satir undertrycktes."))
        assertTrue(text.contains("[SANT]   Expeditionen avslutades vid Grönklitt efter 16 min och 16 km. Antal genomförda manövrer: 1."))
        // Ingen rad saknar märkning.
        text.lines().drop(4).filter { it.isNotBlank() }.forEach { assertTrue(it, it.contains("[SANT]") || it.contains("[GUIDEN]")) }
    }

    @Test fun `perspektivvyn räknar andelen av universums ålder på riktigt`() {
        // 13,787 miljarder år är cirka 4,351 × 10¹⁷ sekunder.
        assertEquals(4.351e17, Perspective.UNIVERSE_AGE_SECONDS, 1e15)
        assertEquals(960.0 / Perspective.UNIVERSE_AGE_SECONDS, Perspective.shareOfUniverseAge(960.0), 0.0)
        assertEquals("2,2 × 10⁻¹⁵", Perspective.scientific(Perspective.shareOfUniverseAge(960.0)))
        assertEquals("16 minuter är 2,2 × 10⁻¹⁵ av universums ålder.", Perspective.describe(960.0))
        assertEquals("1,0 × 10⁰", Perspective.scientific(1.0)); assertEquals("3,2 × 10³", Perspective.scientific(3_200.0))
    }
}
