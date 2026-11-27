package se.pundaren.guide.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import se.pundaren.guide.convoy.Freshness
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.core.platser.PlaceIndex
import se.pundaren.guide.truth.core.platser.PlaceKind
import java.io.File
import kotlin.math.ceil

/**
 * Kartan: MapLibre med den lokala PMTiles-filen (`pmtiles://file://`, T-01 och T-02) och en av tre
 * stilfiler över samma data. Allt som ritas här är sanningsdata: rutten, positionen, målet och
 * ortnamnen ur offlinepaketet. I de tre egna kartlägena körs MapLibre frånkopplat och hämtar aldrig
 * något. I läget Lantmäteriet (ägarens order 2026-11-27) hämtas deras kartbilder över nätet och läggs
 * ovanpå offlinekartan, som syns där en bild saknas. Hämtade bilder ligger kvar på enheten.
 *
 * Stilarna har inga textlager (inga glyfer följer med). Ortnamnen ritas därför som bilder som
 * skapas på enheten först när kartan ber om dem.
 */
class MapController(private val context: Context, private val log: (String) -> Unit) {
    val view: MapView

    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var tiles: File? = null
    private var shownStyle: MapStyle? = null
    private var shownOnline = false

    private var routeLine: List<GeoPoint> = emptyList()
    private var previewLine: List<GeoPoint> = emptyList()
    private var trackLine: List<GeoPoint> = emptyList()
    private var position: GeoPoint? = null
    private var peer: GeoPoint? = null
    private var peerFreshness: Freshness = Freshness.FRESH
    private var destination: GeoPoint? = null
    private var places: PlaceIndex = PlaceIndex.EMPTY

    /** Kartan följer positionen tills användaren drar i den. */
    var following: Boolean = true
        private set
    var onFollowingChanged: (Boolean) -> Unit = {}
    var onLongPress: (GeoPoint) -> Unit = {}

    private val density = context.resources.displayMetrics.density

    init {
        MapLibre.getInstance(context)
        MapLibre.setConnected(false) // utgångsläget: MapLibre hämtar ingenting. Bara läget Lantmäteriet kopplar upp.
        LantmaterietMap.install()
        OfflineManager.getInstance(context).setMaximumAmbientCacheSize(LantmaterietMap.CACHE_BYTES, null)
        // Mörk yta även innan kartstilen är laddad (annars lyser MapLibres ljusa platshållare).
        view = MapView(context, MapLibreMapOptions.createFromAttributes(context).foregroundLoadColor(0xFF0E0E0E.toInt()))
        view.addOnStyleImageMissingListener { id -> if (id.startsWith(LABEL_PREFIX)) style?.addImage(id, labelBitmap(id)) }
    }

    fun onCreate(savedInstanceState: Bundle?) {
        view.onCreate(savedInstanceState)
        view.getMapAsync { m ->
            map = m
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isAttributionEnabled = false // upphovet står alltid i klartext på skärmen i stället
            m.uiSettings.isTiltGesturesEnabled = false
            m.setMinZoomPreference(5.0)
            m.setMaxZoomPreference(17.5)
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && following) {
                    following = false
                    onFollowingChanged(false)
                }
            }
            m.addOnMapLongClickListener { p -> onLongPress(GeoPoint(p.latitude, p.longitude)); true }
            applyStart()
            applyStyle()
        }
    }

    /**
     * Visar kartfilen med vald stil. Anropas när paketet är installerat, när stilen byts och när
     * Lantmäteriets konto läggs in eller tas bort. [online] är sant när kartbilder får hämtas över nätet.
     */
    fun show(pmtiles: File, mapStyle: MapStyle, online: Boolean) {
        if (tiles == pmtiles && shownStyle == mapStyle && shownOnline == online) return
        tiles = pmtiles
        shownStyle = mapStyle
        shownOnline = online
        applyStyle()
    }

    private fun applyStyle() {
        val m = map ?: return
        val file = tiles ?: return
        val which = shownStyle ?: return
        val json = context.assets.open(which.asset).use { it.readBytes().decodeToString() }
            .replace("{{KARTA}}", "pmtiles://file://" + file.absolutePath)
        val first = style == null
        val lantmateriet = which.online && shownOnline
        MapLibre.setConnected(lantmateriet)
        m.setStyle(Style.Builder().fromJson(json)) { s ->
            style = s
            if (lantmateriet) {
                // Lantmäteriets karta läggs ovanpå offlinekartan och under allt som appen själv ritar.
                val tiles = TileSet("2.2.0", LantmaterietMap.TILES).apply {
                    setMaxZoom(LantmaterietMap.MAX_ZOOM)
                    attribution = LantmaterietMap.ATTRIBUTION
                }
                s.addSource(RasterSource(SRC_LANTMATERIET, tiles, 256))
                s.addLayer(RasterLayer("lantmateriet-karta", SRC_LANTMATERIET))
            }
            s.addSource(GeoJsonSource(SRC_PREVIEW)); s.addSource(GeoJsonSource(SRC_ROUTE))
            s.addSource(GeoJsonSource(SRC_DESTINATION)); s.addSource(GeoJsonSource(SRC_POSITION))
            s.addSource(GeoJsonSource(SRC_TRACK))
            s.addSource(GeoJsonSource(SRC_PEER))
            for (rank in 0..2) s.addSource(GeoJsonSource(SRC_PLACES + rank))
            // Det egna spåret: prickad linje under rutten, så att vägen tillbaka syns även utan rutt.
            s.addLayer(LineLayer("spar-kant", SRC_TRACK).withProperties(
                PropertyFactory.lineColor("#000000"), PropertyFactory.lineWidth(6f), PropertyFactory.lineOpacity(0.6f),
                PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")))
            s.addLayer(LineLayer("spar-linje", SRC_TRACK).withProperties(
                PropertyFactory.lineColor("#f2f2f2"), PropertyFactory.lineWidth(3f), PropertyFactory.lineDasharray(arrayOf(0.2f, 2f)),
                PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")))
            s.addLayer(LineLayer("forslag-linje", SRC_PREVIEW).withProperties(
                PropertyFactory.lineColor("#f2f2f2"), PropertyFactory.lineWidth(4f), PropertyFactory.lineOpacity(0.55f),
                PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f))))
            s.addLayer(LineLayer("rutt-kant", SRC_ROUTE).withProperties(
                PropertyFactory.lineColor("#000000"), PropertyFactory.lineWidth(9f), PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")))
            s.addLayer(LineLayer("rutt-linje", SRC_ROUTE).withProperties(
                PropertyFactory.lineColor("#f2f2f2"), PropertyFactory.lineWidth(5f), PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")))
            s.addLayer(CircleLayer("mal-punkt", SRC_DESTINATION).withProperties(
                PropertyFactory.circleColor("#0e0e0e"), PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeColor("#f2f2f2"), PropertyFactory.circleStrokeWidth(4f)))
            val minZoom = floatArrayOf(5f, 8.5f, 10.5f)
            // Lantmäteriets karta har egna ortnamn. Appens namnbilder skulle ligga dubbelt och ritas då inte.
            if (!lantmateriet) for (rank in 0..2) {
                s.addLayer(SymbolLayer("ortnamn-$rank", SRC_PLACES + rank).withProperties(
                    PropertyFactory.iconImage(Expression.get("bild")), PropertyFactory.iconAllowOverlap(false),
                    PropertyFactory.iconPadding(6f)).apply { setMinZoom(minZoom[rank]) })
            }
            s.addLayer(CircleLayer(LAYER_PEER, SRC_PEER).withProperties(
                PropertyFactory.circleColor("#4af8e3"), PropertyFactory.circleRadius(9f),
                PropertyFactory.circleStrokeColor("#4af8e3"), PropertyFactory.circleStrokeWidth(3f)))
            s.addLayer(CircleLayer("position-punkt", SRC_POSITION).withProperties(
                PropertyFactory.circleColor("#ffffff"), PropertyFactory.circleRadius(8f),
                PropertyFactory.circleStrokeColor("#000000"), PropertyFactory.circleStrokeWidth(3f)))
            log("KARTA stil ${which.title} laddad, källa pmtiles://file://…/${file.name}${if (lantmateriet) " med Lantmäteriets kartbilder över nätet" else ""}")
            redraw()
            if (first) position?.let { m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 12.5)) }
        }
    }

    private var startCamera: CameraPosition? = null
    private var startApplied = false

    /**
     * Första kameraläget innan någon position finns: paketets mitt. Kartan kan bli klar före eller
     * efter det här anropet, så läget sparas och sätts när båda finns (annars står kameran kvar på
     * noll grader, sett på målenheten 2026-11-21).
     */
    fun startAt(center: GeoPoint, zoom: Double) {
        startCamera = CameraPosition.Builder().target(LatLng(center.lat, center.lon)).zoom(zoom).build()
        applyStart()
    }

    private fun applyStart() {
        val m = map ?: return
        val camera = startCamera ?: return
        if (startApplied || position != null) return
        startApplied = true
        m.cameraPosition = camera
    }

    fun setRoute(line: List<GeoPoint>) {
        if (line == routeLine) return
        val started = routeLine.isEmpty() && line.isNotEmpty()
        routeLine = line
        style?.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(lineJson(line))
        if (started) { following = true; onFollowingChanged(true) }
    }

    /** En uträknad men inte startad rutt. Kameran ramar in hela förslaget. */
    fun setPreview(line: List<GeoPoint>) {
        if (line == previewLine) return
        previewLine = line
        style?.getSourceAs<GeoJsonSource>(SRC_PREVIEW)?.setGeoJson(lineJson(line))
        if (line.size >= 2) fit(line)
    }

    /** Det egna spåret (sanningsdata: positionerna enheten har haft). */
    fun setTrack(line: List<GeoPoint>) {
        if (line == trackLine) return
        trackLine = line
        style?.getSourceAs<GeoJsonSource>(SRC_TRACK)?.setGeoJson(lineJson(line))
    }

    /**
     * Den andra i konvojen (07, 3.5): fylld prick när positionen är färsk, blekare när den är upp till
     * två minuter gammal, ihålig upp till en kvart. Äldre än så ritas den inte alls (anropa med null).
     */
    fun setPeer(point: GeoPoint?, freshness: Freshness) {
        if (point == peer && freshness == peerFreshness) return
        peer = point
        peerFreshness = freshness
        drawPeer()
    }

    private fun drawPeer() {
        val s = style ?: return
        s.getSourceAs<GeoJsonSource>(SRC_PEER)?.setGeoJson(pointJson(peer))
        val fill = when (peerFreshness) { Freshness.FRESH -> 1f; Freshness.RECENT -> 0.45f; else -> 0f }
        s.getLayerAs<CircleLayer>(LAYER_PEER)?.setProperties(PropertyFactory.circleOpacity(fill))
    }

    /** Flyttar kartan till en punkt. Kartan slutar följa positionen tills man centrerar. */
    fun showPoint(point: GeoPoint) {
        val m = map ?: return
        following = false
        onFollowingChanged(false)
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lon), maxOf(m.cameraPosition.zoom, 13.5)), 700)
    }

    /** Ramar in en linje, t.ex. det egna spåret. Kartan slutar följa positionen tills man centrerar. */
    fun showLine(line: List<GeoPoint>) {
        if (line.size >= 2) fit(line)
    }

    fun setDestination(point: GeoPoint?) {
        if (point == destination) return
        destination = point
        style?.getSourceAs<GeoJsonSource>(SRC_DESTINATION)?.setGeoJson(pointJson(point))
    }

    fun setPosition(point: GeoPoint?, navigating: Boolean) {
        val firstFix = position == null && point != null
        position = point
        style?.getSourceAs<GeoJsonSource>(SRC_POSITION)?.setGeoJson(pointJson(point))
        if (point == null) return
        val m = map ?: return
        if (firstFix) {
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lon), 13.0))
        } else if (following && navigating) {
            val zoom = maxOf(m.cameraPosition.zoom, 14.5)
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.lat, point.lon), zoom), 900)
        }
    }

    fun setPlaces(index: PlaceIndex) {
        if (index === places) return
        places = index
        drawPlaces()
    }

    /** Tillbaka till positionen och följ den igen. */
    fun recenter() {
        following = true
        onFollowingChanged(true)
        val p = position ?: return
        val m = map ?: return
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), maxOf(m.cameraPosition.zoom, 13.5)), 600)
    }

    /** Kartans mittpunkt, t.ex. som startpunkt för en provtur när ingen position finns. */
    fun center(): GeoPoint? = map?.cameraPosition?.target?.let { GeoPoint(it.latitude, it.longitude) }

    private fun fit(line: List<GeoPoint>) {
        val m = map ?: return
        following = false
        onFollowingChanged(false)
        val bounds = LatLngBounds.Builder().apply { line.forEach { include(LatLng(it.lat, it.lon)) } }.build()
        runCatching { m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, (96 * density).toInt()), 700) }
    }

    private fun redraw() {
        val s = style ?: return
        s.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(lineJson(routeLine))
        s.getSourceAs<GeoJsonSource>(SRC_PREVIEW)?.setGeoJson(lineJson(previewLine))
        s.getSourceAs<GeoJsonSource>(SRC_TRACK)?.setGeoJson(lineJson(trackLine))
        s.getSourceAs<GeoJsonSource>(SRC_DESTINATION)?.setGeoJson(pointJson(destination))
        s.getSourceAs<GeoJsonSource>(SRC_POSITION)?.setGeoJson(pointJson(position))
        drawPeer()
        drawPlaces()
    }

    private fun drawPlaces() {
        val s = style ?: return
        val settlements = places.places.filter { it.kind == PlaceKind.SETTLEMENT }
        for (rank in 0..2) {
            val features = settlements.filter { rankOf(it.subKind) == rank }.joinToString(",") { p ->
                """{"type":"Feature","geometry":{"type":"Point","coordinates":[${p.point.lon},${p.point.lat}]},"properties":{"bild":${quote("$LABEL_PREFIX$rank:${p.name}")}}}"""
            }
            s.getSourceAs<GeoJsonSource>(SRC_PLACES + rank)?.setGeoJson("""{"type":"FeatureCollection","features":[$features]}""")
        }
    }

    private fun rankOf(subKind: String) = when (subKind) { "city", "town" -> 0; "village" -> 1; else -> 2 }

    /** Ortnamnet som bild: ljus text med mörk kant, större för större orter. */
    private fun labelBitmap(id: String): Bitmap {
        val rank = id.removePrefix(LABEL_PREFIX).substringBefore(':').toIntOrNull() ?: 2
        val name = id.removePrefix(LABEL_PREFIX).substringAfter(':')
        val size = floatArrayOf(17f, 14f, 12f)[rank.coerceIn(0, 2)] * density
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; color = 0xFFF2F2F2.toInt()
            typeface = if (rank == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val halo = Paint(fill).apply { style = Paint.Style.STROKE; strokeWidth = 3f * density; color = 0xE6000000.toInt() }
        val pad = 4f * density
        val width = ceil(fill.measureText(name) + 2 * pad).toInt().coerceAtLeast(1)
        val height = ceil(fill.fontSpacing + 2 * pad).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val baseline = pad - fill.fontMetrics.ascent
        canvas.drawText(name, pad, baseline, halo)
        canvas.drawText(name, pad, baseline, fill)
        return bitmap
    }

    private fun lineJson(line: List<GeoPoint>): String =
        if (line.size < 2) EMPTY else """{"type":"Feature","geometry":{"type":"LineString","coordinates":[${line.joinToString(",") { "[${it.lon},${it.lat}]" }}]},"properties":{}}"""

    private fun pointJson(p: GeoPoint?): String =
        if (p == null) EMPTY else """{"type":"Feature","geometry":{"type":"Point","coordinates":[${p.lon},${p.lat}]},"properties":{}}"""

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c < ' ' -> append(' ')
            else -> append(c)
        }
        append('"')
    }

    fun onStart() = view.onStart()
    fun onResume() = view.onResume()
    fun onPause() = view.onPause()
    fun onStop() = view.onStop()
    fun onLowMemory() = view.onLowMemory()
    fun onSaveInstanceState(outState: Bundle) = view.onSaveInstanceState(outState)
    fun onDestroy() = view.onDestroy()

    private companion object {
        const val SRC_ROUTE = "rutt"
        const val SRC_PREVIEW = "forslag"
        const val SRC_TRACK = "spar"
        const val SRC_PEER = "konvoj"
        const val SRC_LANTMATERIET = "lantmateriet"
        const val LAYER_PEER = "konvoj-punkt"
        const val SRC_DESTINATION = "mal"
        const val SRC_POSITION = "position"
        const val SRC_PLACES = "ortnamn-"
        const val LABEL_PREFIX = "namn:"
        const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
    }
}
