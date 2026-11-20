package se.pundaren.guide.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.pundaren.guide.BuildConfig
import se.pundaren.guide.data.AssetArchiveSource
import se.pundaren.guide.data.AssetGeoJsonStore
import se.pundaren.guide.engine.DefaultIllogicalEngine
import se.pundaren.guide.engine.model.BabelMode
import se.pundaren.guide.engine.model.BoundingBox
import se.pundaren.guide.engine.model.EngineOutput
import se.pundaren.guide.engine.model.FactPayload
import se.pundaren.guide.engine.model.LoadedArchive
import se.pundaren.guide.engine.model.MapMode
import se.pundaren.guide.engine.model.Place
import se.pundaren.guide.engine.model.PlaceLayer
import se.pundaren.guide.engine.model.ProbabilityDrive
import se.pundaren.guide.engine.model.ReverseAnalysis
import se.pundaren.guide.engine.model.TurnDirection
import se.pundaren.guide.engine.model.factualPlaceName
import se.pundaren.guide.engine.places.BundledGeoJsonPlacesRepository
import se.pundaren.guide.engine.places.distanceMeters

enum class Destination(val label: String) {
    HOME("Hem"),
    MAP("Karta"),
    BABEL("Babel"),
    ARCHIVE("Arkiv"),
    DRIVE("Drift"),
}

data class GuideUiState(
    val ready: Boolean = false,
    val error: String? = null,
    val destination: Destination = Destination.HOME,
    val drive: ProbabilityDrive = ProbabilityDrive(),
    val archive: LoadedArchive = LoadedArchive.EMPTY,
    val homeOutput: EngineOutput? = null,
    val babelText: String = "",
    val babelMode: BabelMode = BabelMode.ANSVARSFORSKJUTAREN,
    val babelReverse: Boolean = false,
    val babelOutput: EngineOutput? = null,
    val reverse: ReverseAnalysis? = null,
    val babelBusy: Boolean = false,
    val mapMode: MapMode = MapMode.PRACTICAL,
    val layers: Set<PlaceLayer> = PlaceLayer.entries.toSet(),
    val places: List<Place> = emptyList(),
    val placeOverflow: Boolean = false,
    val facts: FactPayload = FactPayload(
        distanceMeters = 400,
        direction = TurnDirection.LEFT,
        streetName = "Sveavägen",
    ),
    val commentary: EngineOutput? = null,
    val selectedPlace: Place? = null,
    val cameraLat: Double = 59.3293,
    val cameraLon: Double = 18.0686,
    val mapsKeyMissing: Boolean = false,
    val glitchToken: Int = 0,
)

class GuideViewModel(app: Application) : AndroidViewModel(app) {
    private val engine = DefaultIllogicalEngine()
    private val repository = BundledGeoJsonPlacesRepository(AssetGeoJsonStore(app))
    private val _state = MutableStateFlow(GuideUiState())
    val state: StateFlow<GuideUiState> = _state.asStateFlow()
    private var viewport: BoundingBox = BoundingBox.around(59.3293, 18.0686, 0.08)
    private var placeRequest = 0

    init {
        viewModelScope.launch {
            try {
                val bundle = withContext(Dispatchers.IO) { AssetArchiveSource(app).bundle() }
                engine.load(bundle)
                val drive = _state.value.drive.copy(mapMode = _state.value.mapMode)
                val facts = _state.value.facts
                val commentary = engine.commentaryFor(facts, drive)
                val places = loadPlaces(viewport, _state.value.layers)
                _state.update {
                    it.copy(
                        ready = true,
                        archive = engine.archive(),
                        commentary = commentary,
                        places = places.first,
                        placeOverflow = places.second,
                        drive = drive,
                        mapsKeyMissing = BuildConfig.MAPS_API_KEY.isBlank() ||
                            BuildConfig.MAPS_API_KEY == "MISSING_MAPS_API_KEY",
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(error = "Arkivet gick inte att läsa.") }
            }
        }
    }

    fun select(destination: Destination) {
        _state.update { it.copy(destination = destination) }
    }

    fun dontPanic() {
        val output = engine.dontPanic(_state.value.drive)
        _state.update { it.copy(homeOutput = output, glitchToken = it.glitchToken + if (output.glitch != null) 1 else 0) }
    }

    fun updateBabelText(text: String) {
        _state.update { it.copy(babelText = text) }
    }

    fun updateBabelMode(mode: BabelMode) {
        _state.update { it.copy(babelMode = mode) }
    }

    fun setReverse(enabled: Boolean) {
        _state.update { it.copy(babelReverse = enabled, babelOutput = null, reverse = null) }
    }

    fun runBabel() {
        val current = _state.value
        if (current.babelBusy) return
        _state.update { it.copy(babelBusy = true) }
        viewModelScope.launch {
            val reverse = if (current.babelReverse) {
                withContext(Dispatchers.Default) { engine.analyze(current.babelText) }
            } else {
                null
            }
            val output = if (current.babelReverse) {
                null
            } else {
                withContext(Dispatchers.Default) {
                    engine.translate(current.babelText, current.babelMode.id, current.drive)
                }
            }
            _state.update { it.copy(babelBusy = false, reverse = reverse, babelOutput = output) }
        }
    }

    fun setMapMode(mode: MapMode) {
        val drive = _state.value.drive.copy(mapMode = mode)
        val commentary = engine.commentaryFor(_state.value.facts, drive)
        _state.update {
            it.copy(
                mapMode = mode,
                drive = drive,
                commentary = commentary,
                glitchToken = it.glitchToken + if (mode == MapMode.IMPROBABILITY) 1 else 0,
            )
        }
    }

    fun toggleLayer(layer: PlaceLayer) {
        val current = _state.value.layers
        val next = if (layer in current) current - layer else current + layer
        _state.update { it.copy(layers = next) }
        refreshPlaces(next, viewport, _state.value.cameraLat, _state.value.cameraLon)
    }

    fun showManeuver(facts: FactPayload) {
        val commentary = engine.commentaryFor(facts, _state.value.drive)
        _state.update {
            it.copy(
                facts = facts,
                commentary = commentary,
                selectedPlace = null,
                glitchToken = it.glitchToken + if (commentary.glitch != null) 1 else 0,
            )
        }
    }

    fun selectPlace(place: Place) {
        val current = _state.value
        val meters = distanceMeters(current.cameraLat, current.cameraLon, place.latitude, place.longitude)
        val facts = FactPayload(
            distanceMeters = meters,
            placeName = factualPlaceName(place),
            placeCategory = place.category,
        )
        val commentary = engine.commentaryFor(facts, current.drive)
        _state.update {
            it.copy(
                selectedPlace = place,
                facts = facts,
                commentary = commentary,
                glitchToken = it.glitchToken + if (commentary.glitch != null) 1 else 0,
            )
        }
    }

    fun onCamera(latitude: Double, longitude: Double, bbox: BoundingBox) {
        viewport = bbox
        _state.update { it.copy(cameraLat = latitude, cameraLon = longitude) }
        refreshPlaces(_state.value.layers, bbox, latitude, longitude)
    }

    fun updateDrive(drive: ProbabilityDrive) {
        val withMode = drive.copy(mapMode = _state.value.mapMode)
        val commentary = _state.value.commentary?.let {
            engine.commentaryFor(_state.value.facts, withMode)
        }
        _state.update { it.copy(drive = withMode, commentary = commentary ?: it.commentary) }
    }

    private fun refreshPlaces(
        layers: Set<PlaceLayer>,
        bbox: BoundingBox,
        latitude: Double,
        longitude: Double,
    ) {
        val request = ++placeRequest
        viewModelScope.launch {
            val loaded = loadPlaces(bbox, layers, latitude, longitude)
            if (request != placeRequest) return@launch
            _state.update { it.copy(places = loaded.first, placeOverflow = loaded.second) }
        }
    }

    private suspend fun loadPlaces(
        bbox: BoundingBox,
        layers: Set<PlaceLayer>,
        latitude: Double = 59.3293,
        longitude: Double = 18.0686,
    ): Pair<List<Place>, Boolean> {
        val places = repository.getPlaces(bbox, layers)
        if (places.size <= 180) return places to false
        val nearest = places.sortedBy { distanceMeters(latitude, longitude, it.latitude, it.longitude) }.take(180)
        return nearest to true
    }
}
