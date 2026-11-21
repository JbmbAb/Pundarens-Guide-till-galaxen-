package se.pundaren.guide.truth.core.drift

import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.core.geo.Geo
import se.pundaren.guide.truth.core.platser.Place
import se.pundaren.guide.truth.core.platser.PlaceIndex
import se.pundaren.guide.truth.core.platser.PlaceKind
import java.util.SplittableRandom

/**
 * Osannolikhetsdriften (byggplanen): ett slumpat men verkligt nåbart mål inom vald restid.
 *
 * Isokroner finns inte på enheten (T-06). I stället väljs kandidater inom fågelavstånd, restiden
 * till var och en räknas med ruttmotorns restidsmatris (`sources_to_targets`, A-16) och målet
 * lottas bland dem som faktiskt nås i tid. Slumpen är seedad: samma seed ger samma mål.
 */
object ImprobabilityDrive {
    /** Högsta rimliga fart fågelvägen, för att begränsa kandidaterna före matrisen. */
    fun topSpeedMps(mode: TravelMode): Double = if (mode == TravelMode.FOOT) 1.7 else 27.0

    private val KINDS = setOf(PlaceKind.SETTLEMENT, PlaceKind.FOOD, PlaceKind.REST_AREA)
    private val SETTLEMENTS = setOf("city", "town", "village", "hamlet")

    /**
     * Platser som över huvud taget kan ligga inom restiden, högst [limit] stycken (matrisen kostar
     * per mål). Urvalet bland fler är seedat.
     */
    fun candidates(index: PlaceIndex, from: GeoPoint, maxSeconds: Double, mode: TravelMode, seed: Long, limit: Int = 40): List<Place> {
        val radius = maxSeconds * topSpeedMps(mode)
        val within = index.places.filter { p ->
            p.kind in KINDS && (p.kind != PlaceKind.SETTLEMENT || p.subKind in SETTLEMENTS) &&
                Geo.distanceMeters(from, p.point).let { it <= radius && it >= radius * 0.15 }
        }.sortedBy { it.id }
        if (within.size <= limit) return within
        val random = SplittableRandom(seed)
        val pool = within.toMutableList()
        val chosen = ArrayList<Place>(limit)
        repeat(limit) { chosen += pool.removeAt(random.nextInt(pool.size)) }
        return chosen.sortedBy { it.id }
    }

    data class Pick(
        val place: Place,
        /** Restiden enligt ruttmotorn, sekunder. */
        val seconds: Double,
        /** Hur många av kandidaterna som nåddes inom restiden. */
        val reachable: Int,
        val considered: Int,
    )

    /**
     * Lottar ett mål bland kandidaterna vars restid ligger mellan halva och hela den valda tiden
     * (så att det blir en tur och inte grannhuset). [travelSeconds] har ett värde per kandidat, null
     * där ruttmotorn inte hittade någon väg. Null om inget mål nås.
     */
    fun pick(candidates: List<Place>, travelSeconds: List<Double?>, maxSeconds: Double, seed: Long): Pick? {
        require(candidates.size == travelSeconds.size) { "en restid per kandidat" }
        val reachable = candidates.zip(travelSeconds).filter { (_, s) -> s != null && s <= maxSeconds }.map { (p, s) -> p to s!! }
        if (reachable.isEmpty()) return null
        val far = reachable.filter { it.second >= maxSeconds * 0.5 }
        val pool = (far.ifEmpty { reachable }).sortedBy { it.first.id }
        val (place, seconds) = pool[SplittableRandom(seed).nextInt(pool.size)]
        return Pick(place, seconds, reachable.size, candidates.size)
    }
}
