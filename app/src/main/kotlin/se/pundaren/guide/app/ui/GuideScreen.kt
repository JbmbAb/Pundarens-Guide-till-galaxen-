package se.pundaren.guide.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import se.pundaren.guide.app.AppGraph
import se.pundaren.guide.app.MapController
import se.pundaren.guide.app.MapStyle
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.GuidancePhase
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.core.geo.Geo
import se.pundaren.guide.truth.device.NavigatorStatus
import se.pundaren.guide.truth.device.RoutePlan
import se.pundaren.guide.truth.device.TestDrive
import se.pundaren.guide.voice.VoiceStatus
import kotlin.math.roundToInt

/** Ett valt mål som räknas eller har räknats. */
private data class Proposal(val name: String, val plan: RoutePlan?, val error: String?, val fromRealPosition: Boolean)

/**
 * Huvudskärmen. Uppifrån och ned: sanningsremsan (sant, turkost), kartan, och ovanpå kartan
 * svansytan (Guiden, lila) och knapparna. Nödkortet lägger sig över allt utom sanningsremsan.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GuideScreen(graph: AppGraph, map: MapController, onRequestLocation: () -> Unit) {
    val navigator = graph.navigator
    val guidance by navigator.display.guidance.collectAsState()
    val mapData by navigator.display.map.collectAsState()
    val position by navigator.state.position.collectAsState()
    val safety by navigator.state.safety.collectAsState()
    val status by navigator.status.collectAsState()
    val simulating by navigator.simulating.collectAsState()
    val places by navigator.places.collectAsState()
    val activePackage by navigator.activePackage.collectAsState()
    val voiceStatus by graph.voice.status.collectAsState()
    val shownTail by graph.stage.shown.collectAsState()
    val settings by graph.settings.value.collectAsState()
    val scope = rememberCoroutineScope()

    var choosingDestination by remember { mutableStateOf(false) }
    var proposal by remember { mutableStateOf<Proposal?>(null) }
    var showDrive by remember { mutableStateOf(false) }
    var sourceOf by remember { mutableStateOf<TailCandidate?>(null) }
    var following by remember { mutableStateOf(true) }

    val navigating = guidance.phase == GuidancePhase.NAVIGATING || guidance.phase == GuidancePhase.REROUTING

    fun propose(destination: GeoPoint, name: String) {
        val real = position.quality != PositionQuality.LOST && mapData.position != null
        val origin = if (real) mapData.position else map.center()
        proposal = Proposal(name, null, null, real)
        scope.launch {
            val result = navigator.plan(destination, name, from = origin)
            proposal = Proposal(name, result.getOrNull(), result.exceptionOrNull()?.message, real)
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
    LaunchedEffect(proposal) {
        map.setPreview(proposal?.plan?.line.orEmpty())
        map.setDestination(mapData.destination ?: proposal?.plan?.destination)
    }
    LaunchedEffect(Unit) {
        map.onFollowingChanged = { following = it }
        map.onLongPress = { point -> if (!navigating && !safety.emergencyActive) propose(point, "Punkt på kartan") }
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
        is VoiceStatus.Ready -> if (settings.instructionsMuted) "Beskedsrösten är avstängd" else null
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
                // Svansytan: Guidens enda plats på skärmen.
                Column(Modifier.align(Alignment.TopStart).padding(16.dp).widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shownTail?.let { tail -> TailBubble(tail, onLongPress = { sourceOf = tail }) }
                }

                proposal?.let { p ->
                    ProposalCard(
                        name = p.name, plan = p.plan, error = p.error, mode = settings.travelMode, fromRealPosition = p.fromRealPosition,
                        onStart = { p.plan?.let { navigator.start(it) }; proposal = null },
                        onTestDrive = { factor, deviate ->
                            p.plan?.let { navigator.start(it, TestDrive(factor, if (deviate) setOf(40, 120, 200) else emptySet())) }
                            proposal = null
                        },
                        onCancel = { proposal = null },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                Row(
                    Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (navigating || guidance.phase == GuidancePhase.ARRIVED || simulating) {
                        PillButton(if (guidance.phase == GuidancePhase.ARRIVED) "Klar" else "Stopp") { navigator.stop() }
                    } else {
                        PillButton("Vart?", primary = true, enabled = status is NavigatorStatus.Ready) { choosingDestination = true }
                        PillButton(if (settings.travelMode == TravelMode.FOOT) "Till fots" else "Passagerare") {
                            graph.settings.update { it.copy(travelMode = if (it.travelMode == TravelMode.FOOT) TravelMode.PASSENGER else TravelMode.FOOT) }
                        }
                    }
                    PillButton(settings.mapStyle.title) {
                        graph.settings.update { it.copy(mapStyle = MapStyle.entries[(it.mapStyle.ordinal + 1) % MapStyle.entries.size]) }
                    }
                    if (!following) PillButton("Centrera") { map.recenter() }
                    if (!navigator.hasLocationPermission()) PillButton("Tillåt position") { onRequestLocation() }
                    PillButton("Drift") { showDrive = true }
                }

                // Don't Panic: kort tryck ger en rad ur arkivet (via SafetyGate), långt tryck öppnar nödkortet.
                Box(
                    Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp).size(132.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(GuideColors.Guide, GuideColors.GuideDim)))
                        .combinedClickable(onClick = { graph.requestWisdom() }, onLongClick = { navigator.setEmergency(true) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DON'T\nPANIC", color = GuideColors.OnGuide, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 26.sp)
                        Text("håll in: nödkort", color = GuideColors.OnGuide, fontSize = 11.sp)
                    }
                }
            } else {
                EmergencyCardView(
                    position = mapData.position, snapshot = position, home = settings.home, batteryPercent = safety.batteryPercent,
                    onReadAloud = { lines -> graph.voice.speakInstruction(lines.joinToString(" ")) },
                    onClose = { graph.voice.stopInstruction(); navigator.setEmergency(false) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (choosingDestination) {
        DestinationDialog(
            places = places, near = mapData.position ?: map.center(),
            onPick = { place -> choosingDestination = false; propose(place.point, place.name) },
            onDismiss = { choosingDestination = false },
        )
    }
    if (showDrive) {
        DriveDialog(
            graph = graph, settings = settings, status = status, voiceStatus = voiceStatus, position = mapData.position, mapCenter = map.center(),
            onShowSource = { sourceOf = it }, onDismiss = { showDrive = false },
        )
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
    name: String, plan: RoutePlan?, error: String?, mode: TravelMode, fromRealPosition: Boolean,
    onStart: () -> Unit, onTestDrive: (factor: Int, deviate: Boolean) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(
        modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(28.dp)).background(GuideColors.Surface).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(name, color = GuideColors.OnSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        when {
            error != null -> Text("Ingen rutt: $error", color = GuideColors.Error, fontSize = 16.sp)
            plan == null -> Text("Räknar rutten på enheten …", color = GuideColors.OnSurfaceVariant, fontSize = 16.sp)
            else -> {
                val minutes = (plan.seconds / 60).roundToInt()
                Text(
                    "${Geo.formatDistance(plan.distanceMeters)} · ${if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"} · " +
                        (if (mode == TravelMode.FOOT) "till fots" else "som passagerare") + " · ${plan.steps} steg",
                    color = GuideColors.OnSurface, fontSize = 18.sp,
                )
                if (!fromRealPosition) {
                    Text("Ingen aktuell position. Rutten är räknad från kartans mitt och kan bara köras som provtur.", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (plan != null) {
                if (fromRealPosition) PillButton("Starta", primary = true, onClick = onStart)
                PillButton("Provtur") { onTestDrive(1, false) }
                PillButton("Provtur ×8") { onTestDrive(8, false) }
                PillButton("Avvikelseprov") { onTestDrive(4, true) }
            }
            PillButton("Avbryt", onClick = onCancel)
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
