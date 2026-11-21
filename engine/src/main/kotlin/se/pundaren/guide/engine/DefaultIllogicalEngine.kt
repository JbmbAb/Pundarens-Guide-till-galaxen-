package se.pundaren.guide.engine

import se.pundaren.guide.archive.api.ArchiveEntry
import se.pundaren.guide.archive.api.EntryKind
import se.pundaren.guide.archive.api.Mechanism
import se.pundaren.guide.engine.api.EngineContext
import se.pundaren.guide.engine.api.IllogicalEngine
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.engine.api.TailKind
import se.pundaren.guide.engine.api.WisdomSource
import se.pundaren.guide.truth.api.Arrived
import se.pundaren.guide.truth.api.BatteryLow
import se.pundaren.guide.truth.api.ConvoyContactLost
import se.pundaren.guide.truth.api.ConvoyGap
import se.pundaren.guide.truth.api.EmergencyActivated
import se.pundaren.guide.truth.api.EmergencyCleared
import se.pundaren.guide.truth.api.ManeuverCompleted
import se.pundaren.guide.truth.api.ManeuverUpcoming
import se.pundaren.guide.truth.api.OffRoute
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.api.PositionQualityChanged
import se.pundaren.guide.truth.api.Rerouted
import se.pundaren.guide.truth.api.RouteStarted
import se.pundaren.guide.truth.api.Stationary
import se.pundaren.guide.truth.api.TimeOfDayChanged
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.api.TruthEvent
import java.util.SplittableRandom

/** Startvärden. Inte frysta (G0-grindbeslutet). */
data class EngineConfig(
    /** 05 §5 punkt 2: högst 10 s efter händelsen (kandidatvärde). */
    val tailLifetimeMillis: Long = 10_000,
    /** Uppskattad taltid. Byggarens antagande, mäts i G4. */
    val baseSpeechMillis: Int = 600,
    val millisPerWord: Int = 380,
    /** R-13 / G1: ingen upprepning inom tio repliker. */
    val noRepeatWindow: Int = 10,
    /** Sannolikhet i promille att motsägelseminnet används när en förnekelserad finns. */
    val denialPerMille: Int = 250,
) {
    companion object {
        const val STATUS = "KANDIDAT_OFRYST"
    }
}

/**
 * Illogical Engine, etapp 1. Väljer bara rader ur [LineBank]. Skriver ingen egen text.
 * Samma händelse, samma minne och samma seed ger samma utdata (05, avsnitt 5).
 */
class DefaultIllogicalEngine(
    private val bank: LineBank,
    private val config: EngineConfig = EngineConfig(),
) : IllogicalEngine, WisdomSource {

    override fun onEvent(event: TruthEvent, context: EngineContext): List<TailCandidate> {
        if (!mayProposeTail(event)) return emptyList()
        val rng = SplittableRandom(mix(context.seed, event.eventId))
        val recent = context.memory.presented.takeLast(config.noRepeatWindow).map { it.lineId }.toSet()
        val vehicle = context.travelMode != TravelMode.FOOT

        fun usable(e: ArchiveEntry) = e.id !in recent && !(vehicle && LineBank.isFootOnly(e)) && fitsSituation(e, event)

        // M2: motsägelseminnet förnekar bara yttranden som faktiskt presenterades.
        val lastPresented = context.memory.presented.lastOrNull()
        val denials = bank.denialLines.filter(::usable)
        if (lastPresented != null && denials.isNotEmpty() && rng.nextInt(1000) < config.denialPerMille) {
            val line = denials[rng.nextInt(denials.size)]
            return listOf(toCandidate(line, event, contradicts = lastPresented.tailId, extra = setOf(Mechanism.M2)))
        }
        val pool = bank.lines.filter(::usable)
        if (pool.isEmpty()) return emptyList()
        val line = pool[rng.nextInt(pool.size)]
        return listOf(toCandidate(line, event, contradicts = null, extra = emptySet()))
    }

    /**
     * Don't Panic: en rad på begäran. Väljs bland arkivets citat (original och godkända bearbetningar)
     * och annars bland övriga rader utan situation. Samma spärrar som i [onEvent]: ingen upprepning
     * inom fönstret och inga rader som bara får användas till fots när läget är fordon.
     */
    override fun wisdom(requestId: String, nowMonotonicMillis: Long, context: EngineContext): TailCandidate? {
        val rng = SplittableRandom(mix(context.seed, requestId))
        val recent = context.memory.presented.takeLast(config.noRepeatWindow).map { it.lineId }.toSet()
        val vehicle = context.travelMode != TravelMode.FOOT
        val usable = bank.lines.filter { e ->
            e.id !in recent && !(vehicle && LineBank.isFootOnly(e)) && e.tags.none { it.startsWith("situation:") && it != "situation:general" }
        }
        val quotes = usable.filter { it.kind == EntryKind.QUOTE }
        val pool = quotes.ifEmpty { usable }
        if (pool.isEmpty()) return null
        return toCandidate(pool[rng.nextInt(pool.size)], requestId, nowMonotonicMillis, contradicts = null, extra = emptySet())
    }

    private fun toCandidate(line: ArchiveEntry, event: TruthEvent, contradicts: String?, extra: Set<Mechanism>) =
        toCandidate(line, event.eventId, event.monotonicMillis, contradicts, extra)

    private fun toCandidate(line: ArchiveEntry, triggeredBy: String, atMonotonicMillis: Long, contradicts: String?, extra: Set<Mechanism>) = TailCandidate(
        id = "TAIL-${line.id}-$triggeredBy",
        text = line.text,
        lineId = line.id,
        origin = line.originClass,
        approval = line.approval,
        visibility = line.visibility,
        kind = TailKind.SPOKEN,
        mechanisms = line.mechanisms + extra,
        mentionsRikard = VoiceGuards.mentionsRikard(line.text),
        estimatedSpeechMillis = estimateSpeechMillis(line.text),
        footOnly = LineBank.isFootOnly(line),
        triggeredBy = triggeredBy,
        validUntilMonotonicMillis = atMonotonicMillis + config.tailLifetimeMillis,
        contradicts = contradicts,
    )

    fun estimateSpeechMillis(text: String): Int =
        config.baseSpeechMillis + config.millisPerWord * text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    companion object {
        /** Händelser där tramslagret inte föreslår något alls (05, avsnitt 3). */
        fun mayProposeTail(event: TruthEvent): Boolean = when (event) {
            is OffRoute, is ConvoyContactLost, is EmergencyActivated, is EmergencyCleared,
            is BatteryLow, is ConvoyGap -> false
            is PositionQualityChanged -> event.quality != PositionQuality.LOST
            is RouteStarted, is ManeuverUpcoming, is ManeuverCompleted, is Rerouted,
            is Stationary, is Arrived, is TimeOfDayChanged -> true
        }

        /**
         * Navigationskommentarernas fält `situation` styr när de får föreslås. Rader utan situation
         * (citat, variationer, "general") passar alla händelser som får ge svans.
         */
        fun fitsSituation(e: ArchiveEntry, event: TruthEvent): Boolean {
            val situation = e.tags.firstOrNull { it.startsWith("situation:") }?.removePrefix("situation:") ?: return true
            return when (situation) {
                "general" -> true
                "turn" -> event is ManeuverCompleted
                "straight" -> event is ManeuverCompleted || event is RouteStarted || event is Rerouted || event is TimeOfDayChanged
                "arrival" -> event is Arrived
                else -> false
            }
        }

        private fun mix(seed: Long, eventId: String): Long {
            var h = seed xor 0x5DEECE66DL
            for (c in eventId) h = h * 1_000_003L + c.code
            return h
        }
    }
}
