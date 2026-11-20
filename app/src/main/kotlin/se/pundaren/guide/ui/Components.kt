package se.pundaren.guide.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import se.pundaren.guide.engine.model.Provenance
import se.pundaren.guide.ui.theme.Pebble

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

@Composable
fun Modifier.commentaryGlitch(enabled: Boolean, reducedMotion: Boolean): Modifier {
    val transition = rememberInfiniteTransition(label = "glitch")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2200
                0f at 0
                0f at 1700
                8f at 1860
                -6f at 2020
                0f at 2160
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "shift",
    )
    val amount = if (enabled && !reducedMotion) shift else 0f
    return graphicsLayer { translationX = amount }
}

@Composable
fun rememberGlitchFlash(token: Int, enabled: Boolean, reducedMotion: Boolean): Float {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(token, enabled, reducedMotion) {
        if (!enabled || reducedMotion || token == 0) {
            alpha.snapTo(0f)
            return@LaunchedEffect
        }
        alpha.snapTo(0.55f)
        alpha.animateTo(0f, tween(durationMillis = 400))
    }
    return alpha.value
}

@Composable
fun GuideCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Pebble)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun ProvenanceChip(provenance: Provenance, approved: Boolean? = null) {
    val suffix = when (approved) {
        false -> ", inte godkänd"
        true -> ""
        null -> ""
    }
    Text(
        text = provenance.label + suffix,
        style = MaterialTheme.typography.labelMedium,
        color = if (provenance.isUsersOwnQuote) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.tertiary
        },
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
fun WisdomText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun FlashOverlay(alpha: Float, modifier: Modifier = Modifier) {
    if (alpha <= 0f) return
    Box(
        modifier
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = alpha * 0.45f)),
    )
}
