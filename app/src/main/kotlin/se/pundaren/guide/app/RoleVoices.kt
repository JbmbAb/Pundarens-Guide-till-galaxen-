package se.pundaren.guide.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import se.pundaren.guide.engine.galax.Cue
import se.pundaren.guide.engine.galax.Role
import se.pundaren.guide.engine.galax.clipName
import se.pundaren.guide.voice.GuideVoice
import se.pundaren.guide.voice.VoiceStatus
import java.io.File
import kotlin.coroutines.resume

/**
 * Röstgränssnittet för rollerna. En roll är en figur, inte en teknik: samma replik kan komma från en
 * inspelad skådespelare, från en talsyntes som körs på enheten eller från enhetens egen talmotor.
 * Källorna prövas i ordning, och den första som har repliken läser den.
 *
 * Inget här rör beskeden. Rollrösterna får bara tala när ingen rutt är aktiv (se [AppGraph.speakCue]).
 */
interface CueVoice {
    /** Kort namn för loggen. */
    val name: String

    /** Sant om källan kan läsa just den här repliken nu. */
    fun has(cue: Cue): Boolean

    /** Läser repliken och väntar. Sant om den lästes färdigt, falskt om den klipptes eller inte kunde börja. */
    suspend fun speak(cue: Cue): Boolean

    fun stop()
}

/**
 * Förinspelade repliker: riktig komisk tajming, när den finns. En fil som heter som replikens
 * [clipName] går före all talsyntes. Filerna söks först i appens egen mapp på enheten
 * (`roster/`, så att nya inspelningar kan läggas dit utan ny version) och sedan bland appens tillgångar.
 */
class RecordedVoice(private val context: Context, private val log: (String) -> Unit) : CueVoice {
    override val name = "inspelning"

    private val folder = File(context.getExternalFilesDir(null) ?: context.filesDir, FOLDER)
    private val bundled: Set<String> = runCatching { context.assets.list(FOLDER)?.toSet() }.getOrNull().orEmpty()
    private var player: MediaPlayer? = null
    private var ended: ((Boolean) -> Unit)? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private fun file(cue: Cue): File? {
        val clip = cue.clipName ?: return null
        return EXTENSIONS.map { File(folder, "$clip.$it") }.firstOrNull { it.isFile }
    }

    private fun asset(cue: Cue): String? {
        val clip = cue.clipName ?: return null
        return EXTENSIONS.map { "$clip.$it" }.firstOrNull { it in bundled }
    }

    override fun has(cue: Cue): Boolean = file(cue) != null || asset(cue) != null

    override suspend fun speak(cue: Cue): Boolean {
        stop()
        return suspendCancellableCoroutine { waiting ->
            val done = { finished: Boolean -> if (waiting.isActive) waiting.resume(finished) }
            val media = MediaPlayer()
            try {
                media.setAudioAttributes(attributes)
                val file = file(cue)
                if (file != null) {
                    media.setDataSource(file.path)
                } else {
                    context.assets.openFd("$FOLDER/${asset(cue)}").use { media.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                }
                media.setOnPreparedListener { it.start() }
                media.setOnCompletionListener { finish(media, finished = true) }
                media.setOnErrorListener { _, what, _ -> log("RÖST inspelningen ${cue.clipName} gick inte att spela ($what)"); finish(media, finished = false); true }
                player = media
                ended = done
                media.prepareAsync()
            } catch (e: Exception) {
                log("RÖST inspelningen ${cue.clipName} gick inte att öppna: ${e.javaClass.simpleName}")
                player = media
                ended = done
                finish(media, finished = false)
            }
            waiting.invokeOnCancellation { stop() }
        }
    }

    private fun finish(media: MediaPlayer, finished: Boolean) {
        if (player !== media) return
        val done = ended
        player = null
        ended = null
        runCatching { media.release() }
        done?.invoke(finished)
    }

    override fun stop() {
        player?.let { finish(it, finished = false) }
    }

    companion object {
        const val FOLDER = "roster"
        val EXTENSIONS = listOf("ogg", "mp3", "m4a", "wav")
    }
}

/**
 * Enhetens egen talmotor: reserven som alltid finns. Rollerna skiljs åt med takt och tonhöjd, vilket
 * räcker för att höra att någon annan talar men inte gör någon till skådespelare.
 */
class DeviceVoice(private val voice: GuideVoice) : CueVoice {
    override val name = "enhetens talsyntes"
    private var count = 0

    /** Kandidatvärden tills ägaren har lyssnat: takt och tonhöjd. */
    private fun profile(role: Role?): Pair<Float, Float> = when (role) {
        Role.CEREMONY -> 0.74f to 0.68f
        Role.PANIC -> 1.22f to 1.18f
        Role.HELPFUL -> 1.08f to 1.5f
        Role.BABEL -> 0.96f to 1.0f
        Role.MEMBER -> 1.0f to 1.12f
        Role.MONKEYS -> 1.35f to 1.9f
        Role.OFFICIAL, null -> 0.88f to 0.85f
    }

    override fun has(cue: Cue): Boolean = cue.role != null && voice.status.value is VoiceStatus.Ready

    override suspend fun speak(cue: Cue): Boolean {
        val (rate, pitch) = profile(cue.role)
        // Versaler är skrik på skärmen men riskerar att bokstaveras av talmotorn.
        val letters = cue.text.filter { it.isLetter() }
        val shouted = letters.isNotEmpty() && letters.count { it.isUpperCase() } > letters.length / 2
        return suspendCancellableCoroutine { waiting ->
            voice.speakTail("roll-${++count}", if (shouted) cue.text.lowercase() else cue.text, 60_000, rate, pitch) { finished ->
                if (waiting.isActive) waiting.resume(finished)
            }
            waiting.invokeOnCancellation { voice.stopTail() }
        }
    }

    override fun stop() {
        voice.stopTail()
    }
}
