package se.pundaren.guide.prototyp.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.stadiamaps.ferrostar.core.SpokenInstructionObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
            return
        }

        tal.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(
                    AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                )
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tal.setSpeechRate(0.95f)
        tal.setPitch(1.0f)

        redo = true
        rapportera("TAL REDO: ${rost.name}, offline")
    }

    override fun onSpokenInstructionTrigger(
        spokenInstruction: SpokenInstruction
    ) {
        val besked = spokenInstruction.text.trim()

        paHuvudtrad {
            if (avslutad || tyst.value || besked.isEmpty()) {
                return@paHuvudtrad
            }

            if (!redo) {
                rapportera("TAL EJ REDO: $besked")
                return@paHuvudtrad
            }

            nummer++

            val resultat = motor?.speak(
                besked,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "navigation-$nummer"
            )

            if (resultat != TextToSpeech.SUCCESS) {
                rapportera("TAL FEL: Uppläsning misslyckades")
            } else {
                rapportera("TAL #$nummer: $besked")
            }
        }
    }

    override fun stopAndClearQueue() {
        paHuvudtrad {
            motor?.stop()
        }
    }

    override fun setMuted(isMuted: Boolean) {
        paHuvudtrad {
            tyst.value = isMuted
            if (isMuted) {
                motor?.stop()
            }
        }
    }

    override fun close() {
        paHuvudtrad {
            if (avslutad) return@paHuvudtrad

            avslutad = true
            redo = false
            motor?.stop()
            motor?.shutdown()
            motor = null
        }
    }
}
