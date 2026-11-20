package se.pundaren.guide.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.pundaren.guide.engine.model.AntiNavIntensity
import se.pundaren.guide.engine.model.PoeticSensitivity
import se.pundaren.guide.engine.model.ProbabilityDrive
import se.pundaren.guide.engine.model.RikardSensitivity
import se.pundaren.guide.ui.GuideCard
import se.pundaren.guide.ui.SectionLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriveScreen(
    drive: ProbabilityDrive,
    onDrive: (ProbabilityDrive) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionLabel("Osannolikhetsdrift")
        Text(text = "Kaos utan att flytta kartan", style = MaterialTheme.typography.headlineLarge)
        GuideCard {
            Text(text = "Sannolikhet ${"%.0f".format(drive.chaos * 100)} %")
            val chaos = rememberSliderState(value = drive.chaos)
            Slider(state = chaos, onValueChange = { onDrive(drive.copy(chaos = it)) })
            Text(text = "Rikardfrekvens ${"%.0f".format(drive.rikardFrequency * 100)} %")
            val rikard = rememberSliderState(value = drive.rikardFrequency)
            Slider(state = rikard, onValueChange = { onDrive(drive.copy(rikardFrequency = it)) })
        }
        Text(text = "Rikards känslighet", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RikardSensitivity.entries.forEach { item ->
                FilterChip(
                    selected = drive.rikardSensitivity == item,
                    onClick = { onDrive(drive.copy(rikardSensitivity = item)) },
                    label = { Text(item.label) },
                )
            }
        }
        Text(text = "Poetisk känslighet", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PoeticSensitivity.entries.forEach { item ->
                FilterChip(
                    selected = drive.poeticSensitivity == item,
                    onClick = { onDrive(drive.copy(poeticSensitivity = item)) },
                    label = { Text(item.label) },
                )
            }
        }
        Text(text = "Anti-navigering", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AntiNavIntensity.entries.forEach { item ->
                FilterChip(
                    selected = drive.antiNavIntensity == item,
                    onClick = { onDrive(drive.copy(antiNavIntensity = item)) },
                    label = { Text(item.label) },
                )
            }
        }
        GuideCard {
            Text(
                text = "Varning: att ändra sannolikheten kan göra dig till en pingvin i tre till fem sekunder.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        GuideCard {
            SectionLabel("Om")
            Text(
                text = "© OpenStreetMap contributors. Kartdata från OpenStreetMap, tillgänglig under ODbL.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Babel-Stoner är satir, inte ett diagnostiskt verktyg. Originalcitat visas ordagrant. Allt som motorn skriver märks som konstruerat, variation eller förslag.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
