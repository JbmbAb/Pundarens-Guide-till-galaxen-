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
import se.pundaren.guide.archive.api.ArchiveEntry
import se.pundaren.guide.archive.api.ArchiveReader
import se.pundaren.guide.archive.core.ArchiveImporter
import se.pundaren.guide.archive.core.ArchiveSources
import se.pundaren.guide.archive.core.InMemoryArchiveStore
import se.pundaren.guide.babel.BabelGame
import se.pundaren.guide.conductor.ConductorLoop
import se.pundaren.guide.conductor.ConductorRecord
import se.pundaren.guide.conductor.ConductorSettings
import se.pundaren.guide.conductor.TailConductor
import se.pundaren.guide.encyclopedia.ApprovedLexicon
import se.pundaren.guide.engine.DefaultIllogicalEngine
import se.pundaren.guide.engine.LineBank
import se.pundaren.guide.engine.api.EngineContext
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.engine.galax.Cue
import se.pundaren.guide.engine.galax.DontPanic
import se.pundaren.guide.engine.galax.PanicMemory
import se.pundaren.guide.engine.galax.PanicReply
import se.pundaren.guide.engine.galax.PromiseRegistry
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

    /** Babel-Stoner som spel (etapp 7). Använder bara godkända regler och rader ur arkivet. */
    val babelGame = BabelGame(archive)

    /** Encyklopedin (etapp 7): egna fältanteckningar och arkivets godkända lexikon. */
    val fieldNotes = FieldNoteStore(File(app.filesDir, "anteckningar/anteckningar.tsv"), log::line)
    val lexicon: List<ArchiveEntry> = ApprovedLexicon.entries(archive)

    /** Expeditionsprotokollet (etapp 5). Fakta ur händelsebussen, satir ur det Guiden faktiskt sa. */
    val protocols = ProtocolKeeper(File(app.getExternalFilesDir(null) ?: app.filesDir, "protokoll"), log::line)

    /** Convoy för två (etapp 6). Det enda i appen som använder nätet. */
    val convoy = ConvoyController(scope, ::convoyFix, log::line)

    /** Bara en verklig position med känd noggrannhet får delas. En provtur delar ingenting. */
    private fun convoyFix(): OwnFix? {
        if (navigator.simulating.value) return null
        val point = navigator.display.map.value.position ?: return null
        val quality = navigator.state.position.value
        return OwnFix(point, quality.accuracyMeters ?: return null, quality.fixAgeSeconds ?: return null)
    }

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
        // Nödkortet sänder nödsignalen till den andra, om en delning pågår (07, 3.6).
        scope.launch {
            navigator.state.safety.map { it.emergencyActive }.distinctUntilChanged().collect { if (it) convoy.sendEmergency() }
        }
        // Konvojens läge går in i sanningskärnan: bryts kontakten tiger Guiden (06, R-09).
        scope.launch {
            convoy.view.collect { v ->
                navigator.setConvoy(v.phase == ConvoyPhase.SHARING, v.peer?.point, v.peer?.presentation?.ageSeconds?.toInt())
            }
        }
        // Notisen står kvar så länge delningen pågår, och håller delningen igång när appen ligger i bakgrunden (07, T-09).
        scope.launch {
            convoy.view.map { if (it.phase == ConvoyPhase.SHARING) it.expiresAtUtc else null }.distinctUntilChanged().collect { until ->
                if (until != null) ConvoyShareService.start(app, until, navigator.hasLocationPermission(), log::line) else ConvoyShareService.stop(app)
            }
        }
        ConvoyShareService.onStopRequested = { convoy.requestStop() }
        scope.launch { navigator.prepare() }
    }

    /** Don't Panic, kort tryck: en rad ur arkivet, genom SafetyGate som allt annat från Guiden. */
    fun requestWisdom() {
        val now = SystemClock.elapsedRealtime()
        val navigation = navigator.state.navigation.value
        val context = EngineContext(seed, navigation.travelMode, conductor.memory, navigating = navigation.activeRoute)
        val candidate = engine.wisdom("BEGARAN-${++wisdomRequests}", now, context)
        if (candidate == null) log.line("VISDOM: inga rader kvar att välja bland") else conductor.onRequested(candidate, now)
    }

    private var panicMemory = PanicMemory()
    private var galaxRequests = 0

    /**
     * DON'T PANIC på startsidan: ett galaktiskt beslut om det användaren skrev, eller dagens beslut om
     * rutan är tom. Texten loggas inte och lämnar inte enheten; bara beslutets slag och id skrivs i loggen.
     */
    fun dontPanic(input: String): PanicReply {
        val reply = DontPanic.react(input, settings.value.value.absurdityPercent, seed + ++galaxRequests, panicMemory)
        panicMemory = reply.memory
        log.line("PANIK ${reply.kind} ${reply.decreeId ?: "-"} (${reply.source?.label ?: "allvar"})")
        return reply
    }

    /** Löftesregistret: löftets text tas inte emot. Bara antalet sparas, på enheten. */
    fun registerPromise(): PromiseRegistry.Receipt {
        val receipt = PromiseRegistry.register(settings.value.value.promiseCount, seed + ++galaxRequests)
        settings.update { it.copy(promiseCount = receipt.count) }
        log.line("LÖFTE ${receipt.number}${if (receipt.bonus.isNotEmpty()) " med bonus" else ""}")
        return receipt
    }

    /** Sant när Guidens röst får läsa upp en text från startsidan: rösten finns och ingen rutt är aktiv. */
    fun canReadAloud(): Boolean =
        voice.status.value is se.pundaren.guide.voice.VoiceStatus.Ready && !navigator.state.navigation.value.activeRoute

    /** Läser upp ett beslut med Guidens röst. Aldrig under en aktiv rutt, där beskeden har företräde. */
    fun readAloud(text: String) {
        if (!canReadAloud()) return
        voice.speakTail("galax-${++galaxRequests}", text, 120_000) {}
    }

    fun stopReading() {
        voice.stopTail()
        recorded.stop()
    }

    private val recorded = RecordedVoice(app, log::line)

    /**
     * Rollernas röstkällor, i den ordning de prövas: en inspelad replik går före talsyntesen. En
     * talsyntes som körs på enheten (Piper) hör hemma mellan de två när den byggs in.
     */
    private val cast: List<CueVoice> = listOf(recorded, DeviceVoice(voice))

    /**
     * Läser upp en regisserad rad med rollens röst och väntar tills den är färdigläst. Svarar falskt om
     * raden klipptes eller aldrig fick börja. Avbryts anropet tystnar rösten. Aldrig under en aktiv rutt,
     * där beskeden har företräde.
     */
    suspend fun speakCue(cue: Cue): Boolean {
        if (navigator.state.navigation.value.activeRoute) return false
        val source = cast.firstOrNull { it.has(cue) } ?: return false
        return source.speak(cue)
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
        ConvoyShareService.onStopRequested = null
        ConvoyShareService.stop(app)
        scope.cancel()
        navigator.close()
        voice.close()
    }
}

object BuildInfo {
    const val VERSION = "0.7-galaxen"
}
