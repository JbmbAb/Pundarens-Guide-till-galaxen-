package se.pundaren.guide.safetygate

import se.pundaren.guide.archive.api.Approval
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.engine.api.TailKind
import se.pundaren.guide.truth.api.NavigationSnapshot
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.api.PositionSnapshot
import se.pundaren.guide.truth.api.SafetySnapshot
import se.pundaren.guide.truth.api.TravelMode

/**
 * Kandidatvärdena markerade * i 06. **Inte frysta** (G0-grindbeslutet 2026-11-20).
 * Allt är konfigurerbart; testerna läser värdena härifrån.
 */
data class SafetyGateConfig(
    val quietWindowVehicleSeconds: Double = 15.0,   // R-02 T_quiet fordon*
    val quietWindowFootSeconds: Double = 8.0,       // R-02 T_quiet till fots*
    val noTimeMarginMillis: Long = 2_000,           // R-02 marginal*
    val uncertaintyShownMinMillis: Long = 2_000,    // R-06*
    val batteryPriorityPercent: Int = 15,           // R-08*
    val stressSpeedKmh: Double = 50.0,              // R-10*
    val stressReroutes: Int = 2,                    // R-10*
    val stressRerouteWindowMillis: Long = 120_000,  // R-10*
    val stressMaxTailMillis: Int = 1_500,           // R-10*
    val glitchMaxSpeedKmh: Double = 30.0,           // R-11*
    val afterEmergencyQuietMillis: Long = 60_000,   // R-16*
    val noRepeatWindow: Int = 10,                   // R-13
    /** Byggarens tillägg, inte i 06: från denna fart gäller fordonsreglerna även till fots (F-05). */
    val vehicleRulesFromKmh: Double = 10.0,
) {
    companion object {
        const val STATUS = "KANDIDAT_OFRYST"
        val CANDIDATE = SafetyGateConfig()
    }
}

enum class GateState { NORMAL, POSITION_UNCERTAIN, PRIORITY_INFO, MANEUVER_WINDOW, REROUTING, EMERGENCY }

enum class SuppressReason {
    EMERGENCY, AFTER_EMERGENCY, REROUTING, MANEUVER, NO_TIME, PRIORITY_INFO, POSITION_LOST,
    UNCERTAINTY_NOT_SHOWN, NOT_APPROVED, EXPIRED, REPEAT, DIRECTION_WORD, FOOT_ONLY_IN_VEHICLE, GLITCH_SPEED, STRESS_TOO_LONG,
}

sealed interface PresentationDecision {
    data class Present(val candidate: TailCandidate, val maxMillis: Int) : PresentationDecision
    data class Suppress(val reason: SuppressReason) : PresentationDecision
    data class Interrupt(val reason: SuppressReason) : PresentationDecision
    data object Continue : PresentationDecision
}

/** Allt SafetyGate får veta. Bara värden; ingen klocka och ingen I/O (06, avsnitt 1). */
data class GateInput(
    val nowMonotonicMillis: Long,
    val safety: SafetySnapshot,
    val navigation: NavigationSnapshot,
    val position: PositionSnapshot,
    /** lineId för de senast presenterade svansarna, äldst först. */
    val recentPresentedLineIds: List<String> = emptyList(),
)

/**
 * SafetyGate (06). Deterministisk. Har ingen skrivväg till sanningsdata (G-9): den returnerar bara beslut.
 * Beskedet (kanal B) passerar aldrig här.
 */
class SafetyGate(private val config: SafetyGateConfig = SafetyGateConfig.CANDIDATE) {

    /** Tillstånd med prioritet EMERGENCY > REROUTING > MANEUVER_WINDOW > PRIORITY_INFO > POSITION_UNCERTAIN > NORMAL. */
    fun state(input: GateInput): GateState {
        val nav = input.navigation
        return when {
            input.safety.emergencyActive -> GateState.EMERGENCY
            nav.rerouting -> GateState.REROUTING
            inManeuverWindow(nav) -> GateState.MANEUVER_WINDOW
            input.safety.batteryPercent < config.batteryPriorityPercent || input.safety.convoyContactLost -> GateState.PRIORITY_INFO
            input.position.quality != PositionQuality.GOOD -> GateState.POSITION_UNCERTAIN
            else -> GateState.NORMAL
        }
    }

    /** Beslut om en ny svans. */
    fun decide(input: GateInput, candidate: TailCandidate): PresentationDecision {
        val nav = input.navigation
        val s = state(input)
        if (s == GateState.EMERGENCY) return suppress(SuppressReason.EMERGENCY)
        val cleared = input.safety.emergencyClearedAtMonotonicMillis
        if (cleared != null && input.nowMonotonicMillis - cleared < config.afterEmergencyQuietMillis) return suppress(SuppressReason.AFTER_EMERGENCY)
        if (s == GateState.REROUTING) return suppress(SuppressReason.REROUTING)
        // R-02 prövas före tyst fönster så att skälet blir NO_TIME när nästa talinstruktion är för nära (SG-02).
        if (!fitsBeforeNextInstruction(nav, candidate.estimatedSpeechMillis.toLong())) return suppress(SuppressReason.NO_TIME)
        if (s == GateState.MANEUVER_WINDOW) return suppress(SuppressReason.MANEUVER)
        if (s == GateState.PRIORITY_INFO) return suppress(SuppressReason.PRIORITY_INFO)
        if (s == GateState.POSITION_UNCERTAIN) {
            if (input.position.quality == PositionQuality.LOST && nav.activeRoute) return suppress(SuppressReason.POSITION_LOST)
            val shown = input.position.uncertaintyShownSinceMonotonicMillis ?: return suppress(SuppressReason.UNCERTAINTY_NOT_SHOWN)
            if (candidate.mentionsRikard && input.nowMonotonicMillis - shown < config.uncertaintyShownMinMillis) {
                return suppress(SuppressReason.UNCERTAINTY_NOT_SHOWN)
            }
        }
        if (candidate.approval != Approval.APPROVED) return suppress(SuppressReason.NOT_APPROVED)
        if (input.nowMonotonicMillis > candidate.validUntilMonotonicMillis) return suppress(SuppressReason.EXPIRED)
        if (candidate.lineId in input.recentPresentedLineIds.takeLast(config.noRepeatWindow)) return suppress(SuppressReason.REPEAT)
        // R-12 (uppspelningsspärr för PRIVATE) är borttagen genom ägarbeslut 2026-11-20 kl. 23.30:
        // "I helvete allt ska höras lägg av med detta trams". Synligheten styr inte uppspelning.
        if (vehicleRules(nav) && GateDirectionCheck.hasDirectionWord(candidate.text)) return suppress(SuppressReason.DIRECTION_WORD)
        // F-11 (ägarbeslut 23.36): rader som kan låta som besked får bara användas till fots. Egen mönsterlista + motorns märkning.
        if (vehicleRules(nav) && (candidate.footOnly || GateDirectionCheck.soundsLikeInstruction(candidate.text))) return suppress(SuppressReason.FOOT_ONLY_IN_VEHICLE)
        if (candidate.kind == TailKind.ANIMATION && nav.speedKmh > config.glitchMaxSpeedKmh) return suppress(SuppressReason.GLITCH_SPEED)
        val stressed = stressed(input)
        if (stressed && candidate.kind == TailKind.ANIMATION) return suppress(SuppressReason.STRESS_TOO_LONG)
        if (stressed && candidate.estimatedSpeechMillis > config.stressMaxTailMillis) return suppress(SuppressReason.STRESS_TOO_LONG)

        var max = candidate.estimatedSpeechMillis.toLong()
        nav.millisToNextSpokenInstruction?.let { max = minOf(max, it - config.noTimeMarginMillis) }
        return PresentationDecision.Present(candidate, max.toInt())
    }

    /**
     * Beslut om en svans som redan spelas. Strängare än 06 på en punkt: avbrott sker också vid
     * REROUTING och PRIORITY_INFO, eftersom 06 säger att inga svansar får förekomma där.
     */
    fun checkOngoing(input: GateInput, playing: TailCandidate): PresentationDecision = when (state(input)) {
        GateState.EMERGENCY -> PresentationDecision.Interrupt(SuppressReason.EMERGENCY)
        GateState.REROUTING -> PresentationDecision.Interrupt(SuppressReason.REROUTING)
        GateState.MANEUVER_WINDOW -> PresentationDecision.Interrupt(SuppressReason.MANEUVER)
        GateState.PRIORITY_INFO -> PresentationDecision.Interrupt(SuppressReason.PRIORITY_INFO)
        GateState.POSITION_UNCERTAIN ->
            if (input.position.quality == PositionQuality.LOST && input.navigation.activeRoute) PresentationDecision.Interrupt(SuppressReason.POSITION_LOST)
            else PresentationDecision.Continue
        GateState.NORMAL ->
            if (input.nowMonotonicMillis > playing.validUntilMonotonicMillis) PresentationDecision.Interrupt(SuppressReason.EXPIRED)
            else PresentationDecision.Continue
    }

    private fun inManeuverWindow(nav: NavigationSnapshot): Boolean {
        if (nav.instructionSpeaking) return true
        val t = nav.secondsToNextManeuver ?: return false
        val quiet = if (vehicleRules(nav)) config.quietWindowVehicleSeconds else config.quietWindowFootSeconds
        return t <= quiet
    }

    private fun fitsBeforeNextInstruction(nav: NavigationSnapshot, speechMillis: Long): Boolean {
        val next = nav.millisToNextSpokenInstruction ?: return true
        return speechMillis + config.noTimeMarginMillis < next
    }

    private fun vehicleRules(nav: NavigationSnapshot): Boolean =
        nav.travelMode != TravelMode.FOOT || nav.speedKmh >= config.vehicleRulesFromKmh

    private fun stressed(input: GateInput): Boolean {
        val nav = input.navigation
        val recent = nav.recentRerouteMonotonicMillis.count { input.nowMonotonicMillis - it <= config.stressRerouteWindowMillis }
        return nav.speedKmh > config.stressSpeedKmh || recent >= config.stressReroutes
    }

    private fun suppress(r: SuppressReason) = PresentationDecision.Suppress(r)
}

/** Oberoende kontroll av riktningsord (06, R-03). Egen lista; delar ingen kod med motorns spärr. */
object GateDirectionCheck {
    private val words = listOf("rakt fram", "vänster", "höger", "sväng", "avfart", "rondell")
    fun hasDirectionWord(text: String): Boolean {
        val t = text.lowercase().replace(Regex("\\s+"), " ")
        return words.any { t.contains(it) }
    }

    /** Oberoende lista över fraser som kan misstas för besked (RR-04). Delar ingen kod med motorn. */
    private val instructionLike = listOf(
        "är korrekt", "har rätt", "är verklig", "du är inte vilse", "fortsätt enligt", "kör vidare",
        "avståndet stämmer", "tiden stämmer", "du är framme",
    )
    fun soundsLikeInstruction(text: String): Boolean {
        val t = text.lowercase().replace(Regex("\\s+"), " ")
        return instructionLike.any { t.contains(it) }
    }
}
