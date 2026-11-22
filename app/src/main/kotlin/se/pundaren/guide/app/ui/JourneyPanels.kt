package se.pundaren.guide.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import se.pundaren.guide.app.AppGraph
import se.pundaren.guide.app.ProtocolView
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.GuidanceSnapshot
import se.pundaren.guide.truth.api.TravelMode
import se.pundaren.guide.truth.core.drift.ImprobabilityDrive
import se.pundaren.guide.truth.core.geo.Geo
import se.pundaren.guide.truth.core.handduk.CheckStatus
import se.pundaren.guide.truth.core.handduk.DepartureCheck
import se.pundaren.guide.truth.core.platser.Place
import se.pundaren.guide.truth.core.platser.PlaceIndex
import se.pundaren.guide.truth.core.protokoll.Perspective
import se.pundaren.guide.truth.core.protokoll.ProtocolLayer
import se.pundaren.guide.truth.core.protokoll.WrongQuestion
import se.pundaren.guide.truth.core.snacks.OpenState
import se.pundaren.guide.truth.core.snacks.SnackFinder
import se.pundaren.guide.truth.core.snacks.SnackStop
import se.pundaren.guide.truth.core.spar.TrackSnapshot
import se.pundaren.guide.truth.core.spar.WayBack
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/*
 * Etapp 5: varje skämt ska bära last. Panelerna här visar de sanna värdena under skämten.
 * Allt som står i dem kommer ur sanningslagret (ruttmotorn, offlinepaketet, batteriet, solen),
 * och ingenting är lila: lila är Guiden, och Guiden skriver inte här.
 */

/** Varifrån en rutt eller en sökning utgår. */
data class Origin(
    val point: GeoPoint,
    /** Sant när punkten är enhetens aktuella verkliga position inom kartpaketet. */
    val real: Boolean,
    /** Var punkten kommer ifrån när den inte är verklig, t.ex. en ort eller "provturens läge". */
    val name: String?,
)

internal fun durationText(seconds: Double): String {
    val minutes = (seconds / 60.0).roundToInt()
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

/** Handduken: avfärdskontrollens fem punkter med sina sanna värden. */
@Composable
fun TowelCheck(check: DepartureCheck) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            check.headline, color = if (check.secured) GuideColors.OnSurface else GuideColors.Error,
            fontSize = 17.sp, fontWeight = FontWeight.Bold,
        )
        check.items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    when (item.status) { CheckStatus.OK -> "✓"; CheckStatus.WARNING -> "!"; CheckStatus.UNKNOWN -> "?" },
                    color = when (item.status) {
                        CheckStatus.OK -> GuideColors.OnSurface
                        CheckStatus.WARNING -> GuideColors.Error
                        CheckStatus.UNKNOWN -> GuideColors.OnSurfaceVariant
                    },
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp),
                )
                Text("${item.title}: ${item.value}", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinuteChoice(label: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Text(label, color = GuideColors.OnSurface, fontSize = 16.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { m -> PillButton("$m min", primary = m == selected) { onSelect(m) } }
    }
}

private sealed interface DriveState {
    data object Idle : DriveState
    data object Working : DriveState
    data class Found(val pick: ImprobabilityDrive.Pick, val minutes: Int) : DriveState
    data class Empty(val considered: Int, val minutes: Int) : DriveState
}

/**
 * Osannolikhetsdriften: ett slumpat men verkligt nåbart mål inom vald restid. Kandidaterna väljs
 * ur offlinepaketets platser, restiderna räknas av ruttmotorn på enheten och målet lottas bland dem
 * som faktiskt nås i tid. Fröet loggas, så att samma lottning går att göra om.
 */
@Composable
fun ImprobabilityDialog(
    graph: AppGraph, origin: Origin?, mode: TravelMode, places: PlaceIndex,
    onGo: (Place) -> Unit, onDismiss: () -> Unit,
) {
    val options = if (mode == TravelMode.FOOT) listOf(10, 20, 30, 60) else listOf(15, 30, 45, 60, 90)
    var minutes by remember { mutableIntStateOf(if (mode == TravelMode.FOOT) 20 else 30) }
    var state by remember { mutableStateOf<DriveState>(DriveState.Idle) }
    val scope = rememberCoroutineScope()

    fun draw() {
        val from = origin ?: return
        val chosen = minutes
        state = DriveState.Working
        scope.launch {
            val seed = System.nanoTime()
            val maxSeconds = chosen * 60.0
            val started = System.nanoTime()
            val candidates = ImprobabilityDrive.candidates(places, from.point, maxSeconds, mode, seed)
            val times = graph.navigator.travelSeconds(from.point, candidates.map { it.point }, mode)
            val pick = ImprobabilityDrive.pick(candidates, times, maxSeconds, seed)
            graph.log.line(
                "DRIFT frö $seed, $chosen min, $mode: ${candidates.size} kandidater, ${pick?.reachable ?: 0} nås i tid, " +
                    "lottat ${pick?.place?.name ?: "inget"}${pick?.let { " (${durationText(it.seconds)})" } ?: ""}, " +
                    "${(System.nanoTime() - started) / 1_000_000} ms",
            )
            state = if (pick != null) DriveState.Found(pick, chosen) else DriveState.Empty(candidates.size, chosen)
        }
    }

    Panel(onDismiss) {
        Text("Osannolikhetsdriften", color = GuideColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Ett slumpat mål som ruttmotorn verkligen når inom tiden. Lottningen sker på enheten.",
            color = GuideColors.OnSurfaceVariant, fontSize = 14.sp,
        )
        when {
            origin == null -> Text("Ingen startpunkt än. Vänta på kartan eller på en position.", color = GuideColors.Error, fontSize = 15.sp)
            places.places.isEmpty() -> Text("Offlinepaketet har ingen platsfil, så det finns inga mål att lotta bland.", color = GuideColors.Error, fontSize = 15.sp)
            else -> {
                if (!origin.real) {
                    Text("Ingen aktuell position inom kartpaketet. Restiderna räknas från ${origin.name ?: "kartans mitt"}.", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
                }
                MinuteChoice(if (mode == TravelMode.FOOT) "Restid till fots, högst" else "Restid som passagerare, högst", options, minutes) { minutes = it }
                when (val s = state) {
                    DriveState.Idle -> {}
                    DriveState.Working -> Text("Räknar restider på enheten …", color = GuideColors.OnSurfaceVariant, fontSize = 15.sp)
                    is DriveState.Empty -> Text(
                        if (s.considered == 0) "Inga platser i offlinepaketet ligger inom ${s.minutes} min härifrån."
                        else "Ingen av ${s.considered} prövade platser nås inom ${s.minutes} min.",
                        color = GuideColors.OnSurface, fontSize = 16.sp,
                    )
                    is DriveState.Found -> Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GuideColors.SurfaceHigh).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(s.pick.place.name, color = GuideColors.OnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${kindLabel(s.pick.place)} · ${durationText(s.pick.seconds)} enligt ruttmotorn · " +
                                "${Geo.formatDistance(Geo.distanceMeters(origin.point, s.pick.place.point))} fågelvägen",
                            color = GuideColors.OnSurface, fontSize = 15.sp,
                        )
                        Text(
                            "${s.pick.reachable} av ${s.pick.considered} prövade platser nås inom ${s.minutes} min.",
                            color = GuideColors.OnSurfaceVariant, fontSize = 14.sp,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(if (state is DriveState.Idle) "Lotta mål" else "Lotta igen", primary = state !is DriveState.Found, enabled = state != DriveState.Working) { draw() }
                    (state as? DriveState.Found)?.let { found -> PillButton("Visa rutten", primary = true) { onGo(found.pick.place) } }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { PillButton("Stäng", onClick = onDismiss) }
    }
}

private data class SnackResult(val stops: List<SnackStop>, val minutes: Int, val withDestination: Boolean, val considered: Int)

/**
 * Sannolikhet för snacks: matställen inom vald omväg (eller restid när inget mål finns) och om de
 * har öppet vid ankomst. Saknas öppettider står det "okända"; ingenting gissas.
 */
@Composable
fun SnackDialog(
    graph: AppGraph, origin: Origin?, destination: GeoPoint?, destinationName: String?, mode: TravelMode, places: PlaceIndex,
    onGo: (Place) -> Unit, onDismiss: () -> Unit,
) {
    var minutes by remember { mutableIntStateOf(10) }
    var result by remember { mutableStateOf<SnackResult?>(null) }
    var working by remember { mutableStateOf(false) }

    // Räknas om när omvägen ändras, inte varje gång positionen rör sig.
    LaunchedEffect(minutes, origin != null) {
        val from = origin ?: return@LaunchedEffect
        working = true
        val max = minutes * 60.0
        val started = System.nanoTime()
        val candidates = SnackFinder.candidates(places, from.point, destination, max, mode)
        val there = graph.navigator.travelSeconds(from.point, candidates.map { it.point } + listOfNotNull(destination), mode)
        val direct = if (destination != null) there.lastOrNull() else null
        // Utan väg till målet går ingen omväg att räkna; då visas restiden till matstället i stället.
        val onward = if (destination != null && direct != null) graph.navigator.travelSecondsTo(candidates.map { it.point }, destination, mode) else null
        val stops = SnackFinder.rank(candidates, there.take(candidates.size), onward, direct, max, LocalDateTime.now())
        graph.log.line(
            "SNACKS $minutes min, $mode, mål ${destinationName ?: "saknas"}: ${candidates.size} kandidater, ${stops.size} inom gränsen, " +
                "${(System.nanoTime() - started) / 1_000_000} ms. ${SnackFinder.summary(stops)}",
        )
        result = SnackResult(stops, minutes, onward != null, candidates.size)
        working = false
    }

    Panel(onDismiss) {
        Text("Sannolikhet för snacks", color = GuideColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (origin == null) {
            Text("Ingen startpunkt än. Vänta på kartan eller på en position.", color = GuideColors.Error, fontSize = 15.sp)
        } else {
            if (!origin.real) Text("Räknat från ${origin.name ?: "kartans mitt"}.", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
            MinuteChoice(
                if (destination != null) "Omväg på väg till ${destinationName ?: "målet"}, högst" else "Restid dit, högst",
                listOf(5, 10, 20, 30), minutes,
            ) { minutes = it }
            val r = result
            when {
                working || r == null -> Text("Räknar restider på enheten …", color = GuideColors.OnSurfaceVariant, fontSize = 15.sp)
                else -> {
                    Text(SnackFinder.summary(r.stops), color = GuideColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.stops.forEach { stop ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GuideColors.SurfaceHigh)
                                    .clickable { onGo(stop.place) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(stop.place.name, color = GuideColors.OnSurface, fontSize = 18.sp)
                                Text(
                                    "${kindLabel(stop.place)} · ${durationText(stop.secondsToPlace)} dit" +
                                        (stop.detourSeconds?.let { " · omväg ${durationText(it)}" } ?: "") + " · " + openText(stop.openAtArrival),
                                    color = GuideColors.OnSurfaceVariant, fontSize = 14.sp,
                                )
                                // Perspektivvyn: omvägens andel av universums ålder, uträknad på riktigt.
                                stop.detourSeconds?.takeIf { it >= 30.0 }?.let { detour ->
                                    Text(
                                        "Omvägen är ${Perspective.scientific(Perspective.shareOfUniverseAge(detour))} av universums ålder.",
                                        color = GuideColors.OnSurfaceVariant, fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                    if (r.stops.isNotEmpty()) Text("Tryck på ett matställe för att räkna rutten dit.", color = GuideColors.OnSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { PillButton("Stäng", onClick = onDismiss) }
    }
}

private fun openText(state: OpenState) = when (state) {
    OpenState.OPEN -> "öppet vid ankomst"
    OpenState.CLOSED -> "stängt vid ankomst"
    OpenState.UNKNOWN -> "öppettider okända"
}

/** Fel fråga: sanna fakta som ingen bad om, plus perspektivvyn för restiden som är kvar. */
@Composable
fun WrongQuestionDialog(guidance: GuidanceSnapshot, onDismiss: () -> Unit) {
    Panel(onDismiss) {
        Text("Fel fråga", color = GuideColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Sanna svar på frågor ingen ställde. Räknade ur ruttmotorns steg just nu.", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
        WrongQuestion.facts(guidance.stepsRemaining, guidance.leftTurnsRemaining, guidance.rightTurnsRemaining, guidance.roundaboutsRemaining)
            .forEach { Text(it, color = GuideColors.OnSurface, fontSize = 18.sp) }
        Text("Höjdmeter kvar: okänt. Offlinepaketet saknar höjddata.", color = GuideColors.OnSurfaceVariant, fontSize = 15.sp)
        guidance.secondsRemaining?.takeIf { it >= 30.0 }?.let { seconds ->
            Text("Perspektiv", color = GuideColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Återstående restid: ${Perspective.describe(seconds)}", color = GuideColors.OnSurface, fontSize = 16.sp)
            Text("Universums ålder enligt Planck 2018: 13,787 miljarder år.", color = GuideColors.OnSurfaceVariant, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { PillButton("Stäng", onClick = onDismiss) }
    }
}

/**
 * Expeditionsprotokollet: resans logg i myndighetsform. Rader märkta SANT kommer ur sanningslagret;
 * rader märkta GUIDEN är satir ur arkivet och är de enda lila raderna.
 */
@Composable
fun ProtocolDialog(view: ProtocolView?, savedCount: Int, directory: String, onDismiss: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val clock = remember { DateTimeFormatter.ofPattern("HH.mm.ss") }
    Panel(onDismiss) {
        Text("Expeditionsprotokoll", color = GuideColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (view == null) {
            Text("Inget protokoll än. Det första upprättas när en tur startar.", color = GuideColors.OnSurfaceVariant, fontSize = 15.sp)
        } else {
            Text(
                "${view.expeditionId} · ${if (view.open) "pågår" else "avslutat"} · tider i ${zone.id}",
                color = GuideColors.OnSurfaceVariant, fontSize = 14.sp,
            )
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                view.entries.forEach { entry ->
                    val stamp = clock.format(entry.atUtc.atZone(zone))
                    when (entry.layer) {
                        ProtocolLayer.TRUTH -> Text(
                            "$stamp  SANT  ${entry.text}", color = GuideColors.OnSurface, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                        )
                        ProtocolLayer.SATIRE -> Text(
                            "$stamp  GUIDEN  «${entry.text}» (rad ${entry.sourceRef})", color = GuideColors.GuideAlt, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
        Text("Sparade protokoll på enheten: $savedCount\n$directory", color = GuideColors.OnSurfaceVariant, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { PillButton("Stäng", onClick = onDismiss) }
    }
}

/**
 * Hem: riktning och avstånd till den sparade hempunkten, rutten dit, och vägen tillbaka längs det
 * egna spåret. Spåret fungerar även där ruttmotorn inte har någon väg.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeDialog(
    home: GeoPoint?, position: GeoPoint?, positionIsReal: Boolean, track: TrackSnapshot, wayBack: WayBack?, routeAllowed: Boolean,
    onRouteHome: () -> Unit, onSaveHere: () -> Unit, onShowTrack: () -> Unit, onNewTrack: () -> Unit, onDismiss: () -> Unit,
) {
    val clock = remember { DateTimeFormatter.ofPattern("d/M HH.mm") }
    Panel(onDismiss) {
        Text("Hem", color = GuideColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        when {
            home == null -> Text("Ingen hempunkt är sparad. Nödkortet kan då inte visa riktning hem.", color = GuideColors.OnSurface, fontSize = 16.sp)
            position == null -> Text("Hem är sparat: ${Geo.formatDegreesMinutes(home)}. Ingen aktuell position, så avståndet går inte att räkna.", color = GuideColors.OnSurface, fontSize = 16.sp)
            else -> Text(
                "Hem ligger ${Geo.formatDistance(Geo.distanceMeters(position, home))} åt ${Geo.compassSv(Geo.bearingDegrees(position, home))}, fågelvägen" +
                    (if (positionIsReal) "." else " (räknat från en simulerad position)."),
                color = GuideColors.OnSurface, fontSize = 18.sp,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (home != null) PillButton("Rutt hem", primary = true, enabled = routeAllowed, onClick = onRouteHome)
            PillButton(if (home == null) "Spara här som hem" else "Flytta hem hit", enabled = position != null && positionIsReal, onClick = onSaveHere)
        }

        Text("Eget spår", color = GuideColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (track.line.size < 2) {
            Text(
                "Inget spår än. Spåret börjar när enheten får en position och sparas bara här på enheten.",
                color = GuideColors.OnSurfaceVariant, fontSize = 14.sp,
            )
        } else {
            Text(
                "${Geo.formatDistance(track.lengthMeters)} sedan ${track.startedUtc?.let { clock.format(it.atZone(ZoneId.systemDefault())) } ?: "okänd tid"}" +
                    (if (track.simulated) " · PROVTUR, simulerat spår som inte sparas" else ""),
                color = GuideColors.OnSurface, fontSize = 16.sp,
            )
            wayBack?.let { back ->
                Text(
                    "Tillbaka längs spåret: ${Geo.formatDistance(back.lengthMeters)} till spårets början." +
                        (back.metersToTrack?.takeIf { it > 30.0 }?.let { " Spåret ligger ${Geo.formatDistance(it)} åt ${Geo.compassSv(back.bearingToTrack ?: 0.0)}." } ?: ""),
                    color = GuideColors.OnSurface, fontSize = 16.sp,
                )
            }
            Text(
                "Spåret är var enheten har varit. Appen intygar inte att vägen tillbaka är farbar eller tillåten.",
                color = GuideColors.OnSurfaceVariant, fontSize = 13.sp,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PillButton("Visa spåret", enabled = track.line.size >= 2, onClick = onShowTrack)
            PillButton("Nytt spår", enabled = track.line.isNotEmpty(), onClick = onNewTrack)
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { PillButton("Stäng", onClick = onDismiss) }
    }
}
