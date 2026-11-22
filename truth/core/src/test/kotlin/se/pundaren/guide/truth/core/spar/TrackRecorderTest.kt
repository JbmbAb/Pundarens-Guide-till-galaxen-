package se.pundaren.guide.truth.core.spar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.core.geo.Geo
import java.time.Instant

/** Eget spår och vägen tillbaka längs det (etapp 5, hemväg). */
class TrackRecorderTest {
    private val start = GeoPoint(61.1201175, 14.6222536) // Orsa
    private val t0 = Instant.parse("2026-11-21T09:00:00Z")

    /** En punkt [meters] norr om starten, [seconds] efter starttiden. */
    private fun north(meters: Double) = Geo.destination(start, 0.0, meters)
    private fun at(seconds: Long) = t0.plusSeconds(seconds)

    @Test fun `första positionen sparas och nästa först efter minsta avståndet`() {
        val track = TrackRecorder()
        assertTrue(track.add(start, 5.0, at(0)))
        assertFalse("10 m är under 15 m", track.add(north(10.0), 5.0, at(10)))
        assertTrue(track.add(north(20.0), 5.0, at(20)))
        assertEquals(2, track.points.size)
        assertEquals(20.0, track.lengthMeters, 0.5)
        assertEquals(t0, track.startedUtc)
    }

    @Test fun `en osäker position sparas inte och räknas inte som rörelse`() {
        val track = TrackRecorder()
        track.add(start, 5.0, at(0))
        assertFalse("sämre än 50 m sparas aldrig", track.add(north(200.0), 80.0, at(60)))
        assertFalse("30 m bort med 40 m osäkerhet kan vara stillastående", track.add(north(30.0), 40.0, at(60)))
        assertTrue("45 m bort med 40 m osäkerhet är en förflyttning", track.add(north(45.0), 40.0, at(90)))
        assertEquals(2, track.points.size)
    }

    @Test fun `ett hopp i positionen kommer inte med i spåret`() {
        val track = TrackRecorder()
        track.add(start, 5.0, at(0))
        assertFalse("5 km på 10 s är inget fordon", track.add(north(5_000.0), 5.0, at(10)))
        assertFalse("äldre än senaste punkten", track.add(north(50.0), 5.0, at(-5)))
        assertTrue(track.add(north(50.0), 5.0, at(30)))
        assertEquals(50.0, track.lengthMeters, 0.5)
    }

    @Test fun `spårets längd är summan av sträckorna`() {
        val track = TrackRecorder()
        for (i in 0..100) track.add(north(i * 25.0), 5.0, at(i * 20L))
        assertEquals(101, track.points.size)
        assertEquals(2_500.0, track.lengthMeters, 1.0)
    }

    @Test fun `ett långt spår glesas ut men behåller början och slutet`() {
        val track = TrackRecorder(TrackRecorder.Config(maxPoints = 100))
        for (i in 0..1_000) track.add(north(i * 20.0), 5.0, at(i * 15L))
        assertTrue("högst 100 punkter, var ${track.points.size}", track.points.size <= 100)
        assertTrue("början är vägen hem", Geo.distanceMeters(start, track.points.first().point) < 0.01)
        val end = track.points.last().point
        assertTrue("slutet ligger nära den senaste positionen", Geo.distanceMeters(end, north(20_000.0)) < 700.0)
        // Längden längs en rak linje ändras inte av utglesningen.
        assertEquals(Geo.distanceMeters(start, end), track.lengthMeters, 5.0)
    }

    @Test fun `vägen tillbaka går baklänges från närmaste spårpunkt till början`() {
        val track = TrackRecorder()
        for (i in 0..40) track.add(north(i * 50.0), 5.0, at(i * 40L)) // 2 km rakt norrut
        val here = Geo.destination(north(1_500.0), 90.0, 120.0) // 120 m öster om spåret, 1,5 km upp
        val back = track.wayBack(here)!!
        assertTrue("vägen tillbaka slutar i spårets början", Geo.distanceMeters(start, back.line.last()) < 0.01)
        assertEquals(1_500.0, back.lengthMeters, 30.0)
        assertEquals(120.0, back.metersToTrack!!, 5.0)
        assertEquals("spåret ligger väster om positionen", "väster", Geo.compassSv(back.bearingToTrack!!))
    }

    @Test fun `utan position börjar vägen tillbaka i spårets senaste punkt`() {
        val track = TrackRecorder()
        for (i in 0..10) track.add(north(i * 100.0), 5.0, at(i * 60L))
        val back = track.wayBack(null)!!
        assertEquals(11, back.line.size)
        assertEquals(north(1_000.0), back.line.first())
        assertEquals(1_000.0, back.lengthMeters, 2.0)
        assertNull(back.metersToTrack)
    }

    @Test fun `ett spår med en enda punkt har ingen väg tillbaka`() {
        val track = TrackRecorder()
        assertNull(track.wayBack(start))
        track.add(start, 5.0, at(0))
        assertNull(track.wayBack(start))
        track.add(north(100.0), 5.0, at(60))
        assertNotNull(track.wayBack(start))
    }

    @Test fun `nollställt spår är tomt och börjar om`() {
        val track = TrackRecorder()
        for (i in 0..10) track.add(north(i * 100.0), 5.0, at(i * 60L))
        track.clear()
        assertEquals(0, track.points.size)
        assertEquals(0.0, track.lengthMeters, 0.0)
        assertNull(track.startedUtc)
        assertEquals(TrackSnapshot.EMPTY, track.snapshot(simulated = false))
        assertTrue(track.add(north(5.0), 5.0, at(900)))
    }

    @Test fun `ögonblicksbilden säger om spåret är simulerat`() {
        val track = TrackRecorder()
        track.add(start, 5.0, at(0)); track.add(north(100.0), 5.0, at(60))
        val shot = track.snapshot(simulated = true)
        assertTrue(shot.simulated)
        assertEquals(2, shot.line.size)
        assertEquals(100.0, shot.lengthMeters, 0.5)
        assertEquals(t0, shot.startedUtc)
    }
}
