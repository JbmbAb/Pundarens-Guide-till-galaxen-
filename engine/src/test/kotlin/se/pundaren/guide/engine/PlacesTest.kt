package se.pundaren.guide.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.pundaren.guide.engine.model.BoundingBox
import se.pundaren.guide.engine.model.PlaceLayer
import se.pundaren.guide.engine.places.BundledGeoJsonPlacesRepository
import se.pundaren.guide.engine.places.GeoJsonParser
import se.pundaren.guide.engine.places.GeoJsonStore
import se.pundaren.guide.engine.places.distanceMeters
import java.io.File

class PlacesTest {
    private val data = File(System.getProperty("guide.data"))
    private val parser = GeoJsonParser()

    @Test
    fun parsesBundledStockholmLayers() {
        val toilets = parser.parse(read("toilets.geojson"), PlaceLayer.TOILETS)
        val rests = parser.parse(read("rest_areas.geojson"), PlaceLayer.REST_AREAS)
        val munchies = parser.parse(read("munchie_stations.geojson"), PlaceLayer.MUNCHIE_STATIONS)
        assertEquals(892, toilets.size)
        assertEquals(17, rests.size)
        assertEquals(2292, munchies.size)
        assertEquals("Roslagsstoppet", rests.first { it.id == "node/2786988979" }.name)
        assertTrue(munchies.any { it.openLate == null })
        assertTrue(toilets.all { it.latitude in 58.0..60.5 && it.longitude in 17.0..19.6 })
    }

    @Test
    fun repositoryFiltersByBoundingBox() = runBlocking {
        val store = object : GeoJsonStore {
            override suspend fun read(layer: PlaceLayer): String = when (layer) {
                PlaceLayer.TOILETS -> """{"type":"FeatureCollection","features":[],"ignored":true}"""
                PlaceLayer.REST_AREAS -> read("rest_areas.geojson")
                PlaceLayer.MUNCHIE_STATIONS -> """{"type":"FeatureCollection","extra":1,"features":[
                  {"type":"Feature","geometry":{"type":"Point","coordinates":[18.07,59.33]},"properties":{"osm_id":"node/1","category":"kiosk","name":"Testkiosk","open_late":true,"surprise":"ja"}}
                ]}"""
            }
        }
        val repository = BundledGeoJsonPlacesRepository(store)
        val near = repository.getPlaces(
            BoundingBox(59.63, 18.32, 59.64, 18.34),
            setOf(PlaceLayer.REST_AREAS),
        )
        assertEquals(listOf("Roslagsstoppet"), near.map { it.name })
        val none = repository.getPlaces(
            BoundingBox(0.0, 0.0, 0.1, 0.1),
            PlaceLayer.entries.toSet(),
        )
        assertTrue(none.isEmpty())
        val kiosk = repository.getPlaces(BoundingBox.world(), setOf(PlaceLayer.MUNCHIE_STATIONS)).single()
        assertEquals("Testkiosk", kiosk.name)
        assertEquals(true, kiosk.openLate)
        val meters = distanceMeters(59.3293, 18.0686, 59.634795, 18.331544)
        assertTrue(meters in 30_000..45_000)
    }

    private fun read(name: String): String =
        data.resolve("geojson/stockholms-lan/$name").readText()
}
