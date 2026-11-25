package se.pundaren.guide.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * Det ritade: rymden bakom Guiden och ikonerna i flikraden. Allt ritas i kod, utan bildfiler.
 *
 * Rymden ligger bara bakom Guidens egna sidor. Den ligger aldrig över kartan, sanningsremsan eller
 * nödkortet, och aldrig bakom det som inte är satir.
 */

private class Star(val x: Float, val y: Float, val size: Float, val light: Float, val phase: Float, val speed: Int)

private class Mote(val along: Float, val drift: Float, val lift: Float, val size: Float)

/**
 * Stjärnhimmel med en spiralgalax som vrider sig ett varv på tre minuter. [galaxyAt] är galaxens
 * mitt som andel av ytan, så att den kan läggas där sidan annars är tom.
 */
@Composable
fun Cosmos(modifier: Modifier = Modifier, galaxyAt: Offset = Offset(0.5f, 0.7f)) {
    val time = rememberInfiniteTransition(label = "rymd")
    val turn by time.animateFloat(0f, 1f, infiniteRepeatable(tween(180_000, easing = LinearEasing)), label = "varv")
    val stars = remember {
        val r = Random(42)
        List(170) { Star(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat() * 1.2f, 0.12f + r.nextFloat() * 0.5f, r.nextFloat(), 1 + r.nextInt(40)) }
    }
    val motes = remember {
        val r = Random(1978)
        List(560) { Mote(r.nextFloat(), (r.nextFloat() - 0.5f) * 0.34f, (r.nextFloat() - 0.5f) * 0.07f, 0.8f + r.nextFloat() * 1.8f) }
    }
    Canvas(modifier.fillMaxSize()) {
        val unit = 1.dp.toPx()
        val whole = (2 * PI).toFloat()
        stars.forEach { s ->
            // Hela varv per omlopp, så att blinkandet inte hackar när tiden börjar om.
            val twinkle = 0.65f + 0.35f * sin((turn * s.speed + s.phase) * whole)
            drawCircle(Color.White, s.size * unit, Offset(s.x * size.width, s.y * size.height), alpha = s.light * twinkle)
        }

        val center = Offset(galaxyAt.x * size.width, galaxyAt.y * size.height)
        val reach = min(size.width, size.height) * 0.46f
        drawCircle(
            Brush.radialGradient(listOf(GuideColors.GuideDim.copy(alpha = 0.30f), GuideColors.GuideDim.copy(alpha = 0.08f), Color.Transparent), center, reach * 0.75f),
            reach * 0.75f, center,
        )
        val tilt = -0.42f
        motes.forEachIndexed { i, m ->
            // Två armar. Stoftet ligger tätast vid armen och glesnar utåt.
            val angle = m.along * 8.6f + (i % 2) * PI.toFloat() + m.drift + turn * whole
            val radius = reach * (m.along.pow(0.85f) + m.lift)
            val flatX = cos(angle) * radius
            val flatY = sin(angle) * radius * 0.52f
            val at = Offset(center.x + flatX * cos(tilt) - flatY * sin(tilt), center.y + flatX * sin(tilt) + flatY * cos(tilt))
            val color = when (i % 7) {
                0 -> GuideColors.Warm
                1, 2 -> Color.White
                else -> GuideColors.Guide
            }
            drawCircle(color, m.size * unit * (1.25f - 0.6f * m.along), at, alpha = 0.14f + 0.5f * (1f - m.along))
        }
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), GuideColors.GuideAlt.copy(alpha = 0.25f), Color.Transparent), center, reach * 0.14f), reach * 0.14f, center)
    }
}

/** Flikradens ikoner, ritade i samma streck: gnistor för hemma, vikt karta, pratbubbla och reglage. */
@Composable
fun TabIcon(tab: Tab, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val w = size.width
        val line = Stroke(w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tab) {
            Tab.HOME -> {
                spark(Offset(w * 0.40f, w * 0.60f), w * 0.40f, color)
                spark(Offset(w * 0.80f, w * 0.22f), w * 0.20f, color)
            }
            Tab.MAP -> {
                val sheet = Path().apply {
                    moveTo(w * 0.08f, w * 0.24f); lineTo(w * 0.36f, w * 0.10f); lineTo(w * 0.64f, w * 0.24f); lineTo(w * 0.92f, w * 0.10f)
                    lineTo(w * 0.92f, w * 0.76f); lineTo(w * 0.64f, w * 0.90f); lineTo(w * 0.36f, w * 0.76f); lineTo(w * 0.08f, w * 0.90f)
                    close()
                }
                drawPath(sheet, color, style = line)
                drawLine(color, Offset(w * 0.36f, w * 0.10f), Offset(w * 0.36f, w * 0.76f), line.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.64f, w * 0.24f), Offset(w * 0.64f, w * 0.90f), line.width, StrokeCap.Round)
            }
            Tab.BABEL -> {
                drawRoundRect(color, Offset(w * 0.06f, w * 0.10f), Size(w * 0.88f, w * 0.58f), CornerRadius(w * 0.18f), style = line)
                val tail = Path().apply { moveTo(w * 0.26f, w * 0.68f); lineTo(w * 0.22f, w * 0.94f); lineTo(w * 0.48f, w * 0.68f) }
                drawPath(tail, color, style = line)
                listOf(0.30f, 0.50f, 0.70f).forEach { drawCircle(color, w * 0.055f, Offset(w * it, w * 0.39f)) }
            }
            Tab.DRIVE -> listOf(0.30f, 0.66f, 0.44f).forEachIndexed { i, knob ->
                val x = w * (0.2f + 0.3f * i)
                drawLine(color, Offset(x, w * 0.10f), Offset(x, w * 0.90f), line.width, StrokeCap.Round)
                drawCircle(color, w * 0.12f, Offset(x, w * knob))
            }
        }
    }
}

/** En fyruddig gnista. */
private fun DrawScope.spark(at: Offset, radius: Float, color: Color) {
    val waist = radius * 0.24f
    val path = Path().apply {
        moveTo(at.x, at.y - radius); lineTo(at.x + waist, at.y - waist)
        lineTo(at.x + radius, at.y); lineTo(at.x + waist, at.y + waist)
        lineTo(at.x, at.y + radius); lineTo(at.x - waist, at.y + waist)
        lineTo(at.x - radius, at.y); lineTo(at.x - waist, at.y - waist)
        close()
    }
    drawPath(path, color)
}
