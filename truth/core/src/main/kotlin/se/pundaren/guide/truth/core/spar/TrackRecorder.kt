package se.pundaren.guide.truth.core.spar

import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.core.geo.Geo
import java.time.Instant

/** En punkt i det egna spåret (04, `track_point`). */
data class TrackPoint(val point: GeoPoint, val accuracyMeters: Double, val atUtc: Instant)

/** Spåret som skärmen ritar. Bara värden. */
data class TrackSnapshot(
    val line: List<GeoPoint>,
    val lengthMeters: Double,
    val startedUtc: Instant?,
    /** Sant när spåret kommer från en provtur (simulerad position). Det sparas då aldrig. */
    val simulated: Boolean,
) {
    companion object {
        val EMPTY = TrackSnapshot(emptyList(), 0.0, null, false)
    }
}

/** Vägen tillbaka längs det egna spåret, från den spårpunkt som ligger närmast. */
data class WayBack(
    /** Spåret baklänges: från närmaste spårpunkt till spårets början. */
    val line: List<GeoPoint>,
    val lengthMeters: Double,
    /** Fågelavståndet från nuvarande position till närmaste spårpunkt, eller null utan position. */
    val metersToTrack: Double?,
    /** Riktningen dit i grader medurs från norr, eller null utan position. */
    val bearingToTrack: Double?,
)

/**
 * Eget spår (02 N-8, byggplanen: "din egen karta, ditt spår, hemvägen" fungerar utan nät).
 * Positionerna enheten faktiskt har haft, så att vägen tillbaka går att följa även där ruttmotorn
 * inte har någon väg.
 *
 * Ren logik utan klocka och utan I/O: samma positioner ger samma spår. En position tas med om den
 * är tillräckligt noggrann, ligger tillräckligt långt från den förra och inte kräver en orimlig fart
 * dit. När spåret blir för långt glesas det ut i stället för att tappa sin början: början är vägen hem.
 */
class TrackRecorder(private val config: Config = Config()) {

    /** Kandidatvärden, inte frysta. */
    data class Config(
        /** Minsta avstånd mellan två sparade punkter. */
        val minDistanceMeters: Double = 15.0,
        /** Sämre noggrannhet än så här sparas inte. */
        val maxAccuracyMeters: Double = 50.0,
        /** Fortare än så här mellan två punkter räknas som ett hopp i positionen, inte som en förflyttning. */
        val maxSpeedMps: Double = 70.0,
        val maxPoints: Int = 5_000,
    )

    private val kept = ArrayList<TrackPoint>()
    private var spacing = config.minDistanceMeters
    private var length = 0.0

    val points: List<TrackPoint> get() = kept.toList()
    val size: Int get() = kept.size
    val lengthMeters: Double get() = length
    val startedUtc: Instant? get() = kept.firstOrNull()?.atUtc
    val lastUtc: Instant? get() = kept.lastOrNull()?.atUtc

    /** Ökar varje gång spåret har glesats ut. Den som sparar spåret kan då skriva om sin kopia. */
    var thinnings: Int = 0
        private set

    fun snapshot(simulated: Boolean): TrackSnapshot = TrackSnapshot(kept.map { it.point }, length, startedUtc, simulated)

    /** Lägger till en position om den hör hemma i spåret. Sant om den sparades. */
    fun add(point: GeoPoint, accuracyMeters: Double, atUtc: Instant): Boolean {
        if (accuracyMeters > config.maxAccuracyMeters) return false
        val last = kept.lastOrNull()
        if (last == null) {
            kept += TrackPoint(point, accuracyMeters, atUtc)
            return true
        }
        val distance = Geo.distanceMeters(last.point, point)
        // En osäker position måste flytta sig mer än sin egen osäkerhet för att räknas som rörelse.
        if (distance < maxOf(spacing, accuracyMeters)) return false
        val seconds = (atUtc.toEpochMilli() - last.atUtc.toEpochMilli()) / 1000.0
        if (seconds < 0.0) return false // äldre än den senaste punkten
        if (seconds > 0.0 && distance / seconds > config.maxSpeedMps) return false
        if (seconds == 0.0 && distance > config.maxSpeedMps) return false
        kept += TrackPoint(point, accuracyMeters, atUtc)
        length += distance
        if (kept.size > config.maxPoints) thin()
        return true
    }

    fun clear() {
        kept.clear()
        length = 0.0
        spacing = config.minDistanceMeters
    }

    /** Behåller varannan punkt samt första och sista, och dubblar avståndet mellan nya punkter. */
    private fun thin() {
        val first = kept.first()
        val last = kept.last()
        val thinned = ArrayList<TrackPoint>(kept.size / 2 + 2)
        for (i in kept.indices) if (i % 2 == 0) thinned += kept[i]
        if (thinned.last() !== last) thinned += last
        if (thinned.first() !== first) thinned.add(0, first)
        kept.clear()
        kept += thinned
        spacing *= 2
        thinnings++
        length = kept.zipWithNext().sumOf { (a, b) -> Geo.distanceMeters(a.point, b.point) }
    }

    /**
     * Vägen tillbaka till spårets början. Utan [from] börjar den i spårets senaste punkt.
     * Null om spåret har färre än två punkter.
     */
    fun wayBack(from: GeoPoint?): WayBack? {
        if (kept.size < 2) return null
        val nearest = if (from == null) kept.lastIndex else kept.indices.minBy { Geo.distanceMeters(from, kept[it].point) }
        val line = kept.subList(0, nearest + 1).map { it.point }.asReversed().toList()
        val back = line.zipWithNext().sumOf { (a, b) -> Geo.distanceMeters(a, b) }
        return WayBack(
            line = line,
            lengthMeters = back,
            metersToTrack = from?.let { Geo.distanceMeters(it, kept[nearest].point) },
            bearingToTrack = from?.let { Geo.bearingDegrees(it, kept[nearest].point) },
        )
    }
}
