package se.pundaren.guide.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import se.pundaren.guide.app.AppGraph
import se.pundaren.guide.engine.galax.BowelState
import se.pundaren.guide.engine.galax.Conversion
import se.pundaren.guide.engine.galax.Fecal
import se.pundaren.guide.engine.galax.FecalClass
import se.pundaren.guide.engine.galax.Grip
import se.pundaren.guide.engine.galax.Role
import se.pundaren.guide.engine.galax.Scene

/*
 * Fekala Haverikommissionen (Babel-Stoner) och Omvändelseprogrammet (startsidan).
 *
 * Lila är Guiden och satir. Turkost är sant: Bristolskalan, vårdrådet och upplysningen om vad som
 * sparas. Korall är skrik. Den galaktiska klassificeringen och den jordiska hålls isär på skärmen
 * precis som i motorn: ingen kategori översätts till en Bristoltyp.
 */

/**
 * En scen som spelas upp: raderna kommer en i taget med regisserade pauser, och varje roll läses
 * upp med sin egen röst när [speak] är sant. Klipps en replik läses resten inte upp.
 */
@Composable
internal fun SceneLines(graph: AppGraph, scene: Scene, speak: Boolean) {
    var shown by remember(scene) { mutableIntStateOf(0) }
    LaunchedEffect(scene) {
        var voice = speak
        scene.cues.forEachIndexed { i, cue ->
            delay(cue.pauseBeforeMillis.toLong())
            shown = i + 1
            when {
                voice && cue.role != null -> if (!graph.speakCue(cue)) voice = false
                voice -> delay((cue.text.length * 45L).coerceIn(700L, 2600L))
            }
        }
    }
    scene.authority?.let { Text(it.uppercase(), color = GuideColors.OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    Text(scene.title, color = GuideColors.Guide, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Black)
    scene.cues.take(shown).forEachIndexed { i, cue ->
        val role = cue.role
        if (role != null && (cue.direction != null || scene.cues.getOrNull(i - 1)?.role != role)) {
            Text(
                role.title.uppercase() + (cue.direction?.let { " · $it" } ?: ""),
                color = GuideColors.OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp),
            )
        }
        val shouted = cue.text == cue.text.uppercase() && cue.text.any { it.isLetter() }
        Text(
            if (role == null) cue.text else "”${cue.text}”",
            color = when {
                shouted -> GuideColors.Warm
                role == null -> GuideColors.OnSurface
                else -> GuideColors.GuideAlt
            },
            fontSize = 18.sp, lineHeight = 25.sp,
            fontWeight = if (shouted) FontWeight.Black else FontWeight.Normal,
            fontStyle = if (role == Role.CEREMONY) FontStyle.Italic else FontStyle.Normal,
        )
    }
}

private val cardShape = RoundedCornerShape(topStart = 28.dp, topEnd = 44.dp, bottomEnd = 28.dp, bottomStart = 44.dp)

@Composable
private fun Card(accent: androidx.compose.ui.graphics.Color? = null, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(cardShape).background(GuideColors.SurfaceHigh)
            .then(if (accent != null) Modifier.border(2.dp, accent.copy(alpha = 0.6f), cardShape) else Modifier)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** BAJSANALYSATORN 9000. Ingenting som väljs eller skrivs här sparas eller loggas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FecalPane(graph: AppGraph) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<FecalClass?>(null) }
    var report by remember { mutableStateOf<Scene?>(null) }
    var concern by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var bowel by remember { mutableStateOf(BowelState()) }
    var gameScene by remember { mutableStateOf<Scene?>(null) }
    var inquiry by remember { mutableStateOf<Scene?>(null) }
    var voices by rememberSaveable { mutableStateOf(true) }
    val canSpeak = graph.canReadAloud()
    val speak = voices && canSpeak

    fun show(c: FecalClass) {
        graph.stopReading()
        selected = c; report = Fecal.report(c); concern = false; copied = false
    }

    Text(Fecal.AUTHORITY, color = GuideColors.OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Text(Fecal.TITLE, color = GuideColors.Guide, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
    Text(Fecal.INSTITUTE, color = GuideColors.OnSurfaceVariant, fontSize = 13.sp, fontStyle = FontStyle.Italic)

    Text(Fecal.PROMPT, color = GuideColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Fecal.classes.forEach { c ->
            PillButton(if (c.classical) c.name else "${c.name} (tillägg)", primary = c == selected) { show(c) }
        }
    }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), maxLines = 5,
        label = { Text("Eller berätta vad som hände (frivilligt)") },
        supportingText = { Text("Texten läses bara här, för att välja kategori.") },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Analysera", primary = true, enabled = text.isNotBlank()) {
            graph.stopReading()
            copied = false
            // Allvar före satir: blod, smärta och liknande ger ingen haverirapport, bara verklighetsankaret.
            if (Fecal.isConcern(text)) {
                concern = true; anchor = true; selected = null; report = null
            } else {
                concern = false
                val c = Fecal.classify(text)
                selected = c
                report = if (c != null) Fecal.report(c) else Fecal.unclassified(text)
            }
        }
        PillButton("Rensa", enabled = text.isNotEmpty() || report != null || concern) {
            graph.stopReading()
            text = ""; selected = null; report = null; concern = false; copied = false
        }
        if (canSpeak) PillButton(if (voices) "Röster: på" else "Röster: av") { if (voices) graph.stopReading(); voices = !voices }
    }

    if (concern) Text(Fecal.CONCERN, color = GuideColors.Truth, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)

    report?.let { scene ->
        Card {
            selected?.let { c ->
                Text("${c.id} · ${c.origin.label}", color = GuideColors.OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(c.name + (c.alias?.let { " – $it" } ?: ""), color = GuideColors.GuideAlt, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(c.description, color = GuideColors.OnSurface, fontSize = 16.sp, lineHeight = 22.sp)
                if (!c.classical) Text(Fecal.ADDITION_NOTE, color = GuideColors.OnSurfaceVariant, fontSize = 12.sp)
            }
            SceneLines(graph, scene, speak)
            selected?.let { c ->
                c.filing?.let {
                    Text("GALAKTISK ANMÄLAN", color = GuideColors.OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    Text("”$it”", color = GuideColors.GuideAlt, fontSize = 16.sp, lineHeight = 22.sp)
                }
                Text(Fecal.FOOTNOTE, color = GuideColors.OnSurfaceVariant, fontSize = 13.sp, fontStyle = FontStyle.Italic)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PillButton(Fecal.ANCHOR_BUTTON, primary = !anchor) { anchor = !anchor }
                PillButton(Fecal.REVIEW_BUTTON) { graph.stopReading(); inquiry = Fecal.independentReview }
                selected?.let { c ->
                    PillButton(if (copied) "Kopierad" else "Kopiera haverirapport") {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Haverirapport", Fecal.transcript(c)))
                        copied = true
                    }
                }
            }
            Text("${scene.source.label} · ${Grip.SUBOPTIMIZATION.title}", color = GuideColors.OnSurfaceVariant, fontSize = 11.sp)
        }
    }

    if (!anchor && report == null) PillButton(Fecal.ANCHOR_BUTTON) { anchor = true }
    if (anchor) AnchorCard(selected) { anchor = false }

    // Minispelet.
    Card(accent = GuideColors.Warm) {
        Text(Fecal.GAME_TITLE, color = GuideColors.Warm, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black)
        gameScene?.let { SceneLines(graph, it, speak) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Meter("PAPPERSRANSON", "${bowel.paperRationPercent} %", Modifier.weight(1f))
            Meter("TID PER BESÖK", "${bowel.visitTimePercent} %", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Meter("DOKUMENTATIONSKRAV", "${bowel.documentationDemand}×", Modifier.weight(1f))
            Meter("HANDLÄGGARE PER OBSERVATION", "${bowel.reviewersPerObservation}", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Meter("TILLÅTNA TOALETTBESÖK", if (bowel.visitsAllowed) "alla" else "0", Modifier.weight(1f))
            Meter("EMOTIONELL KOSTNAD", "${bowel.emotionalCostPercent} %", Modifier.weight(1f))
        }
        Meter("MEDICINSK NYTTA", "${bowel.medicalBenefitPercent} %", Modifier.fillMaxWidth())
        Text(Fecal.GAME_STATUS, color = GuideColors.OnSurfaceVariant, fontSize = 14.sp, fontStyle = FontStyle.Italic)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            fun play(next: se.pundaren.guide.engine.galax.BowelScene?) {
                if (next == null) return
                graph.stopReading(); bowel = next.state; gameScene = next.scene
            }
            when {
                Fecal.canClose(bowel) -> PillButton("SLUTLIGT MYNDIGHETSBESLUT", primary = true) { play(Fecal.close(bowel)) }
                Fecal.canReport(bowel) -> PillButton("SLUTRAPPORT", primary = true) { play(Fecal.finalReport(bowel)) }
                !bowel.reported -> {
                    PillButton("ÖKA EFFEKTIVITETEN", primary = true, enabled = Fecal.canIncrease(bowel)) { play(Fecal.increase(bowel)) }
                    PillButton("SUBOPTIMERA ÄNNU MER", enabled = Fecal.canSuboptimize(bowel)) { play(Fecal.suboptimize(bowel)) }
                }
            }
            if (bowel != BowelState()) PillButton("Återställ tarmförvaltningen") { graph.stopReading(); bowel = BowelState(); gameScene = null }
        }
    }

    Text("SÄRSKILDA UTREDNINGAR", color = GuideColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Fecal.inquiries.forEach { s -> PillButton(s.title, primary = s == inquiry) { graph.stopReading(); inquiry = s } }
        PillButton(Fecal.REVIEW_BUTTON, primary = inquiry == Fecal.independentReview) { graph.stopReading(); inquiry = Fecal.independentReview }
    }
    inquiry?.let { s ->
        Card {
            SceneLines(graph, s, speak)
            Text("${s.source.label} · ${Grip.SUBOPTIMIZATION.title}", color = GuideColors.OnSurfaceVariant, fontSize = 11.sp)
        }
    }

    Text(Fecal.DISCLAIMER, color = GuideColors.Truth, fontSize = 13.sp)
    Text("Ingenting du väljer eller skriver här sparas eller loggas. Kopierar du en rapport hamnar den i urklipp, ingen annanstans.", color = GuideColors.Truth, fontSize = 12.sp)
}

/** Verklighetsankaret: det turkosa är sant, det lila är Haverikommissionens syn på saken. */
@Composable
private fun AnchorCard(selected: FecalClass?, onClose: () -> Unit) {
    Card(accent = GuideColors.Truth) {
        Text(Fecal.ANCHOR_TITLE, color = GuideColors.Truth, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Fecal.anchorIntro.forEach { Text(it, color = GuideColors.GuideAlt, fontSize = 15.sp, lineHeight = 21.sp) }

        selected?.let { c ->
            Text("${c.name.uppercase()} PÅ JORDISKA", color = GuideColors.Truth, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Text(Fecal.earthly(c), color = GuideColors.Truth, fontSize = 16.sp, lineHeight = 22.sp)
            c.earthlyNote?.let { Text(it, color = GuideColors.Truth, fontSize = 14.sp, lineHeight = 20.sp) }
        }

        Text("BRISTOLSKALAN – JORDISK MEDICINSK REFERENS", color = GuideColors.Truth, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Fecal.bristol.forEachIndexed { i, type -> Text("Typ ${i + 1}: $type", color = GuideColors.Truth, fontSize = 16.sp, lineHeight = 22.sp) }
        Fecal.anchorFacts.forEach { Text(it, color = GuideColors.Truth, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 2.dp)) }
        Text(Fecal.ANCHOR_SOURCE, color = GuideColors.Truth, fontSize = 12.sp, lineHeight = 17.sp)

        Text("GALAKTISKA HAVERIKOMMISSIONENS KOMMENTAR", color = GuideColors.OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Fecal.anchorComment.forEach { Text(it, color = GuideColors.GuideAlt, fontSize = 15.sp, lineHeight = 21.sp) }
        PillButton("Tillbaka till galaxen", onClick = onClose)
    }
}

/**
 * Omvändelseprogrammet: De tolv heliga stegen, ett i taget. Bara antalet upplåsta steg sparas.
 * Efter det tolfte: ceremonin, slutintyget, paradoxen och diplomet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConversionCard(graph: AppGraph, unlocked: Int, canSpeak: Boolean) {
    val done = unlocked.coerceIn(0, Conversion.STEPS)
    var scene by remember { mutableStateOf<Scene?>(null) }
    var voices by rememberSaveable { mutableStateOf(true) }
    val play = { next: Scene? -> graph.stopReading(); scene = next }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(GuideColors.Surface).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(Conversion.AUTHORITY, color = GuideColors.Guide, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(Conversion.TITLE, color = GuideColors.OnSurface, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(Conversion.SUBTITLE, color = GuideColors.OnSurfaceVariant, fontSize = 14.sp, fontStyle = FontStyle.Italic)
        if (done == 0) {
            Text(Conversion.TAGLINE, color = GuideColors.OnSurface, fontSize = 15.sp)
            Text("VARNING FRÅN GALAKTISKA SJÄLVBILDSMYNDIGHETEN", color = GuideColors.Warm, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Conversion.warning.forEach { Text(it, color = GuideColors.GuideAlt, fontSize = 15.sp, lineHeight = 21.sp) }
        }

        if (done > 0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Conversion.steps.take(done).forEach { s ->
                    PillButton(s.roman, primary = scene == Conversion.step(s.number)) { play(Conversion.step(s.number)) }
                }
            }
        }
        if (done < Conversion.STEPS) {
            val next = Conversion.steps[done]
            PillButton("LÅS UPP STEG ${next.roman}", primary = true) {
                graph.settings.update { it.copy(conversionStep = next.number) }
                graph.log.line("OMVÄNDELSE steg ${next.number}")
                play(Conversion.step(next.number))
            }
        } else {
            Text(Conversion.DIPLOMA, color = GuideColors.Warm, fontSize = 18.sp, fontWeight = FontWeight.Black)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PillButton(Conversion.ceremony.title, primary = true) { play(Conversion.ceremony) }
                PillButton("SLUTINTYG") { play(Conversion.conclusion) }
                PillButton(Conversion.paradox.title) { play(Conversion.paradox) }
                PillButton(Conversion.REPORT_BUTTON) { play(Conversion.reportTheMessenger) }
            }
        }

        scene?.let { s ->
            if (s == Conversion.conclusion) {
                Conversion.certificate.forEach { row ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GuideColors.SurfaceHigh).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(row.area.uppercase(), color = GuideColors.OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${row.before}  →  ${row.after}", color = GuideColors.GuideAlt, fontSize = 16.sp)
                    }
                }
            }
            SceneLines(graph, s, voices && canSpeak)
            if (s == Conversion.paradox) Text(Conversion.MOTTO, color = GuideColors.GuideAlt, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            Text("${s.source.label} · ${Grip.SHAME_ALLERGY.title}", color = GuideColors.OnSurfaceVariant, fontSize = 11.sp)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canSpeak) PillButton(if (voices) "Röster: på" else "Röster: av") { if (voices) graph.stopReading(); voices = !voices }
            if (done == Conversion.STEPS) PillButton("Återfall i självinsikt: börja om") {
                graph.settings.update { it.copy(conversionStep = 0) }
                play(null)
            }
        }
        Text(
            if (done == 0) Conversion.APPROVAL else "Steg $done av ${Conversion.STEPS} genomförda. Bara antalet sparas, här på enheten.",
            color = if (done == 0) GuideColors.OnSurfaceVariant else GuideColors.Truth, fontSize = 12.sp,
        )
    }
}
