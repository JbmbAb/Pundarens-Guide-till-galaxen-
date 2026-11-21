package se.pundaren.guide.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import se.pundaren.guide.app.AppGraph
import se.pundaren.guide.app.BuildInfo
import se.pundaren.guide.app.Settings
import se.pundaren.guide.archive.api.ArchiveEntry
import se.pundaren.guide.archive.api.OriginClass
import se.pundaren.guide.engine.api.TailCandidate
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.PositionSnapshot
import se.pundaren.guide.truth.core.geo.Geo
import se.pundaren.guide.truth.core.nodkort.EmergencyCard
import se.pundaren.guide.truth.core.platser.Place
import se.pundaren.guide.truth.core.platser.PlaceIndex
import se.pundaren.guide.truth.core.platser.PlaceKind
import se.pundaren.guide.truth.device.NavigatorStatus
import se.pundaren.guide.voice.VoiceStatus

/** Byggplanens tre klasser för radernas ursprung, plus reglerna. */
fun originLabel(origin: OriginClass): String = when (origin) {
    OriginClass.ORIGINAL_VERBATIM -> "ORIGINAL"
    OriginClass.APPROVED_EDIT -> "BEARBETAD"
    OriginClass.SYNTHETIC_SATIRE -> "GENERERAD"
    OriginClass.DERIVED_RULE -> "REGEL"
}

@Composable
private fun Panel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 640.dp).clip(RoundedCornerShape(28.dp)).background(GuideColors.Surface).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/** Målsökning i offlinepaketets platser. Ingenting slås upp på nätet. */
@Composable
fun DestinationDialog(places: PlaceIndex, near: GeoPoint?, onPick: (Place) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val hits = remember(query, places) {
        if (query.isBlank()) places.places.filter { it.kind == PlaceKind.SETTLEMENT && it.subKind in setOf("city", "town") }
            .sortedBy { p -> near?.let { Geo.distanceMeters(it, p.point) } ?: 0.0 }.take(30)
        else places.search(query, near = near, limit = 40)
    }
    Panel(onDismiss) {
        Text("Vart?", color = GuideColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Ort eller plats") })
        if (places.places.isEmpty()) {
            Text("Offlinepaketet har ingen platsfil. Håll fingret på kartan för att välja ett mål.", color = GuideColors.OnSurfaceVariant, fontSize = 15.sp)
        } else if (hits.isEmpty()) {
            Text("Ingen plats med det namnet i offlinepaketet.", color = GuideColors.OnSurfaceVariant, fontSize = 15.sp)
        }
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(hits, key = { it.id }) { place ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GuideColors.SurfaceHigh).clickable { onPick(place) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(place.name, color = GuideColors.OnSurface, fontSize = 18.sp)
                        Text(kindLabel(place), color = GuideColors.OnSurfaceVariant, fontSize = 13.sp)
                    }
                    near?.let { Text(Geo.formatDistance(Geo.distanceMeters(it, place.point)), color = GuideColors.OnSurfaceVariant, fontSize = 14.sp) }
                }
            }
        }
        Text("Du kan också hålla fingret på kartan.", color = GuideColors.OnSurfaceVariant, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { PillButton("Stäng", onClick = onDismiss) }
    }
}

private fun kindLabel(p: Place): String = when (p.kind) {
    PlaceKind.SETTLEMENT -> when (p.subKind) { "city" -> "stad"; "town" -> "tätort"; "village" -> "by"; "hamlet" -> "småort"; else -> "plats" }
    PlaceKind.FOOD -> "matställe"
    PlaceKind.SHOP -> "butik"
    PlaceKind.FUEL -> "drivmedel"
    PlaceKind.TOILET -> "toalett"
    PlaceKind.REST_AREA -> "rastplats"
    PlaceKind.INFRASTRUCTURE -> "osynlig infrastruktur"
    PlaceKind.OTHER -> p.subKind
}

/**
 * Nödkortet (Don't Panic, långt tryck). Bara sanna värden, i sanningsfärgen, utan trams och utan
 * animationer. Medan kortet visas är nödläget aktivt och SafetyGate undertrycker all satir.
 */
@Composable
fun EmergencyCardView(
    position: GeoPoint?, snapshot: PositionSnapshot, home: GeoPoint?, batteryPercent: Int,
    onReadAloud: (List<String>) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier,
) {
    val card = EmergencyCard.build(position, snapshot.accuracyMeters, snapshot.fixAgeSeconds, snapshot.quality, home, batteryPercent)
    val lines = card.lines()
    Column(
        modifier.background(GuideColors.Background, RectangleShape).verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("NÖDKORT", color = GuideColors.Truth, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(lines.first(), color = GuideColors.Truth, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
        lines.drop(1).forEach { Text(it, color = GuideColors.Truth, fontSize = 26.sp, lineHeight = 32.sp) }
        Text(
            "Läs upp koordinaterna för larmoperatören. Appen kan inte ringa och har ingen nätanslutning.",
            color = GuideColors.Truth, fontSize = 17.sp,
        )
        // Nödkortet bär bara sanningsfärgen: inga lila knappar här (F-02: lila är Guiden).
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TruthButton("Läs upp") { onReadAloud(lines) }
            TruthButton("Avsluta nödläget", onClick = onClose)
        }
    }
}

/** Knapp i sanningsfärgen med raka hörn, för nödkortet. */
@Composable
private fun TruthButton(text: String, onClick: () -> Unit) {
    Text(
        text, color = GuideColors.Background, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(GuideColors.Truth, RectangleShape).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
    )
}

/** Probability Drive: reglage som bara rör Guiden, plus läget för paket, röst och logg. */
@Composable
fun DriveDialog(
    graph: AppGraph, settings: Settings, status: NavigatorStatus, voiceStatus: VoiceStatus, position: GeoPoint?, mapCenter: GeoPoint?,
    onShowSource: (TailCandidate) -> Unit, onDismiss: () -> Unit,
) {
    val history by graph.history.collectAsState()
    Panel(onDismiss) {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Osannolikhetsdriften", color = GuideColors.Guide, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Reglagen rör bara Guiden. Rutten, beskeden och nödkortet påverkas aldrig.", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)

            SwitchRow("Guiden pratar", settings.guideEnabled) { on -> graph.settings.update { it.copy(guideEnabled = on) } }
            Text("Pratsamhet: ${settings.chattinessPercent} %", color = GuideColors.OnSurface, fontSize = 16.sp)
            Slider(value = settings.chattinessPercent.toFloat(), onValueChange = { v -> graph.settings.update { it.copy(chattinessPercent = v.toInt()) } }, valueRange = 0f..100f)
            SwitchRow("Beskedsrösten läser upp", !settings.instructionsMuted) { on -> graph.settings.update { it.copy(instructionsMuted = !on) } }

            // Röstval: två olika röster gör att det hörs vem som talar (RR-12). Tryck för att byta.
            val voices by graph.voice.voices.collectAsState()
            if (voiceStatus is VoiceStatus.Ready && voices.isNotEmpty()) {
                fun next(current: String) = voices[(voices.indexOf(current) + 1).mod(voices.size)]
                Text("Röster", color = GuideColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("Besked: ${voiceStatus.instructionVoice}") {
                        graph.settings.update { it.copy(instructionVoice = next(voiceStatus.instructionVoice)) }
                    }
                    PillButton("Prov") { graph.voice.speakInstruction("Röstprov. Det här är beskedsrösten.") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("Guiden: ${voiceStatus.tailVoice}") {
                        graph.settings.update { it.copy(tailVoice = next(voiceStatus.tailVoice)) }
                    }
                    // Guidens röstprov är en rad ur arkivet och går genom SafetyGate som allt annat från Guiden.
                    PillButton("Prov") { graph.requestWisdom() }
                }
            }

            Text("Hem", color = GuideColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                settings.home?.let { "Sparat: ${Geo.formatDegreesMinutes(it)}. Används bara för nödkortets riktning och avstånd, och sparas bara på enheten." }
                    ?: "Inte sparat. Nödkortet kan då inte visa riktning hem.",
                color = GuideColors.OnSurfaceVariant, fontSize = 14.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Här är hem", enabled = position != null) { graph.settings.update { it.copy(home = position) } }
                PillButton("Kartans mitt", enabled = mapCenter != null) { graph.settings.update { it.copy(home = mapCenter) } }
                if (settings.home != null) PillButton("Glöm") { graph.settings.update { it.copy(home = null) } }
            }

            Text("Läge", color = GuideColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val pack = when (status) {
                is NavigatorStatus.Ready -> "Offlinepaket ${status.packageVersion}"
                is NavigatorStatus.NoPackage -> status.reason
                is NavigatorStatus.Failed -> status.reason
                NavigatorStatus.Starting -> "Startar …"
            }
            val voice = when (voiceStatus) {
                is VoiceStatus.Ready -> "Talmotor: ${voiceStatus.engine}, röster som läses upp på enheten"
                is VoiceStatus.Failed -> "Röst saknas: ${voiceStatus.reason}"
                VoiceStatus.Starting -> "Rösten startar …"
            }
            Text(
                "$pack\n$voice\nArkiv: ${graph.archive.all().size} poster, radbank ${graph.lineBank.lines.size} rader\nLogg: ${graph.log.path}\nVersion ${BuildInfo.VERSION}",
                color = GuideColors.OnSurfaceVariant, fontSize = 13.sp,
            )

            Text("Senaste repliker", color = GuideColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (history.isEmpty()) Text("Guiden har inte sagt något än.", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
            history.asReversed().take(12).forEach { tail ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GuideColors.SurfaceHigh).clickable { onShowSource(tail) }.padding(12.dp)) {
                    Text("${originLabel(tail.origin)} · ${tail.lineId}", color = GuideColors.Guide, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(tail.text, color = GuideColors.GuideAlt, fontSize = 15.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { PillButton("Stäng", onClick = onDismiss) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = GuideColors.OnSurface, fontSize = 16.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Källan till en replik (E-1): id, klass, upphov, källhänvisning, verifiering och synlighet. */
@Composable
fun SourceDialog(tail: TailCandidate, entry: ArchiveEntry?, onDismiss: () -> Unit) {
    Panel(onDismiss) {
        Text("Källa", color = GuideColors.Guide, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("”${tail.text}”", color = GuideColors.GuideAlt, fontSize = 18.sp, lineHeight = 24.sp)
        val rows = buildList {
            add("Id" to tail.lineId)
            add("Klass" to originLabel(tail.origin))
            if (entry != null) {
                add("Upphov" to entry.author)
                add("Källa" to (entry.sourceRef ?: "ingen källhänvisning"))
                entry.sourceNote?.let { add("Anmärkning" to it) }
                add("Verifiering" to entry.verification.name)
                add("Godkännande" to entry.approval.name)
                add("Synlighet" to entry.visibility.name)
                if (entry.mechanisms.isNotEmpty()) add("Mekanism" to entry.mechanisms.joinToString { "${it.name} ${it.title}" })
            } else add("Arkivpost" to "saknas")
        }
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { (k, v) ->
                Row {
                    Text("$k: ", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(v, color = GuideColors.OnSurface, fontSize = 14.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { PillButton("Stäng", onClick = onDismiss) }
    }
}
