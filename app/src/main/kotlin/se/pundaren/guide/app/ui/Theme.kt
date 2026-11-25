package se.pundaren.guide.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import se.pundaren.guide.app.R

/**
 * Färgerna enligt F-02 (G0-beslutsbladet avsnitt 2): The Sentient Archive som grund.
 *
 * **Turkost är sant. Lila är Guiden.** [Truth] används i sanningsremsan, på nödkortet och för startsidans
 * verklighetsankare, som också är sant. Korall ([Warm]) är larm: TOTAL PANIK och bonusen.
 * Kulörerna följer gällande inriktning, avsnitt 10.
 * Typsnitten är Stitch-skärmarnas: Space Grotesk för det som ropas och Manrope för det som läses
 * (se [GuideFont]). Licenstexterna (SIL OFL 1.1) ligger i `docs/licenser/`.
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

/** Ett snitt ur en variabel typsnittsfil: [weight] är vikten texten ber om, [drawn] den som ritas. */
@OptIn(ExperimentalTextApi::class)
private fun cut(file: Int, weight: Int, drawn: Int = weight) =
    Font(file, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(drawn)))

/**
 * En familj, två typsnitt. Vikten väljer: normal till halvfet text sätts i Manrope, fet och uppåt i
 * Space Grotesk. Då får varje rubrik i appen rätt typsnitt utan att någon skärm behöver veta om det.
 * Space Grotesk slutar vid 700, så de fetaste vikterna ritas alla som 700.
 */
val GuideFont = FontFamily(
    cut(R.font.manrope, 400),
    cut(R.font.manrope, 500),
    cut(R.font.manrope, 600),
    cut(R.font.space_grotesk, 700),
    cut(R.font.space_grotesk, 800, drawn = 700),
    cut(R.font.space_grotesk, 900, drawn = 700),
)

private val GuideTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = GuideFont),
        displayMedium = displayMedium.copy(fontFamily = GuideFont),
        displaySmall = displaySmall.copy(fontFamily = GuideFont),
        headlineLarge = headlineLarge.copy(fontFamily = GuideFont),
        headlineMedium = headlineMedium.copy(fontFamily = GuideFont),
        headlineSmall = headlineSmall.copy(fontFamily = GuideFont),
        titleLarge = titleLarge.copy(fontFamily = GuideFont),
        titleMedium = titleMedium.copy(fontFamily = GuideFont),
        titleSmall = titleSmall.copy(fontFamily = GuideFont),
        bodyLarge = bodyLarge.copy(fontFamily = GuideFont),
        bodyMedium = bodyMedium.copy(fontFamily = GuideFont),
        bodySmall = bodySmall.copy(fontFamily = GuideFont),
        labelLarge = labelLarge.copy(fontFamily = GuideFont),
        labelMedium = labelMedium.copy(fontFamily = GuideFont),
        labelSmall = labelSmall.copy(fontFamily = GuideFont),
    )
}

@Composable
fun GuideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = GuideTypography,
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
