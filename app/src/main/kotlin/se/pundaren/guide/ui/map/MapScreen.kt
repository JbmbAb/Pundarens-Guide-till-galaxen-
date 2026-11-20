package se.pundaren.guide.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import se.pundaren.guide.engine.commentary.instructionLabel
import se.pundaren.guide.engine.model.BoundingBox
import se.pundaren.guide.engine.model.EngineOutput
import se.pundaren.guide.engine.model.FactPayload
import se.pundaren.guide.engine.model.MapMode
import se.pundaren.guide.engine.model.Place
import se.pundaren.guide.engine.model.PlaceLayer
import se.pundaren.guide.engine.model.TurnDirection
import se.pundaren.guide.engine.model.factualCategory
import se.pundaren.guide.engine.model.factualPlaceName
import se.pundaren.guide.ui.FlashOverlay
import se.pundaren.guide.ui.GuideCard
import se.pundaren.guide.ui.ProvenanceChip
import se.pundaren.guide.ui.SectionLabel
import se.pundaren.guide.ui.commentaryGlitch
import se.pundaren.guide.ui.rememberGlitchFlash
import se.pundaren.guide.ui.rememberReducedMotion

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapScreen(
    mapMode: MapMode,
    layers: Set<PlaceLayer>,
    places: List<Place>,
    overflow: Boolean,
    facts: FactPayload,
    commentary: EngineOutput?,
    mapsKeyMissing: Boolean,
    glitchToken: Int,
    onMapMode: (MapMode) -> Unit,
    onToggleLayer: (PlaceLayer) -> Unit,
    onManeuver: (FactPayload) -> Unit,
    onSelectPlace: (Place) -> Unit,
    onCamera: (Double, Double, BoundingBox) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val glitch = mapMode == MapMode.IMPROBABILITY
    val flash = rememberGlitchFlash(glitchToken, glitch || commentary?.glitch != null, reducedMotion)
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(59.3293, 18.0686), 12f)
    }
    LaunchedEffect(camera.isMoving) {
        if (camera.isMoving) return@LaunchedEffect
        val bounds = camera.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        val target = camera.position.target
        onCamera(
            target.latitude,
            target.longitude,
            BoundingBox(
                south = bounds.southwest.latitude,
                west = bounds.southwest.longitude,
                north = bounds.northeast.latitude,
                east = bounds.northeast.longitude,
            ),
        )
    }
    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(mapStyleOptions = styleFor(mapMode)),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = true, myLocationButtonEnabled = false),
        ) {
            places.forEach { place ->
                Marker(
                    state = remember(place.id) {
                        MarkerState(LatLng(place.latitude, place.longitude))
                    },
                    title = factualPlaceName(place),
                    snippet = factualCategory(place.category, place.layer),
                    onClick = {
                        onSelectPlace(place)
                        false
                    },
                )
            }
        }
        if (mapMode == MapMode.POETIC) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                MapMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == mapMode,
                        onClick = { onMapMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, MapMode.entries.size),
                    ) {
                        Text(mode.label)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceLayer.entries.forEach { layer ->
                    FilterChip(
                        selected = layer in layers,
                        onClick = { onToggleLayer(layer) },
                        label = { Text(layer.label) },
                    )
                }
            }
            if (mapsKeyMissing) {
                Text(
                    text = "Kartan saknar API-nyckel. Lägg MAPS_API_KEY i local.properties. Platserna laddas ändå.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f))
                        .padding(8.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            InstructionCard(facts)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Maneuver.entries.forEach { maneuver ->
                    FilterChip(
                        selected = false,
                        onClick = { onManeuver(maneuver.facts) },
                        label = { Text(maneuver.label) },
                    )
                }
            }
            commentary?.let { output ->
                Box {
                    GuideCard(
                        modifier = Modifier
                            .heightIn(max = 180.dp)
                            .commentaryGlitch(glitch, reducedMotion)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SectionLabel("Kommentar")
                        ProvenanceChip(output.sourceProvenance ?: se.pundaren.guide.engine.model.Provenance.KONSTRUERAD)
                        Text(
                            text = output.commentary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (overflow) {
                            Text(
                                text = "Zooma in. Alla prickar ryms inte samtidigt.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    FlashOverlay(alpha = flash, modifier = Modifier.matchParentSize())
                }
            }
        }
    }
}

@Composable
private fun InstructionCard(facts: FactPayload) {
    GuideCard {
        SectionLabel("Visuell instruktion")
        Text(
            text = instructionLabel(facts),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = directionWord(facts.direction),
            style = MaterialTheme.typography.displayLarge,
        )
        facts.streetName?.let {
            Text(text = it, style = MaterialTheme.typography.titleMedium)
        }
        if (facts.recalculating) {
            Text(text = "Räknar om rutten", style = MaterialTheme.typography.labelLarge)
        }
        if (facts.gpsUncertain) {
            Text(
                text = "Position osäker",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = "Instruktionen kommer från fakta. Kommentaren får vara fel.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun directionWord(direction: TurnDirection?): String = when (direction) {
    TurnDirection.LEFT -> "←"
    TurnDirection.RIGHT -> "→"
    TurnDirection.STRAIGHT -> "↑"
    TurnDirection.ARRIVED -> "●"
    null -> "◆"
}

private enum class Maneuver(val label: String, val facts: FactPayload) {
    LEFT("Vänster", FactPayload(400, TurnDirection.LEFT, "Sveavägen")),
    RIGHT("Höger", FactPayload(250, TurnDirection.RIGHT, "Götgatan")),
    STRAIGHT("Rakt fram", FactPayload(800, TurnDirection.STRAIGHT, "Hornsgatan")),
    ARRIVED("Framme", FactPayload(direction = TurnDirection.ARRIVED, placeName = "Milliways")),
    WRONG("Fel sväng", FactPayload(200, TurnDirection.LEFT, recalculating = true)),
    GPS("Osäker", FactPayload(300, TurnDirection.STRAIGHT, gpsUncertain = true)),
}

private fun styleFor(mode: MapMode): MapStyleOptions? = when (mode) {
    MapMode.PRACTICAL -> null
    MapMode.POETIC -> MapStyleOptions(POETIC_STYLE)
    MapMode.IMPROBABILITY -> MapStyleOptions(IMPROBABLE_STYLE)
}

private const val POETIC_STYLE = """
[{"featureType":"all","elementType":"labels.text.fill","stylers":[{"color":"#8d93a3"}]},
 {"featureType":"all","elementType":"geometry","stylers":[{"saturation":-70},{"lightness":-20}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#10131a"}]}]
"""

private const val IMPROBABLE_STYLE = """
[{"elementType":"geometry","stylers":[{"color":"#0b0e14"}]},
 {"elementType":"labels.text.fill","stylers":[{"color":"#c799ff"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#1c2028"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#06221f"}]}]
"""
