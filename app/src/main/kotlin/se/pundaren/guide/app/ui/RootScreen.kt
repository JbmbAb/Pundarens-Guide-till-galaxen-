package se.pundaren.guide.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import se.pundaren.guide.app.AppGraph
import se.pundaren.guide.app.MapController
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.truth.api.GuidancePhase

/**
 * Appens rot: fyra flikar. Startsidan med DON'T PANIC är hemma, kartan är en av Guidens förmågor.
 *
 * Kartskärmen ligger alltid kvar underst, så att kartan, rutten och positionen lever vidare när en
 * annan flik visas. Pågår en tur visas sanningsremsan överst också på de andra flikarna, så att
 * beskedet aldrig göms av Guiden. Nödläget byter alltid till kartskärmen, där nödkortet ligger.
 */
@Composable
fun RootScreen(graph: AppGraph, map: MapController, onRequestLocation: () -> Unit) {
    val navigator = graph.navigator
    val guidance by navigator.display.guidance.collectAsState()
    val position by navigator.state.position.collectAsState()
    val mapData by navigator.display.map.collectAsState()
    val safety by navigator.state.safety.collectAsState()
    val simulating by navigator.simulating.collectAsState()
    val status by navigator.status.collectAsState()
    val voiceStatus by graph.voice.status.collectAsState()
    val settings by graph.settings.value.collectAsState()

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var sourceOf by remember { mutableStateOf<TailCandidate?>(null) }

    val routeShown = guidance.phase != GuidancePhase.IDLE || simulating
    // Babel-Stoner är stängd så länge en tur pågår (greppen stannar utanför navigatorn).
    if (routeShown && tab == Tab.BABEL) tab = Tab.MAP
    LaunchedEffect(safety.emergencyActive) { if (safety.emergencyActive) tab = Tab.MAP }
    // En tur som startar ska synas: kartan kommer fram av sig själv.
    LaunchedEffect(routeShown) { if (routeShown) tab = Tab.MAP }

    Column(Modifier.fillMaxSize().background(GuideColors.Background)) {
        Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.navigationBars)) {
            GuideScreen(graph, map, onRequestLocation)

            if (tab != Tab.MAP && !safety.emergencyActive) {
                // Täcker kartskärmen helt och släpper inte igenom tryck till den.
                Column(Modifier.fillMaxSize().background(GuideColors.Background).pointerInput(Unit) {}.imePadding()) {
                    if (routeShown) TruthStrip(TruthStripModel(guidance, position, systemNote = null, simulated = simulating, voiceNote = null))
                    else Spacer(Modifier.statusBarsPadding())
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            graph = graph, settings = settings, routeActive = routeShown,
                            onEmergency = { tab = Tab.MAP; navigator.setEmergency(true) },
                        )
                        Tab.BABEL -> BabelScreen(graph)
                        Tab.DRIVE -> DriveScreen(
                            graph = graph, settings = settings, status = status, voiceStatus = voiceStatus,
                            position = mapData.position.takeIf { !simulating }, mapCenter = map.center(),
                            onShowSource = { sourceOf = it },
                        )
                        Tab.MAP -> {}
                    }
                }
            }
        }
        if (!safety.emergencyActive) {
            TabBar(
                selected = tab, enabled = { it != Tab.BABEL || !routeShown },
                onSelect = { next -> if (next != tab) graph.stopReading(); tab = next },
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
    sourceOf?.let { tail -> SourceDialog(tail, graph.archive.get(tail.lineId), onDismiss = { sourceOf = null }) }
}
