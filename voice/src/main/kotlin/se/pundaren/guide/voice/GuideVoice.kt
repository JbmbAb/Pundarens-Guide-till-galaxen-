package se.pundaren.guide.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.pundaren.guide.truth.api.InstructionVoice

/** Röstens läge, för sanningsremsan och loggen. */
sealed interface VoiceStatus {
    data object Starting : VoiceStatus
    data class Ready(val voiceName: String) : VoiceStatus
    data class Failed(val reason: String) : VoiceStatus
}

/**
 * Appens enda röst: Samsungs talmotor med svensk offlineröst. En talmotor, två kanaler (03, 4.4):
 *
 * - **Kanal B (besked)** – [speakInstruction]. Sanningslagrets navigeringsröst. Läses alltid upp
 *   med `QUEUE_FLUSH`, som klipper allt annat den här rösten håller på med, även en svans.
 * - **Kanal S (svans)** – [speakTail]. Guidens röst. Får bara börja när inget besked spelas, har en
 *   tidsgräns och klipps i samma ögonblick som ett besked kommer.
 *
 * Båda kanalerna går genom samma `TextToSpeech`-objekt. Det är avsiktligt: Androids talmotor
 * tömmer bara den egna anroparens kö, så med två objekt hade ett besked fått vänta bakom en svans.
 *
 * Motorval, röstval (svenska, kräver inte nät, `SE` först), takt 0,95, tonhöjd 1,0, ljudattribut
 * för navigationsvägledning, kortvarigt ljudfokus med duckning och felvägarna kommer från ägarens
 * `SvenskTalObserver.kt` (etapp 3) och dess ändringar 1–4. Nytt här: svanskanalen, kontrollen att
 * den anslutna motorn verkligen är Samsungs och en andra väg in via systemets standardmotor ([connect]).
 */
class GuideVoice(
    context: Context,
    private val log: (String) -> Unit,
) : InstructionVoice, AutoCloseable {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow<VoiceStatus>(VoiceStatus.Starting)
    val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    private val _instructionSpeaking = MutableStateFlow(false)
    override val instructionSpeaking: StateFlow<Boolean> = _instructionSpeaking.asStateFlow()

    private var engine: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var counter = 0L

    /** Det besked som spelas eller väntar på att spelas (kanal B). */
    private var instructionId: String? = null
    private var instructionStartedAt = 0L

    /** Svansen som spelas (kanal S) och vad som ska hända när den slutar. */
    private var tailId: String? = null
    private var tailDone: ((finished: Boolean) -> Unit)? = null
    private var tailStartedAt = 0L
    private var tailTimeout: Runnable? = null

    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener { }
        .build()
    private var focusFor: String? = null

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = onMain { started(utteranceId) }
        override fun onDone(utteranceId: String?) = onMain { ended(utteranceId, finished = true) }
        @Deprecated("Krävs av API:t")
        override fun onError(utteranceId: String?) = onMain { ended(utteranceId, finished = false) }
        override fun onError(utteranceId: String?, errorCode: Int) = onMain {
            log("TAL FEL: motorn rapporterade fel $errorCode")
            ended(utteranceId, finished = false)
        }
        override fun onStop(utteranceId: String?, interrupted: Boolean) = onMain { ended(utteranceId, finished = false) }
    }

    /** Sant medan anslutningen görs med motorn uttryckligen angiven, falskt när systemets standardmotor prövas. */
    private var explicitAttempt = true

    init {
        if (!samsungEngineInstalled()) {
            // Finns inte Samsungs motor ska ingen annan motor få ta över (F-06: ingen Google).
            main.post { fail("Samsungs talmotor ($SAMSUNG_ENGINE) finns inte på enheten") }
        } else {
            connect(explicit = true)
        }
    }

    /**
     * Två vägar till Samsungs motor:
     *
     * 1. Uttryckligen, som i ägarens `SvenskTalObserver.kt`: `TextToSpeech(…, "com.samsung.SMT")`.
     * 2. Genom systemets standardmotor, om användaren har valt Samsungs motor som önskad motor.
     *
     * Väg 2 behövs på målenheten: SM-X900 med One UI 8.0 och Samsung TTS 3.5.02.46 avvisar väg 1
     * för appar utanför Samsungs egen lista ("is not allowed to bind to private engine", logcat
     * 2026-11-21). Oavsett väg godtas rösten bara om den anslutna motorn är Samsungs.
     */
    private fun connect(explicit: Boolean) {
        explicitAttempt = explicit
        // Ändring 4 (etapp 3): posta alltid. Utan installerad motor anropar Android lyssnaren
        // synkront inifrån konstruktorn, när engine ännu är null.
        val listener = TextToSpeech.OnInitListener { status -> main.post { initialize(status) } }
        engine = if (explicit) TextToSpeech(appContext, listener, SAMSUNG_ENGINE) else TextToSpeech(appContext, listener)
    }

    private fun samsungEngineInstalled(): Boolean = runCatching {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE).setPackage(SAMSUNG_ENGINE)
        appContext.packageManager.queryIntentServices(intent, 0).isNotEmpty()
    }.getOrDefault(false)

    private fun initialize(status: Int) {
        if (closed) return
        val tts = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            if (explicitAttempt) {
                log("TAL: Samsungs talmotor tog inte emot en uttrycklig anslutning (status $status). Provar systemets standardmotor.")
                tts.shutdown()
                engine = null
                connect(explicit = false)
            } else {
                fail("Ingen talmotor kunde startas")
            }
            return
        }
        // Om bindningen till den begärda motorn misslyckas kan Android i tysthet ansluta en annan motor
        // (standardmotorn eller den högst rankade). Listan över installerade motorer avslöjar inte det.
        // Därför prövas både systemets standardmotor och rösten själv (F-06: ingen Google).
        val defaultEngine = runCatching { tts.defaultEngine }.getOrNull()
        val allVoices = runCatching { tts.voices }.getOrNull().orEmpty()
        val swedish = allVoices
            .filter { it.locale.language == "sv" && !it.isNetworkConnectionRequired }
            .sortedWith(compareBy({ it.locale.country != "SE" }, { it.name }))
        log(
            "TAL ansluten ${if (explicitAttempt) "uttryckligen" else "via standardmotorn"}: standardmotor=$defaultEngine, " +
                "${allVoices.size} röster, svenska offline: ${swedish.joinToString { it.name }.ifEmpty { "inga" }}",
        )
        if (!explicitAttempt && defaultEngine != SAMSUNG_ENGINE) {
            fail("Systemets talmotor är $defaultEngine. Välj Samsungs text-till-tal-motor under Inställningar › Allmän hantering › Text till tal")
            return
        }
        if (allVoices.isNotEmpty() && allVoices.all { looksLikeGoogleVoiceName(it.name) }) {
            fail("Den anslutna talmotorn är inte Samsungs")
            return
        }
        val voice = swedish.firstOrNull { !looksLikeGoogleVoiceName(it.name) }
        if (voice == null) {
            fail("Ingen svensk offlineröst i Samsungs talmotor. Installera svenska under Text till tal › Samsungs motor › Installera röstdata")
            return
        }
        if (tts.setVoice(voice) != TextToSpeech.SUCCESS) {
            fail("Den svenska rösten ${voice.name} kunde inte väljas")
            return
        }
        tts.setAudioAttributes(audioAttributes)
        tts.setOnUtteranceProgressListener(progress)
        ready = true
        _status.value = VoiceStatus.Ready(voice.name)
        log("TAL REDO: ${voice.name}, offline (${swedish.size} svenska offlineröster: ${swedish.joinToString { it.name }})")
    }

    private fun fail(reason: String) {
        log("TAL FEL: $reason")
        ready = false
        engine?.shutdown() // ändring 3a (etapp 3): läck inte motorn när starten misslyckas
        engine = null
        _status.value = VoiceStatus.Failed(reason)
    }

    // ------------------------------------------------------------------------------------
    // Kanal B: besked
    // ------------------------------------------------------------------------------------

    override fun speakInstruction(text: String) = onMain {
        if (closed || text.isBlank()) return@onMain
        val tts = engine
        if (!ready || tts == null) {
            log("TAL EJ REDO: $text")
            return@onMain
        }
        // Beskedet klipper svansen. QUEUE_FLUSH nedan gör det i talmotorn; här städas bokföringen.
        finishTail(finished = false, cutBy = "besked")
        counter++
        val id = "besked-$counter"
        tts.setSpeechRate(INSTRUCTION_RATE)
        tts.setPitch(INSTRUCTION_PITCH)
        requestFocus(id)
        instructionId = id
        instructionStartedAt = SystemClock.elapsedRealtime()
        _instructionSpeaking.value = true
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            log("TAL FEL: Uppläsning misslyckades")
            instructionId = null
            _instructionSpeaking.value = false
            abandonFocus(id)
        }
    }

    override fun stopInstruction() = onMain {
        if (instructionId == null) return@onMain
        engine?.stop()
        instructionId = null
        _instructionSpeaking.value = false
        abandonFocus(null)
    }

    // ------------------------------------------------------------------------------------
    // Kanal S: svans
    // ------------------------------------------------------------------------------------

    /**
     * Läser upp en svans. [onDone] anropas alltid exakt en gång på huvudtråden: `true` om svansen
     * lästes färdigt, `false` om den klipptes (av ett besked, av tidsgränsen eller av [stopTail])
     * eller aldrig kunde börja.
     */
    fun speakTail(id: String, text: String, maxMillis: Int, onDone: (finished: Boolean) -> Unit) = onMain {
        val tts = engine
        if (closed || !ready || tts == null || text.isBlank() || _instructionSpeaking.value || maxMillis <= 0) {
            onDone(false)
            return@onMain
        }
        finishTail(finished = false, cutBy = "ny svans")
        val utterance = "svans-$id"
        tts.setSpeechRate(TAIL_RATE)
        tts.setPitch(TAIL_PITCH)
        requestFocus(utterance)
        tailId = utterance
        tailDone = onDone
        tailStartedAt = SystemClock.elapsedRealtime()
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utterance) != TextToSpeech.SUCCESS) {
            finishTail(finished = false, cutBy = "fel")
            return@onMain
        }
        val timeout = Runnable {
            if (tailId == utterance) {
                engine?.stop()
                finishTail(finished = false, cutBy = "tidsgräns $maxMillis ms")
            }
        }
        tailTimeout = timeout
        main.postDelayed(timeout, maxMillis.toLong())
    }

    /** Klipper en pågående svans. Rör inte ett besked. */
    fun stopTail() = onMain {
        if (tailId == null) return@onMain
        if (instructionId == null) engine?.stop()
        finishTail(finished = false, cutBy = "avbruten")
    }

    private fun finishTail(finished: Boolean, cutBy: String?) {
        val id = tailId ?: return
        val done = tailDone
        tailId = null
        tailDone = null
        tailTimeout?.let(main::removeCallbacks)
        tailTimeout = null
        abandonFocus(id)
        val millis = SystemClock.elapsedRealtime() - tailStartedAt
        log(if (finished) "SVANS KLAR: $millis ms" else "SVANS KLIPPT ($cutBy) efter $millis ms")
        done?.invoke(finished)
    }

    // ------------------------------------------------------------------------------------
    // Gemensamt
    // ------------------------------------------------------------------------------------

    private fun started(utteranceId: String?) {
        if (utteranceId != null && utteranceId == instructionId) instructionStartedAt = SystemClock.elapsedRealtime()
        if (utteranceId != null && utteranceId == tailId) tailStartedAt = SystemClock.elapsedRealtime()
    }

    private fun ended(utteranceId: String?, finished: Boolean) {
        if (utteranceId == null) return
        if (utteranceId == instructionId) {
            instructionId = null
            _instructionSpeaking.value = false
            abandonFocus(utteranceId)
            log("TAL KLART: ${SystemClock.elapsedRealtime() - instructionStartedAt} ms${if (finished) "" else " (klippt)"}")
        } else if (utteranceId == tailId) {
            finishTail(finished, cutBy = if (finished) null else "talmotorn")
        }
    }

    private fun requestFocus(id: String) {
        val answer = audio?.requestAudioFocus(focusRequest)
        focusFor = id
        // Läses upp även om fokus nekas (ägarbeslut 2026-11-20 23.30: allt ska höras).
        if (answer != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) log("TAL FOKUS nekat ($answer), läser ändå")
    }

    private fun abandonFocus(id: String?) {
        if (focusFor == null || (id != null && id != focusFor)) return
        focusFor = null
        audio?.abandonAudioFocusRequest(focusRequest)
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }

    override fun close() = onMain {
        if (closed) return@onMain
        closed = true
        ready = false
        engine?.stop()
        finishTail(finished = false, cutBy = "stängd")
        instructionId = null
        _instructionSpeaking.value = false
        abandonFocus(null)
        engine?.shutdown()
        engine = null
    }

    companion object {
        const val SAMSUNG_ENGINE = "com.samsung.SMT"

        /** Ägarens värden för beskedet (SvenskTalObserver.kt). */
        const val INSTRUCTION_RATE = 0.95f
        const val INSTRUCTION_PITCH = 1.0f

        /**
         * Guidens röst ska gå att skilja från beskedet med örat (RR-12). Kandidatvärden: något
         * långsammare och mörkare. Avgörs i lyssningstestet (G4).
         */
        const val TAIL_RATE = 0.90f
        const val TAIL_PITCH = 0.82f

        /**
         * Googles talmotor namnger sina röster "sv-se-x-lfs-local", "sv-se-x-cfg-network" eller
         * "sv-SE-language". En sådan röst godtas aldrig (F-06), vilken väg anslutningen än tog.
         */
        fun looksLikeGoogleVoiceName(name: String): Boolean {
            val n = name.lowercase()
            return n.contains("-x-") || n.endsWith("-language") || n.endsWith("-local") || n.endsWith("-network")
        }
    }
}
