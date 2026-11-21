package se.pundaren.guide.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.pundaren.guide.truth.api.GuidancePhase
import se.pundaren.guide.truth.api.GuidanceSnapshot
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.api.PositionSnapshot
import se.pundaren.guide.truth.core.geo.Geo
import se.pundaren.guide.truth.core.kernel.ManeuverText
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Allt sanningsremsan visar. Varje värde kommer ur Hard Truth Core; inget ur Illogical Engine. */
data class TruthStripModel(
    val guidance: GuidanceSnapshot,
    val position: PositionSnapshot,
    /** Statusrad när navigatorn inte är redo (paket saknas, ruttmotorn startar). */
    val systemNote: String?,
    /** Sant under provtur: positionen är simulerad. */
    val simulated: Boolean,
    /** Rösten är inte tillgänglig: beskeden visas men läses inte upp. */
    val voiceNote: String?,
)

private val clock = DateTimeFormatter.ofPattern("HH.mm")

/**
 * Sanningsremsan (02 N-4, N-6; F-02): alltid överst, raka hörn, turkost på svart, ingen glaseffekt,
 * ingen glitch, ingen drift och ingen asymmetri. Ingenting får ritas över den.
 */
@Composable
fun TruthStrip(model: TruthStripModel, modifier: Modifier = Modifier) {
    val g = model.guidance
    Column(
        modifier
            .fillMaxWidth()
            .background(GuideColors.Background, RectangleShape)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val navigating = g.phase == GuidancePhase.NAVIGATING
            if (navigating) {
                ManeuverArrow(ManeuverText.arrow(g.maneuverType, g.maneuverModifier), Modifier.size(64.dp))
                Spacer(Modifier.width(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = headline(model),
                    color = GuideColors.Truth, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                secondLine(model)?.let {
                    Text(it, color = GuideColors.Truth, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (navigating) {
                Spacer(Modifier.width(18.dp))
                Column(horizontalAlignment = Alignment.End) {
                    g.distanceRemainingMeters?.let { Text("${Geo.formatDistance(it)} kvar", color = GuideColors.Truth, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                    val time = listOfNotNull(
                        g.secondsRemaining?.let(::duration),
                        g.etaWallClockUtc?.let { "framme ${clock.format(it.atZone(ZoneId.systemDefault()))}" },
                    ).joinToString(" · ")
                    if (time.isNotEmpty()) Text(time, color = GuideColors.Truth, fontSize = 16.sp, textAlign = TextAlign.End)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(positionLine(model.position, model.simulated), color = GuideColors.Truth, fontSize = 14.sp)
            model.voiceNote?.let { Text(it, color = GuideColors.Truth, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

private fun headline(model: TruthStripModel): String {
    val g = model.guidance
    return when (g.phase) {
        GuidancePhase.NAVIGATING -> ManeuverText.line(g.maneuverType, g.maneuverModifier, g.distanceToManeuverMeters)
        GuidancePhase.REROUTING -> "Räknar om rutten …"
        GuidancePhase.ARRIVED -> "Framme" + (g.destinationName?.let { ": $it" } ?: "")
        GuidancePhase.IDLE -> model.systemNote ?: "Ingen rutt. Välj ett mål."
    }
}

private fun secondLine(model: TruthStripModel): String? {
    val g = model.guidance
    return when (g.phase) {
        GuidancePhase.NAVIGATING -> g.instructionText
        GuidancePhase.REROUTING -> g.destinationName?.let { "Mot $it" }
        else -> null
    }
}

/** N-5: osäkerheten visas ärligt, med noggrannhet i meter och positionens ålder. */
private fun positionLine(p: PositionSnapshot, simulated: Boolean): String {
    val accuracy = p.accuracyMeters?.let { "±${it.roundToInt()} m" }
    val age = p.fixAgeSeconds?.let { if (it < 60) "${it.roundToInt()} s gammal" else "${(it / 60).roundToInt()} min gammal" }
    val source = if (simulated) "PROVTUR (simulerad position)" else "GPS"
    return when (p.quality) {
        PositionQuality.GOOD -> "$source ${accuracy ?: ""}".trim()
        PositionQuality.DEGRADED -> "Position osäker: ${listOfNotNull(accuracy, age).joinToString(", ")}"
        PositionQuality.LOST -> if (p.accuracyMeters == null) "Söker position …" else "Ingen aktuell position: senaste ${listOfNotNull(accuracy, age).joinToString(", ")}"
    }
}

private fun duration(seconds: Double): String {
    val minutes = (seconds / 60.0).roundToInt()
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

/** Pilen för nästa manöver. Ritad med raka streck i sanningsfärgen. */
@Composable
fun ManeuverArrow(arrow: ManeuverText.Arrow, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * w, y * w)
        fun line(a: Offset, b: Offset, c: Offset? = null, d: Offset? = null) {
            val path = Path().apply {
                moveTo(a.x, a.y); lineTo(b.x, b.y)
                if (c != null) lineTo(c.x, c.y)
                if (d != null) lineTo(d.x, d.y)
            }
            drawPath(path, GuideColors.Truth, style = stroke)
        }
        fun head(tip: Offset, dx: Float, dy: Float) {
            // Pilspets vid tip, pekande i riktningen (dx, dy).
            val l = 0.2f * w
            val bx = tip.x - dx * l; val by = tip.y - dy * l
            line(Offset(bx - dy * l * 0.7f, by + dx * l * 0.7f), tip, Offset(bx + dy * l * 0.7f, by - dx * l * 0.7f))
        }
        when (arrow) {
            ManeuverText.Arrow.STRAIGHT, ManeuverText.Arrow.DEPART -> { line(p(0.5f, 0.9f), p(0.5f, 0.15f)); head(p(0.5f, 0.12f), 0f, -1f) }
            ManeuverText.Arrow.LEFT -> { line(p(0.7f, 0.9f), p(0.7f, 0.4f), p(0.2f, 0.4f)); head(p(0.15f, 0.4f), -1f, 0f) }
            ManeuverText.Arrow.RIGHT -> { line(p(0.3f, 0.9f), p(0.3f, 0.4f), p(0.8f, 0.4f)); head(p(0.85f, 0.4f), 1f, 0f) }
            ManeuverText.Arrow.SLIGHT_LEFT -> { line(p(0.6f, 0.9f), p(0.6f, 0.55f), p(0.3f, 0.2f)); head(p(0.27f, 0.16f), -0.65f, -0.76f) }
            ManeuverText.Arrow.SLIGHT_RIGHT -> { line(p(0.4f, 0.9f), p(0.4f, 0.55f), p(0.7f, 0.2f)); head(p(0.73f, 0.16f), 0.65f, -0.76f) }
            ManeuverText.Arrow.SHARP_LEFT -> { line(p(0.7f, 0.9f), p(0.7f, 0.3f), p(0.3f, 0.7f)); head(p(0.26f, 0.74f), -0.7f, 0.7f) }
            ManeuverText.Arrow.SHARP_RIGHT -> { line(p(0.3f, 0.9f), p(0.3f, 0.3f), p(0.7f, 0.7f)); head(p(0.74f, 0.74f), 0.7f, 0.7f) }
            ManeuverText.Arrow.U_TURN -> { line(p(0.7f, 0.9f), p(0.7f, 0.3f), p(0.3f, 0.3f), p(0.3f, 0.75f)); head(p(0.3f, 0.8f), 0f, 1f) }
            ManeuverText.Arrow.ROUNDABOUT -> roundabout(w, stroke)
            ManeuverText.Arrow.ARRIVE -> {
                drawCircle(GuideColors.Truth, radius = 0.3f * w, center = p(0.5f, 0.5f), style = stroke)
                drawCircle(GuideColors.Truth, radius = 0.1f * w, center = p(0.5f, 0.5f))
            }
        }
    }
}

private fun DrawScope.roundabout(w: Float, stroke: Stroke) {
    drawCircle(GuideColors.Truth, radius = 0.24f * w, center = Offset(0.5f * w, 0.48f * w), style = stroke)
    drawLine(GuideColors.Truth, Offset(0.5f * w, 0.95f * w), Offset(0.5f * w, 0.72f * w), strokeWidth = stroke.width, cap = StrokeCap.Round)
}
