package se.pundaren.guide.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import se.pundaren.guide.app.AppGraph
import se.pundaren.guide.app.Settings
import se.pundaren.guide.engine.galax.DontPanic
import se.pundaren.guide.engine.galax.GalacticBank
import se.pundaren.guide.engine.galax.MonkeyScene
import se.pundaren.guide.engine.galax.MonkeyState
import se.pundaren.guide.engine.galax.Monkeys
import se.pundaren.guide.engine.galax.PanicKind
import se.pundaren.guide.engine.galax.PanicReply
import se.pundaren.guide.engine.galax.PromiseRegistry
import se.pundaren.guide.engine.galax.Source
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Startsidan (gällande inriktning, avsnitt 6): DON'T PANIC i mitten, och runt den visdomssändningen,
 * absurditetsgraden och Löftesregistret. Allt här fungerar utan karta, utan nät och hemma i soffan.
 *
 * Lila är Guiden. Turkost är sant: verklighetsankaret och upplysningen om vad som sparas.
 * Korall är larm: TOTAL PANIK, bonusen och aporna. Sidan rör aldrig rutt, position eller besked.
 *
 * Dubbeltryck på knappen släpper lös Ordens flygande bekräftelsekår och öppnar minispelet
 * Effektivisera aporna. Enkeltryck och långtryck fungerar som förut.
 */

@Composable
fun HomeScreen(graph: AppGraph, settings: Settings, routeActive: Boolean, onEmergency: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var reply by remember { mutableStateOf<PanicReply?>(null) }
    var wisdom by rememberSaveable { mutableIntStateOf((System.nanoTime() % GalacticBank.proverbs.size).toInt().coerceAtLeast(0)) }
    var sent by rememberSaveable { mutableIntStateOf(1) }

    var monkeys by remember { mutableStateOf<MonkeyScene?>(null) }
    var voices by rememberSaveable { mutableStateOf(true) }

    val press = {
        graph.stopReading()
        reply = graph.dontPanic(input)
    }
    val release = {
        graph.stopReading()
        val scene = Monkeys.release(monkeys?.state ?: MonkeyState())
        graph.log.line("APOR flock ${scene.state.flocks}")
        monkeys = scene
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        val left: @Composable ColumnScope.() -> Unit = {
            Text("PUNDARENS GUIDE TILL GALAXEN", color = GuideColors.Guide, fontSize = 20.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            WisdomCard(number = sent, text = GalacticBank.proverbs[wisdom]) {
                wisdom = (wisdom + 1 + (System.nanoTime() % 7).toInt().coerceAtLeast(0)) % GalacticBank.proverbs.size
                sent += 1
            }
            PanicButton(
                onPress = press, onRelease = release, onEmergency = onEmergency, flocks = monkeys?.state?.flocks ?: 0,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), maxLines = 4,
                label = { Text("Vad har hänt? (frivilligt)") },
                supportingText = { Text("Skriv något och tryck på knappen. Tom ruta ger dagens galaktiska beslut. Texten stannar på enheten och sparas inte.") },
            )
        }
        val right: @Composable ColumnScope.() -> Unit = {
            monkeys?.let { scene ->
                MonkeyCard(
                    graph = graph, scene = scene, canSpeak = !routeActive && graph.canReadAloud(), voices = voices,
                    onVoices = { on -> if (!on) graph.stopReading(); voices = on },
                    onScene = { next -> graph.stopReading(); monkeys = next },
                )
            }
            reply?.let { r ->
                ReplyCard(
                    reply = r, canRead = !routeActive && graph.canReadAloud(),
                    onRead = { graph.readAloud(r.spoken()) },
                    onClear = { graph.stopReading(); reply = null; input = "" },
                )
            }
            AbsurdityCard(settings.absurdityPercent) { v -> graph.settings.update { it.copy(absurdityPercent = v) } }
            PromiseCard(graph, settings.promiseCount)
        }

        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) { left() }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) { right() }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) { left(); right() }
        }
    }
}

/** Visdomssändningen: ett insamlat ordspråk i taget. Tryck för nästa. */
@Composable
private fun WisdomCard(number: Int, text: String, onNext: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 44.dp, topEnd = 28.dp, bottomEnd = 44.dp, bottomStart = 28.dp))
            .background(GuideColors.Surface).clickable(onClick = onNext).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("VISDOMSSÄNDNING #$number · ${Source.COLLECTED.label}", color = GuideColors.OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text, color = GuideColors.GuideAlt, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
        Text("tryck för nästa sändning", color = GuideColors.OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
    }
}

/**
 * Den stora knappen. Kort tryck: ett galaktiskt beslut. Dubbeltryck: aporna släpps lös.
 * Långt tryck: nödkortet, som är sant och inte satir.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanicButton(onPress: () -> Unit, onRelease: () -> Unit, onEmergency: () -> Unit, flocks: Int, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "puls")
    val scale by pulse.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "skala")
    val glow by pulse.animateFloat(0.18f, 0.42f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "sken")
    val orbit by pulse.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "varv")
    Box(modifier.size(300.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(GuideColors.Guide.copy(alpha = glow), Color.Transparent)), CircleShape))
        // Bekräftelsekåren cirklar runt tronen. Fler flockar, fler apor.
        val monkeys = if (flocks <= 0) 0 else (4 + 2 * flocks).coerceAtMost(12)
        repeat(monkeys) { i ->
            Text(
                "\uD83D\uDC12", fontSize = 24.sp,
                modifier = Modifier.offset {
                    val angle = Math.toRadians(orbit + i * 360.0 / monkeys)
                    val radius = (130 + 6 * sin(Math.toRadians(orbit * 4.0 + i * 70.0))).dp.toPx()
                    IntOffset((cos(angle) * radius).roundToInt(), (sin(angle) * radius).roundToInt())
                },
            )
        }
        Box(
            Modifier.size(220.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
                .background(Brush.linearGradient(listOf(GuideColors.GuideAlt, GuideColors.Guide, GuideColors.GuideDim)))
                .border(6.dp, GuideColors.GuideDim.copy(alpha = 0.7f), CircleShape)
                .combinedClickable(onClick = onPress, onDoubleClick = onRelease, onLongClick = onEmergency),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DON'T\nPANIC", color = GuideColors.OnGuide, fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text("håll in: nödkort", color = GuideColors.OnGuide, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Effektivisera aporna. Raderna kommer en i taget med regisserade pauser, och varje roll läses upp
 * med sin egen röst när rösten finns och ingen rutt pågår.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonkeyCard(
    graph: AppGraph, scene: MonkeyScene, canSpeak: Boolean, voices: Boolean,
    onVoices: (Boolean) -> Unit, onScene: (MonkeyScene?) -> Unit,
) {
    val state = scene.state
    var shown by remember(scene) { mutableIntStateOf(0) }
    LaunchedEffect(scene) {
        var speak = voices && canSpeak
        scene.cues.forEachIndexed { i, cue ->
            delay(cue.pauseBeforeMillis.toLong())
            shown = i + 1
            when {
                // Klipps en replik (nytt tryck, röster av) läses resten av scenen inte upp.
                speak && cue.role != null -> if (!graph.speakCue(cue)) speak = false
                speak -> delay((cue.text.length * 45L).coerceIn(700L, 2600L))
            }
        }
    }
    val shape = RoundedCornerShape(topStart = 44.dp, topEnd = 28.dp, bottomEnd = 44.dp, bottomStart = 28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(GuideColors.SurfaceHigh).border(2.dp, GuideColors.Warm.copy(alpha = 0.6f), shape)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(Monkeys.AUTHORITY, color = GuideColors.OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("RELEASE THE MONKEYS!", color = GuideColors.Warm, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
        Text(scene.title, color = GuideColors.Guide, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        scene.cues.take(shown).forEachIndexed { i, cue ->
            val role = cue.role
            if (role != null && (cue.direction != null || scene.cues.getOrNull(i - 1)?.role != role)) {
                Text(
                    role.title.uppercase() + (cue.direction?.let { " · $it" } ?: ""),
                    color = GuideColors.OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp),
                )
            }
            val shouted = cue.text == cue.text.uppercase()
            Text(
                if (role == null) cue.text else "”${cue.text}”",
                color = when {
                    shouted -> GuideColors.Warm
                    role == null -> GuideColors.OnSurface
                    else -> GuideColors.GuideAlt
                },
                fontSize = 19.sp, lineHeight = 26.sp,
                fontWeight = if (shouted) FontWeight.Black else FontWeight.Normal,
                fontStyle = if (role == se.pundaren.guide.engine.galax.Role.CEREMONY) FontStyle.Italic else FontStyle.Normal,
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Meter("SMULOR PER APA", "${state.crumbsPerMonkey}", Modifier.weight(1f))
            Meter("ARBETSKRAV", "${state.workDemand}×", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Meter("EMOTIONELL KOSTNAD", "${state.emotionalCostPercent} %", Modifier.weight(1f))
            Meter("FAKTISKT LÖST", "${state.solvedPercent} %", Modifier.weight(1f))
        }
        if (state.worse > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Meter("BEKRÄFTARE SOM KRÄVS", "${state.confirmersNeeded}", Modifier.weight(1f))
                Meter("SKULD FÖR STÄMNINGEN", "${state.blamePercent} %", Modifier.weight(1f))
            }
            Meter("RAPPORTERAT RESULTAT", "${state.reportedResultPercent} %", Modifier.fillMaxWidth())
        }
        Text(Monkeys.STATUS, color = GuideColors.OnSurfaceVariant, fontSize = 14.sp, fontStyle = FontStyle.Italic)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                Monkeys.canBlameRikard(state) -> PillButton("[ SKYLL PÅ RIKARD ]", primary = true) { onScene(Monkeys.blameRikard(state)) }
                Monkeys.canReport(state) -> PillButton("SLUTRAPPORT", primary = true) { onScene(Monkeys.finalReport(state)) }
                !state.reported -> {
                    PillButton("HALVERA SMULRANSONEN", primary = true, enabled = Monkeys.canHalve(state)) { Monkeys.halve(state)?.let(onScene) }
                    PillButton("SUBOPTIMERA ÄNNU MER", enabled = Monkeys.canSuboptimize(state)) { Monkeys.suboptimize(state)?.let(onScene) }
                }
            }
            PillButton("Återställ katastrofen") { onScene(null) }
            if (canSpeak) PillButton(if (voices) "Röster: på" else "Röster: av") { onVoices(!voices) }
        }
        Text(Monkeys.DISCLAIMER, color = GuideColors.Truth, fontSize = 12.sp)
        Text("${scene.source.label} · ${scene.grip.title}", color = GuideColors.OnSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun Meter(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(GuideColors.Surface).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, color = GuideColors.OnSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = GuideColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

/** Beslutet. Raderna kommer en i taget, som ett protokoll som skrivs ut medan man tittar på. */
@Composable
private fun ReplyCard(reply: PanicReply, canRead: Boolean, onRead: () -> Unit, onClear: () -> Unit) {
    val serious = reply.kind == PanicKind.SERIOUS
    val alarm = reply.kind == PanicKind.TOTAL_PANIC
    var shown by remember(reply) { mutableIntStateOf(if (serious) reply.lines.size else 0) }
    LaunchedEffect(reply) {
        if (!serious) for (i in 1..reply.lines.size) { delay(if (alarm) 380 else 300); shown = i }
    }
    val pulse = rememberInfiniteTransition(label = "larm")
    val flash by pulse.animateFloat(0.25f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "blink")
    val accent = when {
        serious -> GuideColors.Truth
        alarm -> GuideColors.Warm
        else -> GuideColors.Guide
    }
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 44.dp, bottomEnd = 28.dp, bottomStart = 44.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(GuideColors.SurfaceHigh)
            .then(if (alarm && shown < reply.lines.size) Modifier.border(3.dp, GuideColors.Warm.copy(alpha = flash), shape) else Modifier)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        reply.authority?.let { Text(it.uppercase(), color = GuideColors.OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Text(reply.headline, color = accent, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
        reply.lines.take(shown).forEach { line ->
            Text(line, color = if (serious) GuideColors.Truth else GuideColors.GuideAlt, fontSize = 19.sp, lineHeight = 26.sp)
        }
        if (shown >= reply.lines.size) {
            reply.anchor?.let { anchor ->
                Column(Modifier.fillMaxWidth().padding(top = 6.dp).border(1.dp, GuideColors.Truth.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(12.dp)) {
                    Text("VERKLIGHETSANKARE", color = GuideColors.Truth, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(anchor, color = GuideColors.Truth, fontSize = 16.sp, lineHeight = 22.sp)
                }
            }
            if (!serious) {
                Text(
                    listOfNotNull(reply.decreeId, reply.source?.label, reply.grip?.title, reply.scale?.title).joinToString(" · "),
                    color = GuideColors.OnSurfaceVariant, fontSize = 11.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canRead && !serious) PillButton("Läs upp", onClick = onRead)
                PillButton("Rensa", onClick = onClear)
            }
        }
    }
}

/** Sannolikhetsmätaren: ett steglöst reglage från lätt förvirrad till bortom varje återstående naturlag. */
@Composable
private fun AbsurdityCard(percent: Int, onChange: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(GuideColors.Surface).padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ABSURDITETSGRAD", color = GuideColors.Guide, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(DontPanic.levelName(percent), color = GuideColors.OnSurface, fontSize = 17.sp)
            }
            Text("$percent %", color = GuideColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = percent.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = 0f..100f)
        Text("Styr hur långt Guidens beslut går och hur ofta Rikard dras in. Rutt och besked påverkas aldrig.", color = GuideColors.OnSurfaceVariant, fontSize = 12.sp)
    }
}

/** Galaktiska Löftesregistret. Löftet räknas som genomfört, krypteringen havererar och texten försvinner på riktigt. */
@Composable
private fun PromiseCard(graph: AppGraph, count: Int) {
    var promise by rememberSaveable { mutableStateOf("") }
    var receipt by remember { mutableStateOf<PromiseRegistry.Receipt?>(null) }
    var shown by remember(receipt) { mutableIntStateOf(0) }
    LaunchedEffect(receipt) {
        receipt?.let { r -> for (i in 1..(r.steps.size + r.bonus.size)) { delay(520); shown = i } }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(GuideColors.Surface).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("GALAKTISKA LÖFTESREGISTRET", color = GuideColors.Guide, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("Enligt sjätte budordet räknas ett löfte som genomfört i samma stund som det uttalas.", color = GuideColors.OnSurface, fontSize = 15.sp)
        OutlinedTextField(
            value = promise, onValueChange = { promise = it }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
            label = { Text("Jag lovar att …") },
        )
        PillButton("Registrera löftet", primary = true, enabled = promise.isNotBlank()) {
            receipt = graph.registerPromise()
            promise = ""
        }
        receipt?.let { r ->
            Text("Diarienummer ${r.number}", color = GuideColors.OnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            r.steps.take(shown).forEach { Text(it, color = GuideColors.GuideAlt, fontSize = 17.sp, lineHeight = 23.sp) }
            r.bonus.take((shown - r.steps.size).coerceAtLeast(0)).forEach {
                Text(it, color = GuideColors.Warm, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            "Löftets text sparas inte någonstans. Bara antalet räknas, här på enheten: $count. " +
                "Bonus om ${PromiseRegistry.untilBonus(count)} ${if (PromiseRegistry.untilBonus(count) == 1) "löfte" else "löften"}.",
            color = GuideColors.Truth, fontSize = 12.sp,
        )
    }
}

/** Flikarna i botten. Kartan är en av Guidens förmågor, inte hela appen. */
enum class Tab(val title: String) { HOME("Hem"), MAP("Karta"), BABEL("Babel"), DRIVE("Reglage") }

@Composable
fun TabBar(selected: Tab, enabled: (Tab) -> Boolean, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(GuideColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            val usable = enabled(tab)
            Box(
                Modifier.widthIn(min = 96.dp).clip(CircleShape).background(if (on) GuideColors.Guide else Color.Transparent)
                    .clickable(enabled = usable) { onSelect(tab) }.padding(horizontal = 22.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.title, fontSize = 16.sp, fontWeight = if (on) FontWeight.Black else FontWeight.SemiBold,
                    color = when {
                        on -> GuideColors.OnGuide
                        usable -> GuideColors.Guide
                        else -> GuideColors.OnSurfaceVariant.copy(alpha = 0.5f)
                    },
                )
            }
        }
    }
}
