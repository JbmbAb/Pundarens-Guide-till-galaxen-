package se.pundaren.guide.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import se.pundaren.guide.ui.archive.ArchiveScreen
import se.pundaren.guide.ui.babel.BabelScreen
import se.pundaren.guide.ui.drive.DriveScreen
import se.pundaren.guide.ui.home.HomeScreen
import se.pundaren.guide.ui.map.MapScreen

@Composable
fun GuideScaffold(state: GuideUiState, viewModel: GuideViewModel) {
    if (!state.ready) {
        Scaffold { padding ->
            Text(
                text = state.error ?: "Laddar arkivet…",
                modifier = Modifier.padding(padding).padding(24.dp),
            )
        }
        return
    }
    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    Destination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = state.destination == destination,
                            onClick = { viewModel.select(destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                DestinationContent(state, viewModel, Modifier.weight(1f))
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        Destination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = state.destination == destination,
                                onClick = { viewModel.select(destination) },
                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                DestinationContent(state, viewModel, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun DestinationContent(state: GuideUiState, viewModel: GuideViewModel, modifier: Modifier) {
    Box(modifier.fillMaxSize()) {
        when (state.destination) {
            Destination.HOME -> HomeScreen(state.drive, state.homeOutput, viewModel::dontPanic)
            Destination.MAP -> MapScreen(
                mapMode = state.mapMode,
                layers = state.layers,
                places = state.places,
                overflow = state.placeOverflow,
                facts = state.facts,
                commentary = state.commentary,
                mapsKeyMissing = state.mapsKeyMissing,
                glitchToken = state.glitchToken,
                onMapMode = viewModel::setMapMode,
                onToggleLayer = viewModel::toggleLayer,
                onManeuver = viewModel::showManeuver,
                onSelectPlace = viewModel::selectPlace,
                onCamera = viewModel::onCamera,
            )
            Destination.BABEL -> BabelScreen(
                archive = state.archive,
                text = state.babelText,
                mode = state.babelMode,
                reverse = state.babelReverse,
                output = state.babelOutput,
                analysis = state.reverse,
                onText = viewModel::updateBabelText,
                onMode = viewModel::updateBabelMode,
                onReverse = viewModel::setReverse,
                onRun = viewModel::runBabel,
            )
            Destination.ARCHIVE -> ArchiveScreen(state.archive)
            Destination.DRIVE -> DriveScreen(state.drive, viewModel::updateDrive)
        }
    }
}

private val Destination.icon: ImageVector
    get() = when (this) {
        Destination.HOME -> Icons.Filled.Home
        Destination.MAP -> Icons.Filled.Place
        Destination.BABEL -> Icons.Filled.Forum
        Destination.ARCHIVE -> Icons.AutoMirrored.Filled.MenuBook
        Destination.DRIVE -> Icons.Filled.Settings
    }
