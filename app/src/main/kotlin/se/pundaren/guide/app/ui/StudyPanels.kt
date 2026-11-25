package se.pundaren.guide.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.pundaren.guide.app.AppGraph
import se.pundaren.guide.babel.BabelGame
import se.pundaren.guide.babel.QuestionLedger
import se.pundaren.guide.babel.SeriousnessCheck
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.core.geo.Geo
import se.pundaren.guide.truth.core.platser.PlaceIndex
import se.pundaren.guide.truth.core.platser.PlaceKind
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Etapp 7: Babel-Stoner och encyklopedin. De nås bara när ingen tur pågår (byggplanen:
 * greppen används aldrig i navigatorn). Babel-Stoners repliker är godkända arkivrader och visas i
 * Guidens lila med id och klass. Frågebokföringen och fältanteckningarna är inte satir och är inte lila.
 */

/** Babel-Stoner: spelet (satir), frågebokföringen (analys) och Fekala Haverikommissionen. Skilda funktioner (02 B-1, B-2). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BabelScreen(graph: AppGraph) {
    var pane by remember { mutableStateOf(0) }
    // Rymden hör till satiren. Frågebokföringen är analys och får stå mot ren botten.
    Box(Modifier.fillMaxSize()) {
        if (pane != 1) Cosmos(galaxyAt = Offset(0.5f, 0.68f))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Babel-Stoner", color = GuideColors.Guide, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Spelet", primary = pane == 0) { graph.stopReading(); pane = 0 }
                    PillButton("Frågebokföring", primary = pane == 1) { graph.stopReading(); pane = 1 }
                    PillButton("Bajsanalysatorn", primary = pane == 2) { pane = 2 }
                }
                when (pane) {
                    0 -> GamePane(graph.babelGame, seed = { System.nanoTime() }, log = graph.log::line)
                    1 -> LedgerPane()
                    else -> FecalPane(graph)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamePane(game: BabelGame, seed: () -> Long, log: (String) -> Unit) {
    var opponent by remember { mutableStateOf(BabelGame.Opponent.COSMIC_CONFUSION) }
    var question by remember { mutableStateOf("") }
    var round by remember { mutableStateOf<BabelGame.Round?>(null) }
    var serious by remember { mutableStateOf(false) }

    Text(
        "Satir. Ställ en fråga, så slingrar sig Babel-Stoner med ett grepp i taget. Namnge greppet och du får tillbaka din fråga. " +
            "Varje replik är en godkänd rad ur arkivet.",
        color = GuideColors.OnSurfaceVariant, fontSize = 14.sp,
    )
    Text(
        "${game.grips.size} av ${game.grips.size + game.unplayable.size} grepp går att spela. De andra saknar godkända rader i arkivet.",
        color = GuideColors.OnSurfaceVariant, fontSize = 13.sp,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BabelGame.Opponent.entries.forEach { o ->
            val count = game.gripsOf(o).size
            PillButton("${o.title} ($count)", primary = o == opponent, enabled = count > 0) { opponent = o; round = null }
        }
    }
    OutlinedTextField(
        value = question, onValueChange = { question = it; serious = false }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        label = { Text("Din fråga") },
    )
    PillButton("Fråga", primary = round == null, enabled = question.isNotBlank()) {
        // Allvar före satir (02 B-7): vid tecken på hot eller våld börjar inget spel.
        serious = SeriousnessCheck.isSerious(question)
        round = if (serious) null else game.start(question, opponent, seed())
        round?.let { log("BABEL spel börjat mot ${it.opponent.title}, frö ${it.seed}") }
    }
    if (serious) Text(SeriousnessCheck.MESSAGE, color = GuideColors.Error, fontSize = 17.sp, fontWeight = FontWeight.Bold)

    round?.let { r ->
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(GuideColors.SurfaceHigh).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("BABEL-STONER · ${r.label.uppercase()} · ${originLabel(r.line.originClass)} · ${r.line.id}", color = GuideColors.Guide, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(r.line.text, color = GuideColors.GuideAlt, fontSize = 18.sp, lineHeight = 24.sp)
        }
        if (!r.won) {
            Text("Replik ${r.replies}. Vilket grepp var det?", color = GuideColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            r.options.forEach { option ->
                Text(
                    option.text, color = GuideColors.OnSurface, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GuideColors.SurfaceHigh)
                        .clickable { round = game.guess(r, option.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        } else {
            Text("Rätt: ${r.grip.rule.id}, ${r.grip.rule.text}.", color = GuideColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Din fråga är återställd: «${r.question}»", color = GuideColors.OnSurface, fontSize = 16.sp)
            Text("Poäng: ${r.replies} ${if (r.replies == 1) "replik" else "repliker"}. Lägre är bättre.", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LedgerPane() {
    // Texten hålls bara i minnet medan panelen är öppen. Ingenting sparas och ingenting lämnar enheten (02 B-5).
    var pasted by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    Text(
        "Klistra in ett samtal med en replik per rad (\"Namn: text\"). Bokföringen listar varje fråga och raden där den ställdes. " +
            "Samma regler gäller alla talare. Den beskriver text, inte människor.",
        color = GuideColors.OnSurfaceVariant, fontSize = 14.sp,
    )
    Text(
        "Texten behandlas här på enheten och sparas inte. Klistra bara in andras meddelanden om du får det.",
        color = GuideColors.OnSurfaceVariant, fontSize = 13.sp,
    )
    OutlinedTextField(
        value = pasted, onValueChange = { pasted = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
        label = { Text("Samtalet") },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Bokför", primary = true, enabled = pasted.isNotBlank()) { result = QuestionLedger.render(QuestionLedger.analyze(pasted)) }
        PillButton("Rensa", enabled = pasted.isNotEmpty() || result != null) { pasted = ""; result = null }
    }
    result?.let { Text(it, color = GuideColors.OnSurface, fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp) }
}

/**
 * Encyklopedin: egna fältanteckningar (det användaren själv har sett) och arkivets godkända lexikon.
 * Inget här är genererat. Redaktionens strykning visar två av anteckningens egna ord; tryck på en
 * anteckning för att se originalet.
 */
@Composable
fun EncyclopediaDialog(graph: AppGraph, position: GeoPoint?, places: PlaceIndex, onDismiss: () -> Unit) {
    val notes by graph.fieldNotes.notes.collectAsState()
    var draft by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(setOf<String>()) }
    val stamp = remember { DateTimeFormatter.ofPattern("d/M HH.mm") }
    Panel(onDismiss) {
        Text("Encyklopedin", color = GuideColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Egna fältanteckningar. Skriv vad du hittar, inte vad du antar. Anteckningarna sparas bara här på enheten.", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
        OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp), label = { Text("Ny fältanteckning") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Spara", primary = true, enabled = draft.isNotBlank()) {
                // Platsen följer bara med när enheten har en verklig position; närmaste ort inom två kilometer anges som stöd.
                val near = position?.let { here -> places.nearest(here, setOf(PlaceKind.SETTLEMENT), 2_000.0, 1).firstOrNull()?.first?.name }
                if (graph.fieldNotes.add(draft, position, near)) draft = ""
            }
            Text(
                if (position != null) "Sparas med positionen ${Geo.formatDegreesMinutes(position)}." else "Ingen aktuell position: anteckningen sparas utan plats.",
                color = GuideColors.OnSurfaceVariant, fontSize = 13.sp,
            )
        }
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (notes.isEmpty()) Text("Inga fältanteckningar än.", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
            notes.asReversed().forEach { note ->
                val shown = note.id in open
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GuideColors.SurfaceHigh)
                        .clickable { open = if (shown) open - note.id else open + note.id }.padding(12.dp),
                ) {
                    Text(
                        stamp.format(note.atUtc.atZone(ZoneId.systemDefault())) + (note.placeName?.let { " · nära $it" } ?: "") +
                            (note.point?.let { " · ${Geo.formatDegreesMinutes(it)}" } ?: ""),
                        color = GuideColors.OnSurfaceVariant, fontSize = 12.sp,
                    )
                    if (shown) {
                        Text(note.text, color = GuideColors.OnSurface, fontSize = 16.sp)
                    } else {
                        Text(note.editorsCut, color = GuideColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Redaktionens strykning. Tryck för originalet.", color = GuideColors.OnSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
            if (graph.lexicon.isNotEmpty()) {
                Text("Lexikon ur arkivet", color = GuideColors.Guide, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Godkända poster med id och klass. De är satir och hör till Guiden.", color = GuideColors.OnSurfaceVariant, fontSize = 12.sp)
                graph.lexicon.forEach { entry ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GuideColors.SurfaceHigh).padding(12.dp)) {
                        Text("${originLabel(entry.originClass)} · ${entry.id} · ${entry.visibility.name}", color = GuideColors.Guide, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(entry.text, color = GuideColors.GuideAlt, fontSize = 15.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { PillButton("Stäng", onClick = onDismiss) }
    }
}
