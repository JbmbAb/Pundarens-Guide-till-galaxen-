package se.pundaren.guide.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Färgerna enligt F-02 (G0-beslutsbladet avsnitt 2): The Sentient Archive som grund.
 *
 * **Turkost är sant. Lila är Guiden.** [Truth] används i sanningsremsan, på nödkortet och för startsidans
 * verklighetsankare, som också är sant. Korall ([Warm]) är larm: TOTAL PANIK och bonusen.
 * Kulörerna följer gällande inriktning, avsnitt 10.
 * Typsnitten Space Grotesk och Manrope följer inte med än (licensfilerna saknas i repot, PR1-20);
 * systemets typsnitt används tills vidare.
 */
object GuideColors {
    val Background = Color(0xFF0B0E14)
    val Surface = Color(0xFF151923)
    val SurfaceHigh = Color(0xFF212736)
    val OnSurface = Color(0xFFE8E8E8)
    val OnSurfaceVariant = Color(0xFFABABAB)

    /** Guiden: lila. */
    val Guide = Color(0xFFC799FF)
    val GuideAlt = Color(0xFFD896FE)
    val GuideDim = Color(0xFF9547F7)
    val OnGuide = Color(0xFF1B0B33)
    val Warm = Color(0xFFFF9DAC)
    val Error = Color(0xFFFF6E84)

    /** Sanningen: turkos. Bara i sanningsremsan och på nödkortet. */
    val Truth = Color(0xFF4AF8E3)
}

@Composable
fun GuideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = GuideColors.Guide,
            onPrimary = GuideColors.OnGuide,
            secondary = GuideColors.GuideAlt,
            tertiary = GuideColors.Warm,
            background = GuideColors.Background,
            onBackground = GuideColors.OnSurface,
            surface = GuideColors.Surface,
            onSurface = GuideColors.OnSurface,
            surfaceVariant = GuideColors.SurfaceHigh,
            onSurfaceVariant = GuideColors.OnSurfaceVariant,
            surfaceContainer = GuideColors.Surface,
            surfaceContainerHigh = GuideColors.SurfaceHigh,
            surfaceContainerHighest = GuideColors.SurfaceHigh,
            error = GuideColors.Error,
        ),
        content = content,
    )
}
