package se.pundaren.guide.prototyp.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.stadiamaps.ferrostar.core.SpokenInstructionObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import se.pundaren.guide.prototyp.sanning.tal.SvenskTalTvatt
import se.pundaren.guide.prototyp.sanning.tal.UpprepningsSparr
import uniffi.ferrostar.SpokenInstruction

class SvenskTalObserver(
    context: Context,
    private val rapportera: (String) -> Unit
) : SpokenInstructionObserver, AutoCloseable {

    private val huvudtrad = Handler(Looper.getMainLooper())
    private val tyst = MutableStateFlow(false)
    override val muteState: StateFlow<Boolean> = tyst

    private var motor: TextToSpeech? = null
    private var redo = false
    private var avslutad = false
    private var nummer = 0L

    // Ändring 3b: kortvarigt ljudfokus med duckning runt varje besked.
    private val ljud = context.applicationContext.getSystemService(AudioManager::class.java)
    private val ljudattribut = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val fokusbegaran = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(ljudattribut)
        .setOnAudioFocusChangeListener { }
        .build()
    private var fokusFor: String? = null

    private fun begarFokus(id: String) {
        val svar = ljud?.requestAudioFocus(fokusbegaran)
        fokusFor = id
        // Beskedet läses upp även om fokus nekas (ägarbeslut 2026-11-20 23.30: allt ska höras).
        if (svar != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) rapportera("TAL FOKUS nekat ($svar), läser ändå")
    }

    private fun slappFokus(id: String?) {
        if (fokusFor == null || (id != null && id != fokusFor)) return
        fokusFor = null
        ljud?.abandonAudioFocusRequest(fokusbegaran)
    }

    private val forlopp = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) = paHuvudtrad { slappFokus(utteranceId) }
        @Deprecated("Krävs av API:t")
        override fun onError(utteranceId: String?) = paHuvudtrad { slappFokus(utteranceId) }
        override fun onError(utteranceId: String?, errorCode: Int) = paHuvudtrad {
            rapportera("TAL FEL: motorn rapporterade fel $errorCode")
            slappFokus(utteranceId)
        }
        override fun onStop(utteranceId: String?, interrupted: Boolean) = paHuvudtrad { slappFokus(utteranceId) }
    }

    init {
        motor = TextToSpeech(
            context.applicationContext,
            { status ->
                paHuvudtrad { initiera(status) }
            },
            "com.samsung.SMT"
        )
    }

    private fun paHuvudtrad(atgard: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            atgard()
        } else {
            huvudtrad.post { atgard() }
        }
    }

    private fun initiera(status: Int) {
        if (avslutad) return

        val tal = motor ?: return

        if (status != TextToSpeech.SUCCESS ||
            // Ändring 1 (kompilering): TextToSpeech har inget publikt currentEngine.
            // Kontrollera i stället att Samsungs motor är installerad (getEngines()).
            tal.engines.none { it.name == "com.samsung.SMT" }
        ) {
            rapportera("TAL FEL: Samsungs talmotor kunde inte startas")
            slappMotorn() // Ändring 3a: läck inte motorn när starten misslyckas
            return
        }

        val rost = tal.voices
            ?.filter {
                it.locale.language == "sv" &&
                    !it.isNetworkConnectionRequired
            }
            ?.sortedWith(
                compareBy(
                    { it.locale.country != "SE" },
                    { it.name }
                )
            )
            ?.firstOrNull()

        if (rost == null ||
            tal.setVoice(rost) != TextToSpeech.SUCCESS
        ) {
            rapportera("TAL FEL: Ingen installerad svensk offlineröst")
            slappMotorn() // Ändring 3a: läck inte motorn när starten misslyckas
            return
        }

        tal.setAudioAttributes(ljudattribut) // samma attribut som ägarens kod, delade med fokusbegäran
        tal.setOnUtteranceProgressListener(forlopp) // Ändring 3b
        tal.setSpeechRate(0.95f)
        tal.setPitch(1.0f)

        redo = true
        rapportera("TAL REDO: ${rost.name}, offline")
    }

    // Ändring 3d: samma besked direkt efter en omräkning läses inte upp igen.
    private val sparr = UpprepningsSparr()

    /** Kallas av navigeringen när rutten räknas om. */
    fun omrakning() = paHuvudtrad { sparr.omrakning() }

    /** Kallas när en ny tur startar. */
    fun nyTur() = paHuvudtrad { sparr.nollstall() }

    // Ändring 3a: frigör motorn efter misslyckad start. Navigeringen fortsätter utan tal.
    private fun slappMotorn() {
        redo = false
        motor?.shutdown()
        motor = null
    }

    override fun onSpokenInstructionTrigger(
        spokenInstruction: SpokenInstruction
    ) {
        val ratt = spokenInstruction.text.trim()
        val besked = SvenskTalTvatt.tvatta(ratt) // Ändring 3c: tvätta Valhallas svenska före uppläsning

        paHuvudtrad {
            if (avslutad || tyst.value || besked.isEmpty()) {
                return@paHuvudtrad
            }

            if (besked != ratt) rapportera("TAL RÅTT: $ratt") // Ändring 3c: spårbarhet i loggen

            if (!sparr.slapp(besked)) { // Ändring 3d
                rapportera("TAL UPPREPNING stoppad efter omräkning: $besked")
                return@paHuvudtrad
            }

            if (!redo) {
                rapportera("TAL EJ REDO: $besked")
                return@paHuvudtrad
            }

            nummer++
            val id = "navigation-$nummer"
            begarFokus(id) // Ändring 3b

            val resultat = motor?.speak(
                besked,
                TextToSpeech.QUEUE_FLUSH,
                null,
                id
            )

            if (resultat != TextToSpeech.SUCCESS) {
                slappFokus(id) // Ändring 3b
                rapportera("TAL FEL: Uppläsning misslyckades")
            } else {
                rapportera("TAL #$nummer: $besked")
            }
        }
    }

    override fun stopAndClearQueue() {
        paHuvudtrad {
            motor?.stop()
            slappFokus(null) // Ändring 3b
        }
    }

    override fun setMuted(isMuted: Boolean) {
        paHuvudtrad {
            tyst.value = isMuted
            if (isMuted) {
                motor?.stop()
                slappFokus(null) // Ändring 3b
            }
        }
    }

    override fun close() {
        paHuvudtrad {
            if (avslutad) return@paHuvudtrad

            avslutad = true
            redo = false
            motor?.stop()
            slappFokus(null) // Ändring 3b
            motor?.shutdown()
            motor = null
        }
    }
}
