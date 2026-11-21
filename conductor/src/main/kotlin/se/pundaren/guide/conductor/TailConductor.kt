package se.pundaren.guide.conductor

import se.pundaren.guide.engine.api.EngineContext
import se.pundaren.guide.engine.api.EngineMemory
import se.pundaren.guide.engine.api.IllogicalEngine
import se.pundaren.guide.engine.api.PresentedTail
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.engine.api.TailKind
import se.pundaren.guide.safetygate.GateInput
import se.pundaren.guide.safetygate.PresentationDecision
import se.pundaren.guide.safetygate.SafetyGate
import se.pundaren.guide.safetygate.SuppressReason
import se.pundaren.guide.truth.api.TruthEvent
import se.pundaren.guide.truth.api.TruthStateReader

/**
 * Svansytan och svansrösten (kanal S). Implementeras av `:app`. Dirigenten anropar bara scenen
 * efter ett `Present` från SafetyGate, och avbryter den när SafetyGate säger `Interrupt`.
 */
interface TailStage {
    /** Börja visa och läsa upp svansen. Får inte blockera. [maxMillis] är den längsta tid den får ta. */
    fun present(tail: TailCandidate, maxMillis: Int)

    /** Avbryt det som presenteras just nu. */
    fun interrupt(reason: SuppressReason)
}

/** Reglagen i Probability Drive. De påverkar bara tramslagret, aldrig sanningskärnan (02, 4.1). */
data class ConductorSettings(
    /** Guiden får prata. Av betyder att inga svansar alls föreslås. */
    val enabled: Boolean = true,
    /** Andel av händelserna (promille) som alls får ge en svans. 1000 = varje händelse som motorn svarar på. */
    val chattinessPerMille: Int = 1000,
)

/** Vad dirigenten gjorde med en svans. Går till loggen och expeditionsprotokollet. */
sealed interface ConductorRecord {
    val tail: TailCandidate
    val atMonotonicMillis: Long

    data class Presented(override val tail: TailCandidate, override val atMonotonicMillis: Long, val maxMillis: Int) : ConductorRecord
    data class Waiting(override val tail: TailCandidate, override val atMonotonicMillis: Long, val reason: SuppressReason) : ConductorRecord
    data class Dropped(override val tail: TailCandidate, override val atMonotonicMillis: Long, val reason: SuppressReason) : ConductorRecord
    data class Interrupted(override val tail: TailCandidate, override val atMonotonicMillis: Long, val reason: SuppressReason) : ConductorRecord
    data class Finished(override val tail: TailCandidate, override val atMonotonicMillis: Long) : ConductorRecord
}

/**
 * Svansdirigenten: händelse -> Illogical Engine -> SafetyGate -> scenen.
 *
 * Dioden: dirigenten ser bara [TruthStateReader] (läsning) och händelserna den får. Den har ingen
 * referens till sanningskärnan och kan varken ändra rutt, besked eller nödläge. Beskedet (kanal B)
 * passerar aldrig här.
 *
 * Tillståndslös gentemot klockan: tiden kommer in som argument, så samma följd av anrop ger samma
 * beslut (testbart). Alla anrop ska komma från en och samma tråd.
 *
 * Sanningen först, tramset efteråt (RR-03): en svans som SafetyGate stoppar av ett övergående skäl
 * (ett besked spelas, omräkning pågår) får vänta och prövas igen vid nästa [reevaluate], men bara
 * så länge dess livslängd räcker (05, §5 punkt 2). Den prövas alltid på nytt mot det aktuella
 * sanningstillståndet; ingenting spelas på ett gammalt beslut.
 */
class TailConductor(
    private val engine: IllogicalEngine,
    private val gate: SafetyGate,
    private val state: TruthStateReader,
    private val stage: TailStage,
    private val seed: Long,
    private val record: (ConductorRecord) -> Unit = {},
) {
    var settings: ConductorSettings = ConductorSettings()

    /** Motorns minne: bara svansar som faktiskt presenterades (04, 1.8). */
    var memory: EngineMemory = EngineMemory()
        private set

    /** Svansen som väntar på att SafetyGate ska säga ja, eller null. */
    var pending: TailCandidate? = null
        private set

    /** Svansen som presenteras just nu, eller null. */
    var playing: TailCandidate? = null
        private set

    private var lastWaitReason: SuppressReason? = null

    /** En händelse från bussen. */
    fun onEvent(event: TruthEvent, nowMonotonicMillis: Long) {
        if (settings.enabled && passesChattiness(event.eventId)) {
            val navigation = state.navigation.value
            val context = EngineContext(seed, navigation.travelMode, memory, navigating = navigation.activeRoute)
            val candidates = engine.onEvent(event, context)
            val chosen = candidates.firstOrNull { it.kind == TailKind.SPOKEN } ?: candidates.firstOrNull()
            if (chosen != null) offer(chosen)
        }
        reevaluate(nowMonotonicMillis)
    }

    /**
     * En svans som användaren själv bad om (Don't Panic). Den går samma väg genom SafetyGate som
     * alla andra svansar och ersätter en eventuell väntande svans.
     */
    fun onRequested(candidate: TailCandidate, nowMonotonicMillis: Long) {
        if (settings.enabled) offer(candidate)
        reevaluate(nowMonotonicMillis)
    }

    private fun offer(candidate: TailCandidate) {
        pending = candidate
        lastWaitReason = null
    }

    /** Scenen har spelat klart svansen (eller den klipptes av ljudlagret). */
    fun onTailFinished(tailId: String, nowMonotonicMillis: Long) {
        val current = playing
        if (current != null && current.id == tailId) {
            playing = null
            record(ConductorRecord.Finished(current, nowMonotonicMillis))
        }
        reevaluate(nowMonotonicMillis)
    }

    /** Anropas när sanningstillståndet har ändrats och med jämna mellanrum. */
    fun reevaluate(nowMonotonicMillis: Long) {
        val input = GateInput(
            nowMonotonicMillis = nowMonotonicMillis,
            safety = state.safety.value,
            navigation = state.navigation.value,
            position = state.position.value,
            recentPresentedLineIds = memory.presented.map { it.lineId },
        )

        playing?.let { current ->
            val decision = gate.checkOngoing(input, current)
            if (decision is PresentationDecision.Interrupt) {
                playing = null
                stage.interrupt(decision.reason)
                record(ConductorRecord.Interrupted(current, nowMonotonicMillis, decision.reason))
            }
        }

        val candidate = pending ?: return
        if (!settings.enabled) { pending = null; return }
        if (nowMonotonicMillis > candidate.validUntilMonotonicMillis) {
            pending = null
            record(ConductorRecord.Dropped(candidate, nowMonotonicMillis, SuppressReason.EXPIRED))
            return
        }
        if (playing != null) return // en svans i taget; den väntande prövas när scenen är ledig

        when (val decision = gate.decide(input, candidate)) {
            is PresentationDecision.Present -> {
                pending = null
                playing = decision.candidate
                memory = memory.withPresented(PresentedTail(decision.candidate.id, decision.candidate.lineId, nowMonotonicMillis))
                record(ConductorRecord.Presented(decision.candidate, nowMonotonicMillis, decision.maxMillis))
                stage.present(decision.candidate, decision.maxMillis)
            }
            is PresentationDecision.Suppress ->
                if (decision.reason in TRANSIENT) {
                    if (lastWaitReason != decision.reason) {
                        lastWaitReason = decision.reason
                        record(ConductorRecord.Waiting(candidate, nowMonotonicMillis, decision.reason))
                    }
                } else {
                    pending = null
                    record(ConductorRecord.Dropped(candidate, nowMonotonicMillis, decision.reason))
                }
            is PresentationDecision.Interrupt, PresentationDecision.Continue -> {}
        }
    }

    /** Deterministiskt urval: samma händelse och samma seed ger samma svar. */
    private fun passesChattiness(eventId: String): Boolean {
        val perMille = settings.chattinessPerMille
        if (perMille >= 1000) return true
        if (perMille <= 0) return false
        var h = seed xor 0x2545F4914F6CDD1DL
        for (c in eventId) h = h * 1_000_003L + c.code
        // Slutblandning (splitmix64) så att id:n som bara skiljer sig i sista tecknet sprids jämnt.
        h = (h xor (h ushr 30)) * -0x40a7b892e31b1a47L
        h = (h xor (h ushr 27)) * -0x6b2fb644ecceee15L
        h = h xor (h ushr 31)
        return Math.floorMod(h, 1000L) < perMille
    }

    companion object {
        /**
         * Skäl som går över av sig själva inom en svans livslängd. Alla andra skäl stryker svansen
         * direkt: nödläge, tystnad efter nödläge, lågt batteri, förlorad position, ej godkänd rad,
         * upprepning, riktningsord, bara-till-fots, glitch i fart och stress.
         */
        val TRANSIENT: Set<SuppressReason> = setOf(
            SuppressReason.MANEUVER,
            SuppressReason.NO_TIME,
            SuppressReason.REROUTING,
            SuppressReason.UNCERTAINTY_NOT_SHOWN,
        )
    }
}
