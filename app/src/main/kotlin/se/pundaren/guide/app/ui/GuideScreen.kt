package se.pundaren.guide.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import se.pundaren.guide.app.ConvoyPhase
import se.pundaren.guide.convoy.Freshness
import se.pundaren.guide.app.AppGraph
import se.pundaren.guide.app.MapController
import se.pundaren.guide.app.MapStyle
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.GuidancePhase
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.core.geo.Geo
import se.pundaren.guide.truth.core.handduk.DepartureCheck
import se.pundaren.guide.truth.core.platser.PlaceKind
import se.pundaren.guide.truth.device.NavigatorStatus
import se.pundaren.guide.truth.device.RoutePlan
import se.pundaren.guide.truth.device.TestDrive
import se.pundaren.guide.voice.VoiceStatus
import java.time.Instant
import java.time.ZoneId

/** Ett mål som turen kan fortsätta mot efter ett stopp. */
private data class Onward(val point: GeoPoint, val name: String)

/** Ett valt mål som räknas eller har räknats. */
private data class Proposal(
    /** Löpnummer, så att ett sent ruttsvar inte skriver över ett nyare val. */
    val request: Int,
    val name: String,
    val plan: RoutePlan?,
    val error: String?,
    val origin: Origin?,
    /** Målet turen var på väg mot när ett matstopp valdes. Erbjuds igen efter stoppet. */
    val onward: Onward? = null,
)

/** Panelerna som kan ligga över kartan, en i taget. */
private enum class Sheet { DESTINATION, IMPROBABILITY, SNACKS, WRONG_QUESTION, PROTOCOL, HOME, ENCYCLOPEDIA, CONVOY, TRANSIT }

/**
 * Huvudskärmen. Uppifrån och ned: sanningsremsan (sant, turkost), kartan, och ovanpå kartan
 * svansytan (Guiden, lila) och knapparna. Nödkortet lägger sig över allt utom sanningsremsan.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun GuideScreen(graph: AppGraph, map: MapController, onRequestLocation: () -> Unit) {
    val navigator = graph.navigator
    val guidance by navigator.display.guidance.collectAsState()
    val mapData by navigator.display.map.collectAsState()
    val power by navigator.display.power.collectAsState()
    val position by navigator.state.position.collectAsState()
    val safety by navigator.state.safety.collectAsState()
    val status by navigator.status.collectAsState()
    val simulating by navigator.simulating.collectAsState()
    val places by navigator.places.collectAsState()
    val activePackage by navigator.activePackage.collectAsState()
    val track by navigator.track.collectAsState()
    val convoy by graph.convoy.view.collectAsState()
    val voiceStatus by graph.voice.status.collectAsState()
    val shownTail by graph.stage.shown.collectAsState()
    val settings by graph.settings.value.collectAsState()
    val protocol by graph.protocols.view.collectAsState()
    val scope = rememberCoroutineScope()

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var proposal by remember { mutableStateOf<Proposal?>(null) }
    var requests by remember { mutableIntStateOf(0) }
    var onward by remember { mutableStateOf<Onward?>(null) }
    var sourceOf by remember { mutableStateOf<TailCandidate?>(null) }
    var following by remember { mutableStateOf(true) }

    val navigating = guidance.phase == GuidancePhase.NAVIGATING || guidance.phase == GuidancePhase.REROUTING
    /** En tur eller provtur visas: pågående, eller framme men inte kvitterad. */
    val busy = navigating || guidance.phase == GuidancePhase.ARRIVED || simulating
    val bbox = activePackage?.manifest?.omradeBbox
    val mode = if (busy) guidance.travelMode else settings.travelMode

    /**
     * Varifrån rutter och sökningar utgår. En verklig start kräver en aktuell position som ligger
     * inom kartpaketet. Under en provtur gäller provturens läge. Annars utgår allt från orten
     * närmast kartans mitt, så att starten ligger vid en väg, och rutten kan bara köras som provtur.
     */
    fun origin(): Origin? {
        val here = mapData.position
        if (simulating) return here?.let { Origin(it, real = false, name = "provturens läge") }
        // Läses av här och inte ur sammansättningen, så att ett anrop utifrån (kartans långtryck) ser paketet som gäller nu.
        val box = activePackage?.manifest?.omradeBbox
        val real = position.quality != PositionQuality.LOST && here != null &&
            (box == null || (here.lon >= box[0] && here.lon <= box[2] && here.lat >= box[1] && here.lat <= box[3]))
        if (real) return Origin(here!!, real = true, name = null)
        val center = map.center() ?: return null
        val startPlace = places.places.filter { it.kind == PlaceKind.SETTLEMENT && it.subKind in setOf("city", "town", "village") }
            .minByOrNull { Geo.distanceMeters(center, it.point) }
        return Origin(startPlace?.point ?: center, real = false, name = startPlace?.name)
    }

    fun propose(destination: GeoPoint, name: String, then: Onward? = null) {
        val from = origin()
        val request = ++requests
        if (from == null) {
            proposal = Proposal(request, name, null, "Ingen startpunkt än. Vänta på kartan eller på en position.", null, then)
            return
        }
        proposal = Proposal(request, name, null, null, from, then)
        scope.launch {
            val result = navigator.plan(destination, name, from = from.point)
            // Bara om det här fortfarande är det senaste valet, och det inte har avbrutits.
            if (proposal?.request == request) {
                proposal = Proposal(request, name, result.getOrNull(), result.exceptionOrNull()?.message, from, then)
            }
        }
    }

    // Kartan får bara sanningsdata.
    LaunchedEffect(activePackage, settings.mapStyle) {
        activePackage?.let { paket ->
            map.show(paket.fil("karta.pmtiles"), settings.mapStyle)
            paket.manifest.omradeBbox?.let { b -> map.startAt(GeoPoint((b[1] + b[3]) / 2, (b[0] + b[2]) / 2), 8.0) }
        }
    }
    LaunchedEffect(places) { map.setPlaces(places) }
    LaunchedEffect(mapData) {
        map.setRoute(mapData.routeLine)
        map.setDestination(mapData.destination ?: proposal?.plan?.destination)
        map.setPosition(mapData.snappedPosition ?: mapData.position, navigating)
    }
    LaunchedEffect(track) { map.setTrack(track.line) }
    LaunchedEffect(convoy.peer) {
        val peer = convoy.peer
        map.setPeer(peer?.takeIf { it.presentation.showOnMap }?.point, peer?.presentation?.freshness ?: Freshness.STALE)
    }
    LaunchedEffect(proposal) {
        map.setPreview(proposal?.plan?.line.orEmpty())
        map.setDestination(mapData.destination ?: proposal?.plan?.destination)
    }
    // Kartans långtryck anropas långt efter att den här sammansättningen gjordes. Hanteraren hålls
    // därför aktuell, så att den ser om en tur pågår just nu och inte hur det var vid starten.
    val longPress by rememberUpdatedState<(GeoPoint) -> Unit> { point ->
        if (!busy && !safety.emergencyActive) propose(point, "Punkt på kartan")
    }
    LaunchedEffect(Unit) {
        map.onFollowingChanged = { following = it }
        map.onLongPress = { point -> longPress(point) }
    }

    val systemNote = when (val s = status) {
        NavigatorStatus.Starting -> "Laddar offlinepaketet …"
        is NavigatorStatus.NoPackage -> s.reason
        is NavigatorStatus.Failed -> s.reason
        is NavigatorStatus.Ready -> null
    }
    val voiceNote = when (val v = voiceStatus) {
        VoiceStatus.Starting -> "Rösten startar …"
        is VoiceStatus.Failed -> "Ingen röst: ${v.reason}"
        is VoiceStatus.Ready -> if (settings.instructionsMuted) "Körriktningsrösten är avstängd" else null
    }

    Column(Modifier.fillMaxSize().background(GuideColors.Background)) {
        // Sanningsremsan ligger utanför kartans yta. Inget i Box nedan kan täcka den (N-6).
        TruthStrip(TruthStripModel(guidance, position, systemNote, simulating, voiceNote))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { map.view }, modifier = Modifier.fillMaxSize())

            Text(
                "© OpenStreetMap contributors",
                color = GuideColors.OnSurfaceVariant, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(GuideColors.Background.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )

            if (!safety.emergencyActive) {
                // Svansytan: Guidens plats på kartskärmen.
                Column(Modifier.align(Alignment.TopStart).padding(16.dp).widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shownTail?.let { tail -> TailBubble(tail, onLongPress = { sourceOf = tail }) }
                }

                proposal?.let { p ->
                    // Handduken: avfärdskontrollen räknas på de värden som gäller just nu.
                    val check = p.plan?.let { plan ->
                        remember(plan, power, settings.home, position.quality, bbox) {
                            DepartureCheck.build(
                                batteryPercent = power.batteryPercent, charging = power.charging, packageBbox = bbox,
                                origin = plan.origin, destination = plan.destination, home = settings.home,
                                arrivalUtc = Instant.now().plusSeconds(plan.seconds.toLong()),
                                // En start som inte är enhetens verkliga position räknas inte som aktuell position.
                                positionQuality = if (p.origin?.real == true) position.quality else PositionQuality.LOST,
                                zone = ZoneId.systemDefault(),
                            )
                        }
                    }
                    ProposalCard(
                        proposal = p, check = check, mode = mode, replacesRoute = navigating,
                        onStart = { p.plan?.let { onward = p.onward; navigator.start(it) }; proposal = null },
                        onTestDrive = { factor, deviate ->
                            p.plan?.let {
                                onward = p.onward
                                navigator.start(it, TestDrive(factor, if (deviate) setOf(40, 120, 200) else emptySet()))
                            }
                            proposal = null
                        },
                        onCancel = { proposal = null },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Don't Panic: kort tryck ger en rad ur arkivet (via SafetyGate), långt tryck öppnar nödkortet.
                    Box(
                        Modifier.align(Alignment.End).size(132.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(GuideColors.Guide, GuideColors.GuideDim)))
                            .combinedClickable(onClick = { graph.requestWisdom() }, onLongClick = { navigator.setEmergency(true) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DON'T\nPANIC", color = GuideColors.OnGuide, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 26.sp)
                            Text("håll in: nödkort", color = GuideColors.OnGuide, fontSize = 11.sp)
                        }
                    }

                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (busy) {
                            PillButton(if (guidance.phase == GuidancePhase.ARRIVED) "Klar" else "Stopp") {
                                val arrived = guidance.phase == GuidancePhase.ARRIVED
                                val next = onward
                                onward = null
                                // Startpunkten läses av före stoppet: en provtur glömmer sin position när den avslutas.
                                if (arrived && next != null) propose(next.point, next.name)
                                navigator.stop()
                            }
                            if (navigating) PillButton("Fel fråga") { sheet = Sheet.WRONG_QUESTION }
                        } else {
                            PillButton("Vart?", primary = true, enabled = status is NavigatorStatus.Ready) { sheet = Sheet.DESTINATION }
                            PillButton("Lotta mål", enabled = status is NavigatorStatus.Ready) { sheet = Sheet.IMPROBABILITY }
                            PillButton(if (settings.travelMode == TravelMode.FOOT) "Till fots" else "Passagerare") {
                                graph.settings.update { it.copy(travelMode = if (it.travelMode == TravelMode.FOOT) TravelMode.PASSENGER else TravelMode.FOOT) }
                            }
                        }
                        if (guidance.phase != GuidancePhase.ARRIVED) {
                            PillButton("Snacks", enabled = status is NavigatorStatus.Ready) { sheet = Sheet.SNACKS }
                        }
                        PillButton("Hem") { sheet = Sheet.HOME }
                        // Körriktningsrösten av eller på med ett tryck. Sanningsremsan visar ändå varje besked.
                        PillButton(if (settings.instructionsMuted) "Vägröst: av" else "Vägröst: på") {
                            graph.settings.update { it.copy(instructionsMuted = !it.instructionsMuted) }
                        }
                        PillButton(settings.mapStyle.title) {
                            graph.settings.update { it.copy(mapStyle = MapStyle.entries[(it.mapStyle.ordinal + 1) % MapStyle.entries.size]) }
                        }
                        if (!following) PillButton("Centrera") { map.recenter() }
                        if (!navigator.hasLocationPermission()) PillButton("Tillåt position") { onRequestLocation() }
                        PillButton("Protokoll") { sheet = Sheet.PROTOCOL }
                        PillButton(
                            when {
                                convoy.peerEmergencyAtUtc != null -> "Convoy: NÖD"
                                safety.convoyContactLost -> "Convoy: ingen kontakt"
                                convoy.phase == ConvoyPhase.SHARING -> "Convoy: delar"
                                else -> "Convoy"
                            },
                            primary = convoy.peerEmergencyAtUtc != null || safety.convoyContactLost,
                        ) { sheet = Sheet.CONVOY }
                        // Encyklopedin nås bara när ingen tur pågår. Babel-Stoner och reglagen är egna flikar.
                        if (!busy) PillButton("Encyklopedi") { sheet = Sheet.ENCYCLOPEDIA }
                        // Kollektivtrafiken hämtas från Trafiklab över nätet, bara när den här knappen används.
                        PillButton("Kollektivtrafik") { sheet = Sheet.TRANSIT }
                    }
                }
            } else {
                EmergencyCardView(
                    position = mapData.position, snapshot = position, home = settings.home, batteryPercent = safety.batteryPercent,
                    peer = convoy.peer?.point, peerAgeSeconds = convoy.peer?.presentation?.ageSeconds?.toInt(),
                    onReadAloud = { lines -> graph.voice.speakInstruction(lines.joinToString(" ")) },
                    onClose = { graph.voice.stopInstruction(); navigator.setEmergency(false) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // Encyklopedin är stängd så länge en tur pågår.
    if (busy && sheet == Sheet.ENCYCLOPEDIA) sheet = null
    // Nödläget stänger alla paneler: nödkortet ska aldrig ligga under något annat.
    if (!safety.emergencyActive) when (sheet) {
        Sheet.DESTINATION -> DestinationDialog(
            places = places, near = mapData.position ?: map.center(),
            onPick = { place -> sheet = null; propose(place.point, place.name) },
            onDismiss = { sheet = null },
        )
        Sheet.IMPROBABILITY -> ImprobabilityDialog(
            graph = graph, origin = origin(), mode = mode, places = places,
            onGo = { place -> sheet = null; propose(place.point, place.name) },
            onDismiss = { sheet = null },
        )
        Sheet.SNACKS -> SnackDialog(
            graph = graph, origin = origin(), destination = mapData.destination.takeIf { navigating },
            destinationName = guidance.destinationName.takeIf { navigating }, mode = mode, places = places,
            onGo = { place ->
                sheet = null
                // Väljs ett matstopp under en tur erbjuds det ursprungliga målet igen efter stoppet.
                val then = mapData.destination.takeIf { navigating }?.let { Onward(it, guidance.destinationName ?: "målet") }
                propose(place.point, place.name, then)
            },
            onDismiss = { sheet = null },
        )
        Sheet.WRONG_QUESTION -> WrongQuestionDialog(guidance, onDismiss = { sheet = null })
        Sheet.PROTOCOL -> ProtocolDialog(
            view = protocol, savedCount = remember(protocol) { graph.protocols.saved().size }, directory = graph.protocols.path,
            onDismiss = { sheet = null },
        )
        Sheet.HOME -> HomeDialog(
            home = settings.home, position = mapData.position, positionIsReal = !simulating, track = track,
            wayBack = remember(track, mapData.position) { navigator.wayBack() }, routeAllowed = status is NavigatorStatus.Ready,
            onRouteHome = { sheet = null; settings.home?.let { propose(it, "Hem") } },
            onSaveHere = { mapData.position?.let { here -> graph.settings.update { it.copy(home = here) } } },
            onShowTrack = { sheet = null; map.showLine(track.line) },
            onNewTrack = { navigator.newTrack() },
            onDismiss = { sheet = null },
        )
        Sheet.CONVOY -> ConvoyDialog(graph, own = mapData.position, onShowPeer = { map.showPoint(it) }, onDismiss = { sheet = null })
        Sheet.ENCYCLOPEDIA -> EncyclopediaDialog(
            graph = graph, position = mapData.position.takeIf { !simulating && position.quality != PositionQuality.LOST }, places = places,
            onDismiss = { sheet = null },
        )
        Sheet.TRANSIT -> TransitDialog(
            graph = graph, position = mapData.position.takeIf { !simulating && position.quality != PositionQuality.LOST },
            onDismiss = { sheet = null },
        )
        null -> {}
    }
    sourceOf?.let { tail -> SourceDialog(tail, graph.archive.get(tail.lineId), onDismiss = { sourceOf = null }) }
}

/** Guidens replik. Lila, rundad, och bara här. Långt tryck visar källan (E-1). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TailBubble(tail: TailCandidate, onLongPress: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(28.dp)).background(GuideColors.SurfaceHigh.copy(alpha = 0.94f))
            .combinedClickable(onClick = {}, onLongClick = onLongPress).padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text("GUIDEN · ${originLabel(tail.origin)} · ${tail.lineId}", color = GuideColors.Guide, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(tail.text, color = GuideColors.GuideAlt, fontSize = 20.sp, lineHeight = 26.sp)
        Text("håll in för källa", color = GuideColors.OnSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun ProposalCard(
    proposal: Proposal, check: DepartureCheck?, mode: TravelMode, replacesRoute: Boolean,
    onStart: () -> Unit, onTestDrive: (factor: Int, deviate: Boolean) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier,
) {
    val plan = proposal.plan
    val real = proposal.origin?.real == true
    Column(
        modifier.padding(16.dp).widthIn(max = 560.dp).clip(RoundedCornerShape(28.dp)).background(GuideColors.Surface)
            .verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(proposal.name, color = GuideColors.OnSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        when {
            proposal.error != null -> Text("Ingen rutt: ${proposal.error}", color = GuideColors.Error, fontSize = 16.sp)
            plan == null -> Text("Räknar rutten på enheten …", color = GuideColors.OnSurfaceVariant, fontSize = 16.sp)
            else -> {
                Text(
                    "${Geo.formatDistance(plan.distanceMeters)} · ${durationText(plan.seconds)} · " +
                        (if (mode == TravelMode.FOOT) "till fots" else "som passagerare") + " · ${plan.steps} steg",
                    color = GuideColors.OnSurface, fontSize = 18.sp,
                )
                if (!real) {
                    Text(
                        "Ingen aktuell position inom kartpaketet. Rutten är räknad från ${proposal.origin?.name ?: "kartans mitt"} och kan bara köras som provtur.",
                        color = GuideColors.OnSurfaceVariant, fontSize = 14.sp,
                    )
                }
                if (replacesRoute) Text("Den pågående turen avbryts när den här startar.", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
                proposal.onward?.let { Text("Efter stoppet erbjuds turen mot ${it.name} igen.", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp) }
                check?.let { TowelCheck(it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (plan != null && real) PillButton("Starta", primary = true, onClick = onStart)
            PillButton("Avbryt", onClick = onCancel)
        }
        if (plan != null) {
            // Provtur: simulerad position längs rutten, för att pröva appen utan att röra sig.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Provtur") { onTestDrive(1, false) }
                PillButton("Provtur ×8") { onTestDrive(8, false) }
                PillButton("Avvikelseprov") { onTestDrive(4, true) }
            }
        }
    }
}

@Composable
fun PillButton(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) GuideColors.Guide else GuideColors.SurfaceHigh,
            contentColor = if (primary) GuideColors.OnGuide else GuideColors.Guide,
        ),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}
