package se.pundaren.guide.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import se.pundaren.guide.engine.model.EngineOutput
import se.pundaren.guide.engine.model.ProbabilityDrive
import se.pundaren.guide.ui.GuideCard
import se.pundaren.guide.ui.ProvenanceChip
import se.pundaren.guide.ui.SectionLabel
import se.pundaren.guide.ui.WisdomText
import se.pundaren.guide.ui.rememberReducedMotion

@Composable
fun HomeScreen(
    drive: ProbabilityDrive,
    output: EngineOutput?,
    onDontPanic: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "pulse")
    val animated by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "scale",
    )
    val scale = if (reducedMotion) 1f else animated
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionLabel("Visdomssändning")
        Text(
            text = "Pundarens guide",
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            text = "En ologisk men oumbärlig följeslagare. Fakta stannar. Kommentaren får ha fel.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer),
                    ),
                )
                .clickable(onClick = onDontPanic)
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Få inte panik",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = "Sannolikhet ${"%.0f".format(drive.chaos * 100)} % · Rikardfrekvens ${"%.0f".format(drive.rikardFrequency * 100)} %",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (output == null) {
            Text(
                text = "Tryck på knappen. Originalcitat visas ordagrant. Resten märks som konstruerad.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            HomeResult(output)
        }
    }
}

@Composable
private fun HomeResult(output: EngineOutput) {
    Text(
        text = "Visdomssändning ${output.transmissionNumber ?: ""}",
        style = MaterialTheme.typography.titleMedium,
    )
    output.quotes.forEach { quote ->
        GuideCard {
            ProvenanceChip(quote.provenance)
            quote.title?.let {
                Text(text = it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            }
            WisdomText(text = quote.text, modifier = Modifier.padding(top = 8.dp))
            Text(
                text = "Ordagrant ur arkivet. Texten är inte ändrad.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    GuideCard {
        ProvenanceChip(se.pundaren.guide.engine.model.Provenance.KONSTRUERAD)
        Text(
            text = output.commentary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (output.rikardInvolved) {
        Text(
            text = "Rikardstörning: signalen är ostadig.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
