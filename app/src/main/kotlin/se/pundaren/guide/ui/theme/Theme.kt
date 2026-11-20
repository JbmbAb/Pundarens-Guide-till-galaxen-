package se.pundaren.guide.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.pundaren.guide.R

private val Void = Color(0xFF000000)
private val Mist = Color(0xFF0B0E14)
private val ContainerLow = Color(0xFF10131A)
private val Container = Color(0xFF161A21)
private val ContainerHigh = Color(0xFF1C2028)
private val ContainerHighest = Color(0xFF22262F)
private val Primary = Color(0xFFC799FF)
private val PrimaryContainer = Color(0xFFBC87FE)
private val OnPrimary = Color(0xFF440080)
private val Secondary = Color(0xFF4AF8E3)
private val OnSecondary = Color(0xFF005B51)
private val SecondaryContainer = Color(0xFF006A60)
private val Tertiary = Color(0xFFFF8BA0)
private val TertiaryContainer = Color(0xFFFB899C)
private val OnSurface = Color(0xFFECEDF6)
private val OnSurfaceVariant = Color(0xFFB6BECC)
private val Outline = Color(0xFF45484F)
private val Error = Color(0xFFFF6E84)

private val CosmicColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Color(0xFF2A0054),
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = Color(0xFFB6FFF4),
    tertiary = Tertiary,
    onTertiary = Color(0xFF620324),
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = Color(0xFF3F0014),
    background = Mist,
    onBackground = OnSurface,
    surface = Mist,
    onSurface = OnSurface,
    surfaceVariant = ContainerHighest,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = Void,
    surfaceContainerLow = ContainerLow,
    surfaceContainer = Container,
    surfaceContainerHigh = ContainerHigh,
    surfaceContainerHighest = ContainerHighest,
    outline = Outline,
    outlineVariant = Outline,
    error = Error,
    onError = Color(0xFF490013),
    errorContainer = Color(0xFFA70138),
    onErrorContainer = Color(0xFFFFB2B9),
    inverseSurface = OnSurface,
    inverseOnSurface = Mist,
    inversePrimary = Color(0xFF7E29DF),
)

private fun variable(font: Int, weight: Int): Font = Font(
    resId = font,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(
    variable(R.font.manrope, 400),
    variable(R.font.manrope, 500),
    variable(R.font.manrope, 600),
    variable(R.font.manrope, 700),
    variable(R.font.manrope, 800),
)

val SpaceGrotesk = FontFamily(
    variable(R.font.space_grotesk, 400),
    variable(R.font.space_grotesk, 500),
    variable(R.font.space_grotesk, 700),
)

private val GuideTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        letterSpacing = (-1.1).sp,
        color = OnSurface,
    ),
    headlineLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.1.sp,
    ),
)

private val GuideShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(topStart = 32.dp, topEnd = 18.dp, bottomEnd = 36.dp, bottomStart = 14.dp),
)

val Pebble = RoundedCornerShape(topStart = 28.dp, topEnd = 16.dp, bottomEnd = 32.dp, bottomStart = 12.dp)

@Composable
fun GuideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CosmicColors,
        typography = GuideTypography,
        shapes = GuideShapes,
        content = content,
    )
}
