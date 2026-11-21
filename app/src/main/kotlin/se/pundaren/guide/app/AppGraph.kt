package se.pundaren.guide.app

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import se.pundaren.guide.archive.api.ArchiveReader
import se.pundaren.guide.archive.core.ArchiveImporter
import se.pundaren.guide.archive.core.ArchiveSources
import se.pundaren.guide.archive.core.InMemoryArchiveStore
import se.pundaren.guide.conductor.ConductorLoop
import se.pundaren.guide.conductor.ConductorRecord
import se.pundaren.guide.conductor.ConductorSettings
import se.pundaren.guide.conductor.TailConductor
import se.pundaren.guide.engine.DefaultIllogicalEngine
import se.pundaren.guide.engine.LineBank
import se.pundaren.guide.engine.api.EngineContext
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.safetygate.SafetyGate
import se.pundaren.guide.truth.api.GuidancePhase
import se.pundaren.guide.truth.device.OfflineNavigator
import se.pundaren.guide.voice.GuideVoice
import java.io.File
import java.time.Instant

/**
 * Kompositionsroten (03): här, och bara här, möts sanningslagret och tramslagret.
 *
 * ```
 * OfflineNavigator (sanning) ──besked──► GuideVoice kanal B ──► högtalare
 *        │ händelser, läsning
 *        ▼
 * TailConductor ─► DefaultIllogicalEngine ─► SafetyGate ─► GuideStage ─► GuideVoice kanal S
 * ```
 *
 * Det finns ingen pil tillbaka: dirigenten, motorn och scenen får bara navigatorns läsgränssnitt.
 */
class AppGraph(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val log = FileLog(app)
    val settings = AppSettings(app)

    val voice = GuideVoice(app, log::line)
    val navigator = OfflineNavigator(app, voice, log::line)

    val archive: ArchiveReader = InMemoryArchiveStore.of(ArchiveImporter.import(archiveSources()))
    val lineBank: LineBank = LineBank.from(archive)
    private val engine = DefaultIllogicalEngine(lineBank)
    private val gate = SafetyGate()
    val stage = GuideStage(voice, log::line)

    /** Ny seed varje start, så att Guiden inte säger samma sak varje dag. Loggas för att kunna återskapas. */
    private val seed = System.nanoTime()
    private val conductor = TailConductor(engine, gate, navigator.state, stage, seed, ::record)

    private val _history = MutableStateFlow<List<TailCandidate>>(emptyList())
    /** De senast presenterade svansarna, nyast sist. För källvisningen (E-1). */
    val history: StateFlow<List<TailCandidate>> = _history.asStateFlow()

    private var wisdomRequests = 0

    /** Expeditionsprotokollet (etapp 5). Fakta ur händelsebussen, satir ur det Guiden faktiskt sa. */
    val protocols = ProtocolKeeper(File(app.getExternalFilesDir(null) ?: app.filesDir, "protokoll"), log::line)

    init {
        log.line("START ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), app ${BuildInfo.VERSION}")
        log.line("ARKIV ${archive.all().size} poster, radbank ${lineBank.lines.size} rader (${lineBank.excluded.size} uteslutna), seed $seed")
        stage.onFinished = { tailId -> conductor.onTailFinished(tailId, SystemClock.elapsedRealtime()) }
        ConductorLoop.start(scope, conductor, navigator.events, navigator.state, SystemClock::elapsedRealtime)
        scope.launch {
            navigator.events.events.collect {
                log.line("HÄNDELSE ${describe(it)}")
                protocols.onEvent(it)
            }
        }
        // Protokollet avslutas när turen kvitteras eller avbryts (remsan går tillbaka till viloläge).
        scope.launch {
            navigator.display.guidance.map { it.phase }.distinctUntilChanged().collect { phase ->
                if (phase == GuidancePhase.IDLE) protocols.close(Instant.now())
            }
        }
        scope.launch {
            settings.value.collect { s ->
                conductor.settings = ConductorSettings(enabled = s.guideEnabled, chattinessPerMille = s.chattinessPercent * 10)
                navigator.setInstructionsMuted(s.instructionsMuted)
                navigator.setTravelMode(s.travelMode)
                voice.setPreferredVoices(s.instructionVoice, s.tailVoice)
            }
        }
        scope.launch { navigator.prepare() }
    }

    /** Don't Panic, kort tryck: en rad ur arkivet, genom SafetyGate som allt annat från Guiden. */
    fun requestWisdom() {
        val now = SystemClock.elapsedRealtime()
        val context = EngineContext(seed, navigator.state.navigation.value.travelMode, conductor.memory)
        val candidate = engine.wisdom("BEGARAN-${++wisdomRequests}", now, context)
        if (candidate == null) log.line("VISDOM: inga rader kvar att välja bland") else conductor.onRequested(candidate, now)
    }

    private fun record(r: ConductorRecord) {
        when (r) {
            is ConductorRecord.Presented -> {
                _history.update { (it + r.tail).takeLast(30) }
                protocols.onPresented(r.tail, Instant.now())
            }
            is ConductorRecord.Waiting -> log.line("SVANS VÄNTAR (${r.reason}): ${r.tail.lineId}")
            is ConductorRecord.Dropped -> log.line("SVANS STRUKEN (${r.reason}): ${r.tail.lineId}")
            is ConductorRecord.Interrupted, is ConductorRecord.Finished -> {}
        }
    }

    private fun describe(event: se.pundaren.guide.truth.api.TruthEvent): String =
        event.toString().replace(Regex("eventId=[^,]+, monotonicMillis=\\d+, wallClockUtc=[^,]+, expeditionId=[^,]+, "), "")
            .replace(Regex(", schemaVersion=\\d+"), "")

    private fun archiveSources(): ArchiveSources {
        fun read(name: String) = app.assets.open("arkiv/$name").use { it.readBytes().decodeToString() }
        return ArchiveSources(
            quotesJson = read("quotes.json"),
            lexiconJson = read("lexicon.json"),
            rulesJson = read("rules.json"),
            examplesJson = read("examples.json"),
            navigationCommentsJson = read("navigation_comments.json"),
        )
    }

    override fun close() {
        scope.cancel()
        navigator.close()
        voice.close()
    }
}

object BuildInfo {
    const val VERSION = "0.5-etapp5"
}
