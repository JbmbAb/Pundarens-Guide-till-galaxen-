package se.pundaren.guide.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import se.pundaren.guide.app.AppGraph
import se.pundaren.guide.engine.galax.PanicReply
import se.pundaren.guide.online.RouteSamples
import se.pundaren.guide.online.Sight
import se.pundaren.guide.online.SightKind
import se.pundaren.guide.online.WikiSummary
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.core.platser.Place
import se.pundaren.guide.truth.core.platser.PlaceKind
import kotlin.math.roundToInt

/*
 * Sevärt längs vägen: det man annars aldrig hade hittat. Grottor, vattenfall, ruiner, bunkrar,
 * övergivna platser och märklig natur nära rutten eller nära där man står, med verklig omväg räknad av
 * ruttmotorn på enheten (samma mekanik som Snacks). Fynden hämtas över nätet när man trycker Sök.
 *
 * Fakta om platsen står i sanningens färg. Guidens utlåtande är lila och är Guidens, inte fakta.
 */

/** Ett fynd med restid dit och omväg, där ruttmotorn kunde räkna dem. */
data class SightStop(val sight: Sight, val secondsThere: Double?, val detourSeconds: Double?, val metersAway: Double)

private sealed interface SightState {
    data object Idle : SightState
    data object Working : SightState
    data class Found(val stops: List<SightStop>, val considered: Int) : SightState
    data class Failed(val message: String) : SightState
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SightsDialog(
    graph: AppGraph, origin: Origin?, destination: GeoPoint?, destinationName: String?, routeLine: List<GeoPoint>, mode: TravelMode,
    onGo: (Place) -> Unit, onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var minutes by remember { mutableIntStateOf(20) }
    var kinds by remember { mutableStateOf(SightKind.entries.toSet() - SightKind.ATTRACTION) }
    var state by remember { mutableStateOf<SightState>(SightState.Idle) }
    var open by remember { mutableStateOf<String?>(null) }

    fun search() {
        val from = origin ?: return
        state = SightState.Working
        open = null
        scope.launch {
            // Längs rutten när en finns, annars längs fågelvägen till målet, annars runt där man står.
            val line = when {
                destination != null && routeLine.size >= 2 -> routeLine
                destination != null -> listOf(from.point, destination)
                else -> listOf(from.point)
            }.map { it.lat to it.lon }
            val found = graph.online.sights(RouteSamples.along(line), kinds)
            val sights = (found as? se.pundaren.guide.app.Fetched.Ok)?.value
            if (sights == null) {
                state = SightState.Failed((found as se.pundaren.guide.app.Fetched.Failed).message)
                return@launch
            }
            // De intressantaste och närmaste först; ruttmotorn räknar sedan restid för de fyrtio bästa.
            val near = sights.map { s -> s to line.minOf { RouteSamples.meters(it, s.lat to s.lon) } }
                .sortedWith(compareByDescending<Pair<Sight, Double>> { it.first.interest }.thenBy { it.second }).take(40)
            val points = near.map { GeoPoint(it.first.lat, it.first.lon) }
            val there = graph.navigator.travelSeconds(from.point, points + listOfNotNull(destination), mode)
            val direct = if (destination != null) there.lastOrNull() else null
            val onward = if (destination != null && direct != null) graph.navigator.travelSecondsTo(points, destination, mode) else null
            val max = minutes * 60.0
            val stops = near.mapIndexed { i, (sight, meters) ->
                val t = there.getOrNull(i)
                val detour = if (t != null && direct != null) onward?.getOrNull(i)?.let { (t + it - direct).coerceAtLeast(0.0) } else null
                SightStop(sight, t, detour, meters)
            }.filter { stop ->
                // Går restiden inte att räkna (platsen ligger utanför kartpaketet) får den vara med ändå.
                val cost = stop.detourSeconds ?: stop.secondsThere.takeIf { destination == null }
                cost == null || cost <= max
            }.sortedWith(compareByDescending<SightStop> { it.sight.interest }.thenBy { it.detourSeconds ?: it.secondsThere ?: Double.MAX_VALUE })
            graph.log.line("SEVÄRT $minutes min, $mode: ${sights.size} fynd, ${stops.size} inom gränsen")
            state = SightState.Found(stops, sights.size)
        }
    }

    Panel(onDismiss) {
        Text("Sevärt längs vägen", color = GuideColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Det ni annars aldrig hade hittat.", color = GuideColors.GuideAlt, fontSize = 15.sp, fontStyle = FontStyle.Italic)
        if (origin == null) {
            Text("Ingen startpunkt än. Vänta på kartan eller på en position.", color = GuideColors.Error, fontSize = 15.sp)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SightKind.entries.forEach { kind ->
                    PillButton(kind.title, primary = kind in kinds) { kinds = if (kind in kinds) kinds - kind else kinds + kind }
                }
            }
            Text(if (destination != null) "Omväg på väg till ${destinationName ?: "målet"}, högst" else "Restid dit, högst", color = GuideColors.OnSurface, fontSize = 16.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10, 20, 30, 45, 60).forEach { m -> PillButton("$m min", primary = m == minutes) { minutes = m } }
                PillButton("Sök", primary = true, enabled = state != SightState.Working && kinds.isNotEmpty()) { search() }
            }
            when (val s = state) {
                SightState.Idle -> {}
                SightState.Working -> Text("Letar och räknar omvägar …", color = GuideColors.OnSurfaceVariant, fontSize = 15.sp)
                is SightState.Failed -> Text(s.message, color = GuideColors.Error, fontSize = 15.sp)
                is SightState.Found -> {
                    Text(
                        if (s.stops.isEmpty()) "Inget inom ${minutes} minuter. Prova längre omväg eller fler slag." else "${s.stops.size} fynd inom ${minutes} minuter.",
                        color = GuideColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    )
                    Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.stops.forEach { stop ->
                            SightCard(graph, stop, expanded = open == stop.sight.id, onToggle = { open = if (open == stop.sight.id) null else stop.sight.id }) {
                                onGo(Place(stop.sight.id, stop.sight.name, PlaceKind.OTHER, stop.sight.kind.name.lowercase(), GeoPoint(stop.sight.lat, stop.sight.lon), null))
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { PillButton("Stäng", onClick = onDismiss) }
    }
}

internal fun sightLine(stop: SightStop): String {
    val parts = mutableListOf(stop.sight.kind.one)
    stop.secondsThere?.let { parts += "${durationText(it)} dit" }
    stop.detourSeconds?.let { parts += "omväg ${durationText(it)}" }
    if (stop.secondsThere == null) parts += "${(stop.metersAway / 1000).let { if (it < 10) "%.1f".format(it).replace('.', ',') else it.roundToInt().toString() }} km från vägen, utanför kartpaketet"
    stop.sight.elevation?.let { parts += "$it m ö.h." }
    return parts.joinToString(" · ")
}

/** Besökskortet: namn, slag och omväg, och när det öppnas text och bild, vägen dit och Guidens utlåtande. */
@Composable
private fun SightCard(graph: AppGraph, stop: SightStop, expanded: Boolean, onToggle: () -> Unit, onGo: () -> Unit) {
    val scope = rememberCoroutineScope()
    var summary by remember(stop.sight.id) { mutableStateOf<WikiSummary?>(null) }
    var image by remember(stop.sight.id) { mutableStateOf<ImageBitmap?>(null) }
    var asked by remember(stop.sight.id) { mutableStateOf(false) }
    var ruling by remember(stop.sight.id) { mutableStateOf<PanicReply?>(null) }
    var judging by remember(stop.sight.id) { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GuideColors.SurfaceHigh).clickable {
            onToggle()
            val article = stop.sight.wikipedia
            if (!asked && article != null) {
                asked = true
                scope.launch {
                    summary = graph.online.wikiSummary(article)
                    summary?.imageUrl?.let { url -> image = graph.online.image(url)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                }
            }
        }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stop.sight.name, color = GuideColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(sightLine(stop), color = GuideColors.Truth, fontSize = 14.sp)
        if (!expanded) {
            stop.sight.description?.let { Text(it, color = GuideColors.OnSurfaceVariant, fontSize = 14.sp, maxLines = 2) }
        } else {
            image?.let {
                Image(it, contentDescription = stop.sight.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(14.dp)))
            }
            val text = summary?.text ?: stop.sight.description
            when {
                text != null -> Text(text, color = GuideColors.OnSurface, fontSize = 15.sp)
                stop.sight.wikipedia != null && summary == null -> Text("Hämtar beskrivningen …", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
                else -> Text("Ingen har skrivit något om den här platsen. Det är ofta ett gott tecken.", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Räkna rutten dit", primary = true, onClick = onGo)
                PillButton(if (judging) "Guiden överlägger …" else "Guidens utlåtande", enabled = !judging) {
                    judging = true
                    scope.launch {
                        try { ruling = graph.dontPanicChosen("Vi har hittat en plats längs vägen: ${stop.sight.name} (${stop.sight.kind.one.lowercase()}). Ska vi åka dit?") } finally { judging = false }
                    }
                }
            }
            ruling?.let { r ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GuideColors.Surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    r.authority?.let { Text(it, color = GuideColors.Guide, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Text(r.headline, color = GuideColors.Guide, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    r.lines.forEach { Text(it, color = GuideColors.GuideAlt, fontSize = 15.sp) }
                }
            }
        }
    }
}
