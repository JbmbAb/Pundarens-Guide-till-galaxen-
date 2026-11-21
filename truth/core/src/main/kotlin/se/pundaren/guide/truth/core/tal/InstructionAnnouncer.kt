package se.pundaren.guide.truth.core.tal

import se.pundaren.guide.truth.api.InstructionVoice

/**
 * Sanningslagrets väg från ruttmotorns talinstruktion till kanal B: tvätt av Valhallas svenska
 * ([SvenskTalTvatt]), spärr mot upprepning efter omräkning ([UpprepningsSparr]) och sedan rösten.
 * Samma ordning och samma loggrader som i etapp 3-prototypens SvenskTalObserver.
 *
 * Ingenting här känner till Guiden eller Illogical Engine. Beskedet kan inte stoppas, ändras
 * eller fördröjas av tramslagret; det enda som kan tysta det är användarens egen ljudavstängning.
 */
class InstructionAnnouncer(
    private val voice: InstructionVoice,
    private val log: (String) -> Unit,
) {
    sealed interface Outcome {
        data class Spoken(val text: String) : Outcome
        data class RepeatSuppressed(val text: String) : Outcome
        data class Muted(val text: String) : Outcome
        data object Empty : Outcome
    }

    private val guard = UpprepningsSparr()
    private var count = 0L

    /** Användarens ljudavstängning. Beskedet visas fortfarande i sanningsremsan. */
    @Volatile var muted: Boolean = false

    @Synchronized
    fun announce(rawInstruction: String): Outcome {
        val raw = rawInstruction.trim()
        val text = SvenskTalTvatt.tvatta(raw)
        if (text.isEmpty()) return Outcome.Empty
        if (text != raw) log("TAL RÅTT: $raw")
        if (!guard.slapp(text)) {
            log("TAL UPPREPNING stoppad efter omräkning: $text")
            return Outcome.RepeatSuppressed(text)
        }
        if (muted) {
            log("TAL TYST: $text")
            return Outcome.Muted(text)
        }
        count++
        log("TAL #$count: $text")
        voice.speakInstruction(text)
        return Outcome.Spoken(text)
    }

    /** Navigeringen har börjat räkna om rutten. */
    @Synchronized fun rerouteStarted() = guard.omrakning()

    /** En ny tur börjar: glöm föregående besked. */
    @Synchronized fun newTrip() { guard.nollstall(); count = 0 }

    fun stop() = voice.stopInstruction()
}
