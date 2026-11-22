package se.pundaren.guide.app.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import se.pundaren.guide.app.AppGraph
import se.pundaren.guide.app.Settings
import se.pundaren.guide.engine.galax.DontPanic
import se.pundaren.guide.engine.galax.GalacticBank
import se.pundaren.guide.engine.galax.PanicKind
import se.pundaren.guide.engine.galax.PanicReply
import se.pundaren.guide.engine.galax.PromiseRegistry
import se.pundaren.guide.engine.galax.Source

/*
 * Startsidan (gällande inriktning, avsnitt 6): DON'T PANIC i mitten, och runt den visdomssändningen,
 * absurditetsgraden och Löftesregistret. Allt här fungerar utan karta, utan nät och hemma i soffan.
 *
 * Lila är Guiden. Turkost är sant: verklighetsankaret och upplysningen om vad som sparas.
 * Korall är larm: TOTAL PANIK och bonusen. Sidan rör aldrig rutt, position eller besked.
 */

@Composable
fun HomeScreen(graph: AppGraph, settings: Settings, routeActive: Boolean, onEmergency: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var reply by remember { mutableStateOf<PanicReply?>(null) }
    var wisdom by rememberSaveable { mutableIntStateOf((System.nanoTime() % GalacticBank.proverbs.size).toInt().coerceAtLeast(0)) }
    var sent by rememberSaveable { mutableIntStateOf(1) }

    val press = {
        graph.stopReading()
        reply = graph.dontPanic(input)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        val left: @Composable ColumnScope.() -> Unit = {
            Text("PUNDARENS GUIDE TILL GALAXEN", color = GuideColors.Guide, fontSize = 20.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            WisdomCard(number = sent, text = GalacticBank.proverbs[wisdom]) {
                wisdom = (wisdom + 1 + (System.nanoTime() % 7).toInt().coerceAtLeast(0)) % GalacticBank.proverbs.size
                sent += 1
            }
            PanicButton(onPress = press, onEmergency = onEmergency, modifier = Modifier.align(Alignment.CenterHorizontally))
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), maxLines = 4,
                label = { Text("Vad har hänt? (frivilligt)") },
                supportingText = { Text("Skriv något och tryck på knappen. Tom ruta ger dagens galaktiska beslut. Texten stannar på enheten och sparas inte.") },
            )
        }
        val right: @Composable ColumnScope.() -> Unit = {
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

/** Den stora knappen. Kort tryck: ett galaktiskt beslut. Långt tryck: nödkortet, som är sant och inte satir. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanicButton(onPress: () -> Unit, onEmergency: () -> Unit, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "puls")
    val scale by pulse.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "skala")
    val glow by pulse.animateFloat(0.18f, 0.42f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "sken")
    Box(modifier.size(300.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(GuideColors.Guide.copy(alpha = glow), Color.Transparent)), CircleShape))
        Box(
            Modifier.size(220.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
                .background(Brush.linearGradient(listOf(GuideColors.GuideAlt, GuideColors.Guide, GuideColors.GuideDim)))
                .border(6.dp, GuideColors.GuideDim.copy(alpha = 0.7f), CircleShape)
                .combinedClickable(onClick = onPress, onLongClick = onEmergency),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DON'T\nPANIC", color = GuideColors.OnGuide, fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text("håll in: nödkort", color = GuideColors.OnGuide, fontSize = 12.sp)
            }
        }
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
