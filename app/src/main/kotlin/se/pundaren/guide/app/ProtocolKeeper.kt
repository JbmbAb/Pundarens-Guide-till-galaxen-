package se.pundaren.guide.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.truth.api.RouteStarted
import se.pundaren.guide.truth.api.TruthEvent
import se.pundaren.guide.truth.core.protokoll.ExpeditionProtocol
import se.pundaren.guide.truth.core.protokoll.ProtocolEntry
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** Protokollet som skärmen visar: det pågående, eller det senast avslutade. */
data class ProtocolView(
    val expeditionId: String,
    val entries: List<ProtocolEntry>,
    /** Sant medan expeditionen pågår och protokollet fortfarande tar emot rader. */
    val open: Boolean,
)

/**
 * För expeditionsprotokollet (byggplanen: "resans riktiga logg i myndighetsform, sparad till arkivet").
 *
 * Ett protokoll per expedition, från `RouteStarted` tills turen kvitteras eller avbryts.
 * Sanningsrader kan bara uppstå ur sanningshändelser ([ExpeditionProtocol.truth]); Guidens repliker
 * förs in som satir med arkivets rad-id. Lagren blandas aldrig.
 *
 * Protokollet skrivs till en textfil efter varje ny rad, så att det överlever att appen stängs.
 * Arkivdatabasen i 04 (tabellen `expedition_log`) finns inte än; filen är dess ersättare tills vidare
 * (avvikelse E5-D2). Filen innehåller mål och tider och lämnar inte enheten av sig själv.
 */
class ProtocolKeeper(private val directory: File, private val log: (String) -> Unit) {
    private var current: ExpeditionProtocol? = null

    private val _view = MutableStateFlow<ProtocolView?>(null)
    val view: StateFlow<ProtocolView?> = _view.asStateFlow()

    val path: String get() = directory.absolutePath

    /** En sanningshändelse från bussen. `RouteStarted` börjar ett nytt protokoll. */
    @Synchronized
    fun onEvent(event: TruthEvent) {
        if (event is RouteStarted) {
            close(event.wallClockUtc) // en tur som ersätts av en ny avslutas först
            current = ExpeditionProtocol(event.expeditionId ?: "X-${event.wallClockUtc.toEpochMilli()}")
        }
        val protocol = current ?: return
        val before = protocol.entries.size
        protocol.truth(event)
        if (protocol.entries.size != before) publish(protocol, save = true)
    }

    /** En replik som Guiden faktiskt presenterade under expeditionen. */
    @Synchronized
    fun onPresented(tail: TailCandidate, atUtc: Instant) {
        val protocol = current ?: return
        protocol.satire(atUtc, tail.text, tail.lineId)
        publish(protocol, save = true)
    }

    /** Turen kvitterades eller avbröts. Protokollet avslutas och ligger kvar som fil. */
    @Synchronized
    fun close(atUtc: Instant) {
        val protocol = current ?: return
        protocol.closed(atUtc)
        current = null
        publish(protocol, save = true)
        log("PROTOKOLL ${protocol.expeditionId} avslutat: ${protocol.entries.size} rader, sparat i $directory")
    }

    /** De sparade protokollen, nyast först. */
    fun saved(): List<File> =
        directory.listFiles { f -> f.isFile && f.name.endsWith(".txt") }?.sortedByDescending { it.name }.orEmpty()

    private fun publish(protocol: ExpeditionProtocol, save: Boolean) {
        _view.value = ProtocolView(protocol.expeditionId, protocol.entries, open = !protocol.isClosed)
        if (!save) return
        runCatching {
            directory.mkdirs()
            File(directory, "${protocol.expeditionId}.txt").writeText(protocol.render(ZoneId.systemDefault()))
        }.onFailure { log("PROTOKOLL kunde inte sparas: $it") }
    }
}
