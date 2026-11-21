package se.pundaren.guide.app

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import se.pundaren.guide.app.ui.GuideScreen
import se.pundaren.guide.app.ui.GuideTheme

/**
 * Appens enda aktivitet. Äger kompositionsroten ([AppGraph]) och kartan ([MapController]).
 * Skärmen hålls tänd medan en rutt är aktiv (telefonen eller plattan sitter i fäste, röst först).
 */
class MainActivity : ComponentActivity() {
    private lateinit var graph: AppGraph
    private lateinit var map: MapController

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        graph.log.line("POSITION: behörighet ${if (granted) "beviljad" else "nekad"}")
        if (granted) graph.navigator.startLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        graph = AppGraph(this)
        map = MapController(this, graph.log::line)
        map.onCreate(savedInstanceState)

        if (graph.navigator.hasLocationPermission()) graph.navigator.startLocation()
        else askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)

        lifecycleScope.launch {
            graph.navigator.state.navigation.collect { nav ->
                if (nav.activeRoute) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        setContent {
            GuideTheme {
                GuideScreen(graph, map, onRequestLocation = { askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION) })
            }
        }
    }

    override fun onStart() { super.onStart(); map.onStart() }
    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { map.onPause(); super.onPause() }
    override fun onStop() { map.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); map.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); map.onSaveInstanceState(outState) }

    override fun onDestroy() {
        map.onDestroy()
        graph.close()
        super.onDestroy()
    }
}
