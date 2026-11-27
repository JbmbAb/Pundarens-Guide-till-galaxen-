package se.pundaren.guide.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import se.pundaren.guide.app.AppGraph
import se.pundaren.guide.app.DepartureBoard
import se.pundaren.guide.app.Fetched
import se.pundaren.guide.app.TripSearch
import se.pundaren.guide.online.Departure
import se.pundaren.guide.online.TransitLeg
import se.pundaren.guide.online.TransitTrip
import se.pundaren.guide.truth.api.GeoPoint
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/*
 * Kollektivtrafik från Trafiklab: resa från A till B (ResRobot) och avgångar med förseningar
 * (Trafiklab Realtime). Det här är uppgifter utifrån och visas rakt, i sanningens färg, utan satir.
 * Ingenting hämtas förrän någon trycker på en knapp, och ingenting här rör appens egen rutt.
 */

private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private fun clock(at: LocalDateTime): String = HOUR_MINUTE.format(at)

internal fun minutesText(from: LocalDateTime, to: LocalDateTime): String {
    val minutes = Duration.between(from, to).toMinutes().coerceAtLeast(0)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

@Composable
fun TransitDialog(graph: AppGraph, position: GeoPoint?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var fromHere by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var trips by remember { mutableStateOf<TripSearch?>(null) }
    var board by remember { mutableStateOf<DepartureBoard?>(null) }
    var fetchedAt by remember { mutableStateOf<LocalTime?>(null) }

    val here = position.takeIf { fromHere }
    val hasOrigin = here != null || from.isNotBlank()

    fun <T> fetch(call: suspend () -> Fetched<T>, show: (T) -> Unit) {
        if (busy) return
        busy = true; failure = null; trips = null; board = null
        scope.launch {
            try {
                when (val result = call()) {
                    is Fetched.Ok -> { show(result.value); fetchedAt = LocalTime.now() }
                    is Fetched.Failed -> failure = result.message
                }
            } finally {
                busy = false
            }
        }
    }

    Panel(onDismiss) {
        Text("Kollektivtrafik", color = GuideColors.Truth, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Uppgifterna hämtas från Trafiklab över nätet när du trycker på en knapp. Hållplatsnamnen skickas dit, och din position om du väljer Härifrån.",
            color = GuideColors.OnSurfaceVariant, fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (fromHere && position != null) {
                Text("Från: din position", color = GuideColors.OnSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                PillButton("Skriv hållplats") { fromHere = false }
            } else {
                OutlinedTextField(value = from, onValueChange = { from = it }, singleLine = true, modifier = Modifier.weight(1f), label = { Text("Från hållplats") })
                PillButton("Härifrån", enabled = position != null) { fromHere = true }
            }
        }
        OutlinedTextField(value = to, onValueChange = { to = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Till hållplats") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Sök resa", primary = true, enabled = !busy && hasOrigin && to.isNotBlank()) {
                fetch({ graph.online.trips(from, here, to) }) { trips = it }
            }
            PillButton("Avgångar", enabled = !busy && hasOrigin) {
                fetch({ graph.online.departures(from, here) }) { board = it }
            }
            PillButton("Stäng", onClick = onDismiss)
        }
        if (busy) Text("Hämtar från Trafiklab …", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
        failure?.let { Text(it, color = GuideColors.Error, fontSize = 15.sp) }

        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            trips?.let { found ->
                Text("${found.from} → ${found.to}", color = GuideColors.OnSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (found.trips.isEmpty()) Text("Trafiklab hittade ingen resa.", color = GuideColors.OnSurfaceVariant, fontSize = 15.sp)
                found.trips.forEach { TripCard(it) }
            }
            board?.let { shown ->
                Text("Avgångar från ${shown.stop.name}", color = GuideColors.OnSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                shown.stop.distanceMeters?.let { Text("Närmaste hållplats, $it m bort.", color = GuideColors.OnSurfaceVariant, fontSize = 13.sp) }
                if (shown.departures.isEmpty()) Text("Trafiklab har inga avgångar att visa just nu.", color = GuideColors.OnSurfaceVariant, fontSize = 15.sp)
                shown.departures.forEach { DepartureRow(it) }
            }
            if (trips != null || board != null) {
                fetchedAt?.let { Text("Hämtat kl. ${HOUR_MINUTE.format(it)} från Trafiklab.", color = GuideColors.OnSurfaceVariant, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun TripCard(trip: TransitTrip) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GuideColors.SurfaceHigh).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val changes = when (trip.changes) { 0 -> "utan byte"; 1 -> "1 byte"; else -> "${trip.changes} byten" }
        Text(
            "${clock(trip.departs)}–${clock(trip.arrives)} · ${minutesText(trip.departs, trip.arrives)} · $changes",
            color = GuideColors.Truth, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        )
        trip.legs.forEach { Text(legText(it), color = GuideColors.OnSurface, fontSize = 14.sp) }
    }
}

internal fun legText(leg: TransitLeg): String =
    if (leg.walk) {
        val distance = leg.distanceMeters?.let { "$it m " }.orEmpty()
        "Gå ${distance}till ${leg.to} (${minutesText(leg.departs, leg.arrives)})"
    } else {
        val toward = leg.direction?.takeIf { it.isNotBlank() }?.let { " mot $it" }.orEmpty()
        "${clock(leg.departs)} ${leg.from} → ${clock(leg.arrives)} ${leg.to} · ${leg.line}$toward"
    }

@Composable
private fun DepartureRow(departure: Departure) {
    val color = if (departure.canceled) GuideColors.Error else GuideColors.OnSurface
    Text(departureText(departure), color = color, fontSize = 15.sp)
}

internal fun departureText(d: Departure): String {
    val line = listOf(d.line, d.destination.takeIf { it.isNotBlank() }?.let { "mot $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
    val platform = d.platform?.let { " · läge $it" }.orEmpty()
    val scheduled = clock(d.scheduled)
    val time = when {
        d.canceled -> "INSTÄLLD $scheduled"
        !d.hasRealtime || d.realtime == null -> "$scheduled (tidtabell, ingen prognos)"
        d.delaySeconds >= 60 -> "${clock(d.realtime!!)} (tidtabell $scheduled, +${d.delaySeconds / 60} min)"
        d.delaySeconds <= -60 -> "${clock(d.realtime!!)} (tidtabell $scheduled, ${-d.delaySeconds / 60} min tidig)"
        else -> "$scheduled (i tid)"
    }
    return "$time · $line$platform"
}
