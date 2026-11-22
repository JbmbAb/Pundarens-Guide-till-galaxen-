package se.pundaren.guide.app

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.pundaren.guide.conductor.TailStage
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.safetygate.SuppressReason
import se.pundaren.guide.voice.GuideVoice
import se.pundaren.guide.voice.VoiceStatus

/**
 * Svansytan och svansrösten (kanal S). Det enda stället i appen där Guidens text når skärm och
 * högtalare, och bara efter ett `Present` från SafetyGate via dirigenten (R-07 i 11). Svansen visas
 * i lila i svansytan, aldrig i sanningsremsan.
 */
class GuideStage(
    private val voice: GuideVoice,
    private val log: (String) -> Unit,
) : TailStage {
    private val main = Handler(Looper.getMainLooper())

    private val _shown = MutableStateFlow<TailCandidate?>(null)
    /** Svansen som visas just nu, eller null. */
    val shown: StateFlow<TailCandidate?> = _shown.asStateFlow()

    /** Sätts av kompositionsroten: dirigenten ska få veta när svansen är slut. */
    var onFinished: (tailId: String) -> Unit = {}

    private var textOnlyEnd: Runnable? = null

    override fun present(tail: TailCandidate, maxMillis: Int) {
        cancelTextOnly()
        _shown.value = tail
        log("SVANS ${tail.lineId} [${tail.origin}] högst $maxMillis ms: ${tail.text}")
        if (voice.status.value is VoiceStatus.Ready) {
            voice.speakTail(tail.id, tail.text, maxMillis) { finish(tail.id) }
        } else {
            // Ingen röst: svansen visas som text lika länge som den hade tagit att läsa upp.
            val end = Runnable { finish(tail.id) }
            textOnlyEnd = end
            main.postDelayed(end, minOf(maxMillis, tail.estimatedSpeechMillis).toLong().coerceAtLeast(500))
        }
    }

    override fun interrupt(reason: SuppressReason) {
        cancelTextOnly()
        val current = _shown.value
        _shown.value = null
        voice.stopTail()
        if (current != null) log("SVANS AVBRUTEN ($reason): ${current.lineId}")
    }

    private fun finish(tailId: String) {
        if (_shown.value?.id == tailId) _shown.value = null
        onFinished(tailId)
    }

    private fun cancelTextOnly() {
        textOnlyEnd?.let(main::removeCallbacks)
        textOnlyEnd = null
    }
}
