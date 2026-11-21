package se.pundaren.guide.convoy

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Hur en annan deltagares position får visas, efter ålder (07, 3.5). */
enum class Freshness {
    /** Högst 30 s: prick med ålder i sekunder. */
    FRESH,

    /** 30 s till 2 min: blekare prick, "för N s sedan". */
    RECENT,

    /** 2 till 15 min: ihålig prick, "senast sedd HH.MM". */
    LAST_SEEN,

    /** Över 15 min: ingen prick, bara "senast sedd HH.MM" i listan. */
    STALE,
}

/** Det gränssnittet visar för den andra deltagaren. Åldern står alltid med, även när den är kort. */
data class PeerPresentation(
    val freshness: Freshness,
    val ageSeconds: Long,
    val label: String,
    /** Falskt för [Freshness.STALE]: en så gammal position ritas inte på kartan. */
    val showOnMap: Boolean,
)

/**
 * Ärlig ålder (byggplanen; 07, 3.5 och T-07): **ingen gammal position får presenteras som aktuell.**
 *
 * De två telefonernas klockor kan gå olika. Åldern räknas därför på två sätt, och det högre värdet
 * visas (07: "klockskillnad okänd"):
 * 1. mottagarens klocka minus tiden då positionen togs (avsändarens klocka), och
 * 2. tiden sedan kuvertet kom fram, mätt med mottagarens egen monotona klocka, plus hur gammal
 *    positionen var när den sändes (båda tiderna ur avsändarens klocka).
 * Det andra sättet påverkas inte av klockskillnaden, men missar tiden kuvertet var på väg.
 */
object ConvoyFreshness {
    fun ageSeconds(
        nowUtc: Instant,
        fixTimeUtcMillis: Long,
        sentAtUtcMillis: Long,
        /** Mottagarens monotona klocka nu respektive när kuvertet togs emot. */
        nowMonotonicMillis: Long,
        receivedMonotonicMillis: Long,
    ): Long {
        val acrossClocks = nowUtc.toEpochMilli() - fixTimeUtcMillis
        val sinceReceipt = (nowMonotonicMillis - receivedMonotonicMillis) + (sentAtUtcMillis - fixTimeUtcMillis).coerceAtLeast(0)
        return (maxOf(acrossClocks, sinceReceipt).coerceAtLeast(0)) / 1000
    }

    fun classify(ageSeconds: Long): Freshness = when {
        ageSeconds <= ConvoyLimits.FRESH_SECONDS -> Freshness.FRESH
        ageSeconds <= ConvoyLimits.RECENT_SECONDS -> Freshness.RECENT
        ageSeconds <= ConvoyLimits.LAST_SEEN_SECONDS -> Freshness.LAST_SEEN
        else -> Freshness.STALE
    }

    private val clock = DateTimeFormatter.ofPattern("HH.mm")

    /** [nowUtc] minus åldern ger klockslaget i mottagarens egen tid, så att "senast sedd" stämmer med mottagarens klocka. */
    fun present(ageSeconds: Long, nowUtc: Instant, zone: ZoneId): PeerPresentation {
        val freshness = classify(ageSeconds)
        val label = when (freshness) {
            Freshness.FRESH -> "$ageSeconds s"
            Freshness.RECENT -> "för $ageSeconds s sedan"
            Freshness.LAST_SEEN, Freshness.STALE -> "senast sedd " + clock.format(nowUtc.minusSeconds(ageSeconds).atZone(zone))
        }
        return PeerPresentation(freshness, ageSeconds, label, showOnMap = freshness != Freshness.STALE)
    }
}

/**
 * Mötespunkten (byggplanen): räknas ur bådas restid till ett fåtal kandidatplatser, inte ur kartans
 * mittpunkt. Vald plats är den där den som har längst dit får kortast väg; vid lika väljs den med
 * minst sammanlagd restid. Restiderna kommer från ruttmotorns restidsmatris på telefonen.
 */
object MeetingPoint {
    data class Choice(val index: Int, val secondsA: Double, val secondsB: Double) {
        val waitSeconds: Double get() = kotlin.math.abs(secondsA - secondsB)
    }

    /** Null om ingen kandidat nås av båda. `null` i en lista betyder att ruttmotorn inte hittade någon väg dit. */
    fun choose(secondsA: List<Double?>, secondsB: List<Double?>): Choice? {
        require(secondsA.size == secondsB.size) { "en restid per kandidat för var och en" }
        return secondsA.indices.mapNotNull { i ->
            val a = secondsA[i] ?: return@mapNotNull null
            val b = secondsB[i] ?: return@mapNotNull null
            Choice(i, a, b)
        }.minWithOrNull(compareBy({ maxOf(it.secondsA, it.secondsB) }, { it.secondsA + it.secondsB }, { it.index }))
    }
}
