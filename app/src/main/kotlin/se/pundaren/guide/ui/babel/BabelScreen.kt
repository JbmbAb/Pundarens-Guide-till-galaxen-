package se.pundaren.guide.ui.babel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import se.pundaren.guide.engine.model.BabelMode
import se.pundaren.guide.engine.model.EngineOutput
import se.pundaren.guide.engine.model.LoadedArchive
import se.pundaren.guide.engine.model.Provenance
import se.pundaren.guide.engine.model.ReverseAnalysis
import se.pundaren.guide.ui.GuideCard
import se.pundaren.guide.ui.ProvenanceChip
import se.pundaren.guide.ui.SectionLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BabelScreen(
    archive: LoadedArchive,
    text: String,
    mode: BabelMode,
    reverse: Boolean,
    output: EngineOutput?,
    analysis: ReverseAnalysis?,
    busy: Boolean,
    onText: (String) -> Unit,
    onMode: (BabelMode) -> Unit,
    onReverse: (Boolean) -> Unit,
    onRun: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionLabel("Babel-Stoner")
        Text(text = "Där logiken inte får vinna", style = MaterialTheme.typography.headlineLarge)
        GuideCard {
            Text(
                text = archive.disclaimers.firstOrNull()
                    ?: "Babel-Stoner är en satirisk samtalsmotor, inte ett diagnostiskt verktyg. Den ställer inga diagnoser och tillskriver inga motiv.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            label = { Text("Text") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Text(
            text = "Inklistrad text sparas inte och skickas inte vidare.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!reverse) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BabelMode.entries.forEach { item ->
                    FilterChip(
                        selected = item == mode,
                        onClick = { onMode(item) },
                        label = { Text(item.title) },
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = if (reverse) "Omvänd analys" else "Simulering", style = MaterialTheme.typography.titleMedium)
            Switch(checked = reverse, onCheckedChange = onReverse)
        }
        Button(onClick = onRun, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Läser…" else if (reverse) "Läs texten" else "Kör")
        }
        output?.let { BabelResult(it) }
        analysis?.let { ReverseResult(it) }
    }
}

@Composable
private fun BabelResult(output: EngineOutput) {
    GuideCard {
        SectionLabel(output.modeName ?: "Svar")
        ProvenanceChip(output.sourceProvenance ?: Provenance.KONSTRUERAD)
        output.sourceId?.let {
            Text(
                text = "Källa $it",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(text = output.commentary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
    }
    output.quotes.forEach { quote ->
        GuideCard {
            ProvenanceChip(quote.provenance)
            Text(text = quote.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        }
    }
    output.analysis?.let { galactic ->
        GuideCard {
            SectionLabel("Galaktisk analys")
            Text(text = "Resultat: ${galactic.answeredPercent} % besvarad sakfråga.", style = MaterialTheme.typography.titleMedium)
            Text(text = galactic.prose, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            galactic.labels.forEach { label ->
                Text(text = "${label.label}: ${label.value}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = galactic.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ReverseResult(analysis: ReverseAnalysis) {
    if (analysis.satireSuppressed) {
        GuideCard {
            Text(text = "Stöd", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
            Text(text = analysis.summary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            analysis.supportReferral?.let {
                Text(text = it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
        return
    }
    GuideCard {
        SectionLabel("Omvänd läsning")
        Text(text = analysis.summary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        analysis.unanswered.forEach { question ->
            Text(text = "Frågan verkar inte ha besvarats: $question", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        analysis.contradictions.forEach { pair ->
            Text(text = "Möjlig motsägelse.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(text = pair.first, style = MaterialTheme.typography.bodyMedium)
            Text(text = pair.second, style = MaterialTheme.typography.bodyMedium)
        }
        analysis.patternNotes.forEach { note ->
            Text(text = note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
