package se.pundaren.guide.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.pundaren.guide.truth.api.InstructionVoice

/** Röstens läge, för sanningsremsan och loggen. */
sealed interface VoiceStatus {
    data object Starting : VoiceStatus
    data class Ready(val engine: String, val instructionVoice: String, val tailVoice: String) : VoiceStatus
    data class Failed(val reason: String) : VoiceStatus
}

/**
 * Appens enda röst: enhetens talmotor med en svensk röst som läses upp helt på enheten.
 * En talmotor, två kanaler (03, 4.4):
 *
 * - **Kanal B (besked)** – [speakInstruction]. Sanningslagrets navigeringsröst. Läses alltid upp
 *   med `QUEUE_FLUSH`, som klipper allt annat den här rösten håller på med, även en svans.
 * - **Kanal S (svans)** – [speakTail]. Guidens röst. Får bara börja när inget besked spelas, har en
 *   tidsgräns och klipps i samma ögonblick som ett besked kommer.
 *
 * Båda kanalerna går genom samma `TextToSpeech`-objekt. Det är avsiktligt: Androids talmotor
 * tömmer bara den egna anroparens kö, så med två objekt hade ett besked fått vänta bakom en svans.
 * Kanalerna får olika röster när enheten har mer än en svensk röst, så att det hörs vem som talar
 * (RR-12).
 *
 * **Vilken motor.** Ägarens `SvenskTalObserver.kt` (etapp 3) bad uttryckligen om Samsungs motor
 * `com.samsung.SMT`. På målenheten SM-X900 (One UI 8.0, Samsung TTS 3.5.02.46) är den en privat
 * motor som avvisar appen ("is not allowed to bind to private engine"), och Android ansluter då
 * tyst den högst rankade öppna motorn, som där är Googles (logcat 2026-11-21). Ägarbeslut samma dag:
 * förbudet mot Google gäller Googles API:er, inte Google i övrigt, och enhetens talmotor får
 * användas. Appen använder därför systemets talmotor, vilken den än är.
 *
 * **Bara på enheten.** Bara röster som inte kräver nät väljs, och helst motorns uttryckligt lokala
 * röster ("…-local"). Texten som läses upp (gatunamn, citat) ska inte lämna enheten.
 *
 * Takt 0,95, tonhöjd 1,0, ljudattribut för navigationsvägledning, kortvarigt ljudfokus med duckning
 * och felvägarna kommer från ägarens fil och dess ändringar 1–4.
 */
class GuideVoice(
    context: Context,
    private val log: (String) -> Unit,
) : InstructionVoice, AutoCloseable {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow<VoiceStatus>(VoiceStatus.Starting)
    val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    private val _voices = MutableStateFlow<List<String>>(emptyList())
    /** Namnen på enhetens svenska röster som läses upp utan nät, i den ordning de föredras. */
    val voices: StateFlow<List<String>> = _voices.asStateFlow()

    private val _instructionSpeaking = MutableStateFlow(false)
    override val instructionSpeaking: StateFlow<Boolean> = _instructionSpeaking.asStateFlow()

    private var engine: TextToSpeech? = null
    private var engineName = "okänd"
    private var swedish: List<Voice> = emptyList()
    private var instructionVoice: Voice? = null
    private var tailVoice: Voice? = null
    private var preferredInstruction: String? = null
    private var preferredTail: String? = null
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

    /** En svans som just klippts av ett besked, tills talmotorn har kvitterat att den tystnat. */
    private var cutTailId: String? = null
    private var cutTailAt = 0L

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

    init {
        engine = TextToSpeech(
            appContext,
            // Ändring 4 (etapp 3): posta alltid. Utan installerad motor anropar Android lyssnaren
            // synkront inifrån konstruktorn, när engine ännu är null.
            { status -> main.post { initialize(status) } },
        )
    }

    private fun initialize(status: Int) {
        if (closed) return
        val tts = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            fail("Ingen talmotor kunde startas")
            return
        }
        engineName = runCatching { tts.defaultEngine }.getOrNull() ?: "okänd"
        val all = runCatching { tts.voices }.getOrNull().orEmpty()
        swedish = all
            .filter { it.locale.language == "sv" && !it.isNetworkConnectionRequired && !isNetworkVoiceName(it.name) }
            .sortedWith(compareBy({ !isLocalVoiceName(it.name) }, { it.locale.country != "SE" }, { it.name }))
        _voices.value = swedish.map { it.name }
        log("TAL motor $engineName, ${all.size} röster, svenska utan nät: ${swedish.joinToString { it.name }.ifEmpty { "inga" }}")
        if (swedish.isEmpty()) {
            fail("Ingen svensk röst som läses upp på enheten. Installera svensk röstdata under Inställningar › Allmän hantering › Text till tal")
            return
        }
        tts.setAudioAttributes(audioAttributes)
        tts.setOnUtteranceProgressListener(progress)
        chooseVoices()
        ready = true
    }

    /** Väljer röst per kanal: användarens val om det finns, annars två olika röster om enheten har flera. */
    private fun chooseVoices() {
        val first = swedish.firstOrNull() ?: return
        val instruction = swedish.firstOrNull { it.name == preferredInstruction } ?: first
        val tail = swedish.firstOrNull { it.name == preferredTail }
            ?: swedish.lastOrNull { it.name != instruction.name && isLocalVoiceName(it.name) == isLocalVoiceName(instruction.name) }
            ?: swedish.firstOrNull { it.name != instruction.name }
            ?: instruction
        instructionVoice = instruction
        tailVoice = tail
        _status.value = VoiceStatus.Ready(engineName, instruction.name, tail.name)
        log("TAL REDO: besked ${instruction.name}, Guiden ${tail.name}, motor $engineName, utan nät")
    }

    /** Användarens val av röster (namn ur [voices]). Null betyder appens eget val. */
    fun setPreferredVoices(instruction: String?, tail: String?) = onMain {
        preferredInstruction = instruction
        preferredTail = tail
        if (ready) chooseVoices()
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
        val voice = instructionVoice
        if (!ready || tts == null || voice == null) {
            log("TAL EJ REDO: $text")
            return@onMain
        }
        // Beskedet äger rösten från första raden: det märks som pågående innan något annat händer,
        // så att ingen svans kan lämnas in medan beskedet är på väg (speakTail vägrar då).
        // Sett på målenheten 2026-11-21 kl. 13.03.06: svansens slutanrop kördes förut inifrån den
        // här funktionen, dirigenten lämnade in nästa svans i samma ögonblick, och dess tidsgräns
        // hade kunnat stoppa talmotorn mitt i beskedet.
        counter++
        val id = "besked-$counter"
        instructionId = id
        instructionStartedAt = SystemClock.elapsedRealtime()
        _instructionSpeaking.value = true
        // Beskedet klipper svansen. QUEUE_FLUSH nedan gör det i talmotorn; här städas bokföringen.
        // Den klippta svansens id sparas så att motorns kvitto kan tidsättas (R-01: inom 100 ms).
        cutTailId = tailId
        cutTailAt = instructionStartedAt
        finishTail(finished = false, cutBy = "besked")
        tts.voice = voice
        tts.setSpeechRate(INSTRUCTION_RATE)
        tts.setPitch(INSTRUCTION_PITCH)
        requestFocus(id)
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
     * eller aldrig kunde börja. Anropet kommer alltid efteråt, aldrig inifrån det här anropet.
     */
    fun speakTail(id: String, text: String, maxMillis: Int, onDone: (finished: Boolean) -> Unit) = onMain {
        val tts = engine
        val voice = tailVoice
        if (closed || !ready || tts == null || voice == null || text.isBlank() || instructionId != null || _instructionSpeaking.value || maxMillis <= 0) {
            main.post { onDone(false) }
            return@onMain
        }
        finishTail(finished = false, cutBy = "ny svans")
        val utterance = "svans-$id"
        tts.voice = voice
        tts.setSpeechRate(TAIL_RATE)
        tts.setPitch(if (voice.name == instructionVoice?.name) TAIL_PITCH_SAME_VOICE else TAIL_PITCH)
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
                // En svans tidsgräns får aldrig stoppa talmotorn medan ett besked spelas.
                if (instructionId == null) engine?.stop()
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
        // Slutanropet postas alltid och körs aldrig inifrån den som klippte svansen. Annars kan
        // mottagaren (dirigenten) lämna in nästa svans mitt i ett pågående anrop här.
        if (done != null) main.post { done(finished) }
    }

    // ------------------------------------------------------------------------------------
    // Gemensamt
    // ------------------------------------------------------------------------------------

    private fun started(utteranceId: String?) {
        if (utteranceId != null && utteranceId == instructionId) {
            // Tiden från begäran till att motorn börjar tala: så länge kan en klippt svans som mest höras.
            log("TAL BÖRJAR: ${SystemClock.elapsedRealtime() - instructionStartedAt} ms efter begäran")
            instructionStartedAt = SystemClock.elapsedRealtime()
        }
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
        } else if (utteranceId == cutTailId) {
            // Talmotorns kvitto på att den klippta svansen har tystnat.
            log("SVANS TYST: ${SystemClock.elapsedRealtime() - cutTailAt} ms efter beskedets begäran")
            cutTailId = null
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
        /** Ägarens värden för beskedet (SvenskTalObserver.kt). */
        const val INSTRUCTION_RATE = 0.95f
        const val INSTRUCTION_PITCH = 1.0f

        /**
         * Guidens röst ska gå att skilja från beskedet med örat (RR-12). Kandidatvärden: något
         * långsammare, och mörkare när samma röst måste användas för båda. Avgörs i lyssningstestet (G4).
         */
        const val TAIL_RATE = 0.90f
        const val TAIL_PITCH = 0.95f
        const val TAIL_PITCH_SAME_VOICE = 0.80f

        /** Röster som motorn uttryckligen läser upp på enheten, t.ex. "sv-se-x-lfs-local". */
        fun isLocalVoiceName(name: String): Boolean = name.lowercase().endsWith("-local")

        /** Röster som motorn hämtar från nätet, t.ex. "sv-se-x-lfs-network". Väljs aldrig. */
        fun isNetworkVoiceName(name: String): Boolean = name.lowercase().endsWith("-network")
    }
}
