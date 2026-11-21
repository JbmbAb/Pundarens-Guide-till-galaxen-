package se.pundaren.guide.engine.api

import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.archive.api.Mechanism
import se.pundaren.guide.archive.api.OriginClass
import se.pundaren.guide.archive.api.Visibility
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.api.TruthEvent

enum class TailKind { SPOKEN, HUD_TEXT, ANIMATION }

/**
 * Ett förslag från tramslagret (03, 4.2; G-4). Bara text, id, ursprung, synlighet, slag och livslängd.
 * Inga fält av sanningstyp, inga callbacks. Avvikelse D-2: fälten [approval], [mechanisms] och
 * [mentionsRikard] är tillagda så att SafetyGate kan pröva R-06 och R-15 utan att läsa arkivet.
 */
data class TailCandidate(
    val id: String,
    val text: String,
    val lineId: String,
    val origin: OriginClass,
    val approval: Approval,
    val visibility: Visibility,
    val kind: TailKind,
    val mechanisms: Set<Mechanism>,
    val mentionsRikard: Boolean,
    val estimatedSpeechMillis: Int,
    val triggeredBy: String,
    val validUntilMonotonicMillis: Long,
    val contradicts: String? = null,
    /** Raden har riktningsord eller kan låta som ett besked och får bara presenteras till fots (F-11, 23.36). */
    val footOnly: Boolean = false,
)

/** En svans som SafetyGate faktiskt släppte fram och som spelades. Bara sådana får förnekas (04, 1.8). */
data class PresentedTail(val tailId: String, val lineId: String, val presentedAtMonotonicMillis: Long)

/** Motorns minne. Oföränderligt; anroparen håller det och skickar in det med varje händelse. */
data class EngineMemory(val presented: List<PresentedTail> = emptyList()) {
    fun withPresented(tail: PresentedTail, keep: Int = 50): EngineMemory =
        EngineMemory((presented + tail).takeLast(keep))
}

data class EngineContext(
    /** Seed sätts alltid av anroparen (PR1-09: ingen System.nanoTime()). */
    val seed: Long,
    val travelMode: TravelMode,
    val memory: EngineMemory = EngineMemory(),
)

/**
 * Illogical Engine (05, avsnitt 5). Ren funktion av händelsen, minnet och en seed. Ingen I/O.
 * Motorn kan bara föreslå. Den kan inte publicera händelser och har ingen referens till sanningskärnan.
 */
interface IllogicalEngine {
    fun onEvent(event: TruthEvent, context: EngineContext): List<TailCandidate>
}

/**
 * En rad som användaren själv ber om (Don't Panic, kort tryck). Ingen sanningshändelse utlöser den,
 * så [TailCandidate.triggeredBy] är anroparens eget id för begäran. Raden går samma väg genom
 * SafetyGate som alla andra svansar. Tillägg i etapp 4.
 */
interface WisdomSource {
    fun wisdom(requestId: String, nowMonotonicMillis: Long, context: EngineContext): TailCandidate?
}
