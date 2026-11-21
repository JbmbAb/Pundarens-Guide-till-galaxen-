package se.pundaren.guide.prototyp.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.SpokenInstructionObserver
import com.stadiamaps.ferrostar.core.http.HttpClientProvider
import com.stadiamaps.ferrostar.core.http.IResponse
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import se.pundaren.guide.prototyp.sanning.paket.PaketInstallerare
import se.pundaren.guide.prototyp.sanning.rutt.ProvPositionskalla
import se.pundaren.guide.prototyp.sanning.rutt.ValhallaRuttkalla
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.RouteDeviation
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.SpokenInstruction
import uniffi.ferrostar.TripState
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep

/**
 * Prototyp för G2/G3. Sanningslager i sin helhet: ingen Illogical Engine finns i appen.
 * Turkost (#4af8e3) används bara i sanningsremsan (F-02). Ingen talsyntes: talinstruktionerna
 * loggas (G4 och val av talmotor är egen grind). Allt loggas till logcat (tagg Etapp2) och till
 * Android/data/<paket>/files/etapp2-logg.txt.
 */
class MainActivity : Activity() {

    companion object {
        const val TAGG = "Etapp2"
        val ORSA = 61.1201175 to 14.6222536
        val GRONKLITT = 61.2077132 to 14.5377551
        val MORA = 61.0086787 to 14.5442519
        // F-03 (ägarbeslut 2026-11-21 06.15): långt prov in i Härjedalen, place=town ur OSM
        val SVEG = 62.0360709 to 14.3598392
        const val TURKOS = 0xFF4AF8E3.toInt()
        const val BAKGRUND = 0xFF0E0E0E.toInt()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var mapView: MapView
    private lateinit var remsa: TextView
    private lateinit var loggVy: TextView
    private var karta: MapLibreMap? = null
    private var valhalla: Valhalla? = null
    private var ruttkalla: ValhallaRuttkalla? = null
    private var core: FerrostarCore? = null
    private val position = ProvPositionskalla { logg(it) }
    private lateinit var loggFil: File
    private var talRaknare = 0
    private var stegForut = -1
    private var avvikelseForut = false
    private val tid = SimpleDateFormat("HH:mm:ss.SSS", Locale.forLanguageTag("sv-SE"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loggFil = File(getExternalFilesDir(null) ?: filesDir, "etapp2-logg.txt")
        MapLibre.getInstance(this)
        MapLibre.setConnected(false) // MapLibre ska aldrig försöka hämta något
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        setContentView(bygglayout())
        logg("START ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        val internet = checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
        logg("INTERNET-behörighet: ${if (internet) "BEVILJAD (fel!)" else "saknas (som avsett)"}")
        scope.launch { forbered() }
    }

    private fun bygglayout(): FrameLayout {
        val rot = FrameLayout(this).apply { setBackgroundColor(BAKGRUND) }
        rot.addView(mapView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        remsa = TextView(this).apply {
            setBackgroundColor(BAKGRUND); setTextColor(TURKOS); textSize = 22f
            typeface = Typeface.DEFAULT_BOLD; setPadding(32, 24, 32, 24); text = "Laddar offlinepaketet…"
        }
        rot.addView(remsa, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xCC0E0E0E.toInt()); setPadding(16, 16, 16, 16)
        }
        val knappar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun knapp(text: String, gora: () -> Unit) = Button(this).apply {
            this.text = text; isAllCaps = false; setOnClickListener { gora() }
        }.also { knappar.addView(it) }
        knapp("Orsa → Grönklitt (passagerare)") { navigera(GRONKLITT, ValhallaRuttkalla.Profil.PASSAGERARE, setOf()) }
        knapp("Orsa → Mora (till fots)") { navigera(MORA, ValhallaRuttkalla.Profil.TILL_FOTS, setOf()) }
        knapp("Orsa → Sveg (passagerare)") { navigera(SVEG, ValhallaRuttkalla.Profil.PASSAGERARE, setOf()) }
        knapp("Avvikelseprov ×3") { navigera(MORA, ValhallaRuttkalla.Profil.PASSAGERARE, setOf(60, 200, 340)) }
        knapp("Stopp") { core?.stopNavigation(); position.stoppa(); logg("STOPP") }
        panel.addView(knappar)
        loggVy = TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 11f; typeface = Typeface.MONOSPACE }
        panel.addView(ScrollView(this).apply { addView(loggVy) }, LinearLayout.LayoutParams(MATCH_PARENT, 420))
        rot.addView(panel, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        return rot
    }

    private suspend fun forbered() {
        val paket = withContext(Dispatchers.IO) {
            val inst = PaketInstallerare(File(filesDir, "offline"))
            val inkommande = File(getExternalFilesDir(null), "inkommande")
            val kalla = if (File(inkommande, "manifest.json").isFile) {
                logg("PAKET läses från $inkommande (adb push)")
                PaketInstallerare.Kalla { File(inkommande, it).inputStream() }
            } else {
                logg("PAKET läses från APK:ns tillgångar")
                PaketInstallerare.Kalla { assets.open("paket/$it") }
            }
            when (val u = runCatching { inst.installera(kalla) }.getOrElse { PaketInstallerare.Utfall.Avvisat(it.toString()) }) {
                is PaketInstallerare.Utfall.Installerat -> {
                    logg("PAKET ${u.paket.version} ${if (u.redanAktivt) "redan aktivt" else "installerat atomärt"} på ${u.millisekunder} ms: " +
                        u.paket.manifest.filer.joinToString { "${it.namn} ${it.storlek / 1024} kB" })
                    u.paket
                }
                is PaketInstallerare.Utfall.Avvisat -> { logg("PAKET AVVISAT: ${u.skal}"); inst.aktivt() }
            }
        }
        if (paket == null) { remsa.text = "Inget offlinepaket. Se loggen."; return }

        // Karta: pmtiles://file:// (T-01, T-02). Stilen saknar textlager, så inga glyfer hämtas.
        val stil = assets.open("stil/mork.json").use { it.readBytes().decodeToString() }
            .replace("{{KARTA}}", "pmtiles://file://" + paket.fil("karta.pmtiles").absolutePath)
        mapView.getMapAsync { m ->
            karta = m
            m.cameraPosition = CameraPosition.Builder().target(LatLng(ORSA.first, ORSA.second)).zoom(12.5).build()
            m.setStyle(Style.Builder().fromJson(stil)) { s ->
                s.addSource(GeoJsonSource("rutt")); s.addSource(GeoJsonSource("position"))
                s.addLayer(LineLayer("rutt-kant", "rutt").withProperties(
                    PropertyFactory.lineColor("#000000"), PropertyFactory.lineWidth(9f)))
                s.addLayer(LineLayer("rutt-linje", "rutt").withProperties(
                    PropertyFactory.lineColor("#f2f2f2"), PropertyFactory.lineWidth(5f)))
                s.addLayer(CircleLayer("position-punkt", "position").withProperties(
                    PropertyFactory.circleColor("#ffffff"), PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleStrokeColor("#000000"), PropertyFactory.circleStrokeWidth(3f)))
                logg("KARTA stil laddad, källa pmtiles://file://…/karta.pmtiles")
            }
        }

        // Ruttning: valhalla-mobile mot tar-utdraget (T-05).
        val v = withContext(Dispatchers.IO) {
            val t0 = System.nanoTime()
            val config = ValhallaConfigFactory.usingTileExtract(paket.fil("valhalla_tiles.tar").absolutePath)
            Valhalla(this@MainActivity, config).also { logg("VALHALLA startad på ${(System.nanoTime() - t0) / 1_000_000} ms") }
        }
        valhalla = v
        val rk = ValhallaRuttkalla(v, ValhallaRuttkalla.Profil.PASSAGERARE) { rad, ratt -> logg(rad); if (ratt != null) sparaRatt(ratt) }
        ruttkalla = rk
        val config = NavigationControllerConfig(
            WaypointAdvanceMode.WaypointWithinRange(100.0),
            stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
            stepAdvanceDistanceToEndOfStep(10u, 32u),
            RouteDeviationTracking.StaticThreshold(15u, 50.0),
            CourseFiltering.SNAP_TO_ROUTE,
        )
        // HTTP-klienten krävs av konstruktorn men används inte med CustomRouteProvider. Den här vägrar
        // alltid, så ett eventuellt anrop syns i loggen i stället för att gå ut på nätet.
        val ingetNatverk = object : HttpClientProvider {
            override suspend fun call(request: uniffi.ferrostar.RouteRequest): IResponse {
                logg("FEL: Ferrostar försökte göra ett HTTP-anrop; stoppat")
                throw IllegalStateException("Prototypen har inget nätverk")
            }
        }
        val c = FerrostarCore(rk, ingetNatverk, position, config)
        c.spokenInstructionObserver = object : SpokenInstructionObserver {
            private val tyst = MutableStateFlow(false)
            override fun onSpokenInstructionTrigger(spokenInstruction: SpokenInstruction) {
                talRaknare++; logg("TAL #$talRaknare: ${spokenInstruction.text}")
            }
            override fun stopAndClearQueue() {}
            override fun setMuted(isMuted: Boolean) { tyst.value = isMuted }
            override val muteState: StateFlow<Boolean> = tyst
        }
        core = c
        scope.launch {
            c.state.collect { st ->
                val ts = st.tripState
                if (st.isCalculatingNewRoute) { remsa.text = "Räknar om rutten …"; logg("OMRÄKNING pågår") }
                when (ts) {
                    is TripState.Navigating -> {
                        val instr = ts.visualInstruction?.primaryContent?.text ?: "–"
                        remsa.text = "%s\n%.0f m · %.1f km kvar".format(instr, ts.progress.distanceToNextManeuver, ts.progress.distanceRemaining / 1000)
                        if (ts.remainingSteps.size != stegForut) { stegForut = ts.remainingSteps.size; logg("STEG kvar ${ts.remainingSteps.size}: $instr") }
                        val avv = ts.deviation is RouteDeviation.Deviation
                        if (avv != avvikelseForut) { avvikelseForut = avv; logg(if (avv) "AVVIKELSE upptäckt: ${ts.deviation}" else "PÅ RUTTEN igen") }
                        visaPosition(ts.snappedUserLocation)
                    }
                    is TripState.Complete -> { remsa.text = "Framme."; logg("FRAMME (Complete)") }
                    else -> {}
                }
                visaRutt(st.routeGeometry)
            }
        }
        remsa.text = "Offlinepaket ${paket.version}. Välj en provtur."
    }

    private fun navigera(mal: Pair<Double, Double>, profil: ValhallaRuttkalla.Profil, avvikelseVid: Set<Int>) {
        val c = core ?: return logg("Inte redo än")
        ruttkalla?.profil = profil
        talRaknare = 0; stegForut = -1
        scope.launch {
            try {
                val start = UserLocation(GeographicCoordinate(ORSA.first, ORSA.second), 5.0, null, java.time.Instant.now(), null)
                val rutter = c.getRoutes(start, listOf(Waypoint(GeographicCoordinate(mal.first, mal.second), WaypointKind.BREAK, null)))
                val r = rutter.first()
                c.startNavigation(r)
                position.starta(r, takt = if (profil == ValhallaRuttkalla.Profil.TILL_FOTS) 8 else 4, avvikelseVid = avvikelseVid)
                logg("NAVIGERING startad (${profil.name}), simulerad position, avvikelseförsök vid $avvikelseVid")
            } catch (e: Exception) {
                logg("FEL i ruttning: $e")
            }
        }
    }

    private fun visaRutt(geo: List<GeographicCoordinate>) {
        val s = karta?.style ?: return
        if (geo.isEmpty()) return
        s.getSourceAs<GeoJsonSource>("rutt")?.setGeoJson(LineString.fromLngLats(geo.map { Point.fromLngLat(it.lng, it.lat) }))
        val ram = LatLngBounds.Builder().apply { geo.forEach { include(LatLng(it.lat, it.lng)) } }.build()
        karta?.moveCamera(CameraUpdateFactory.newLatLngBounds(ram, 80))
    }

    private fun visaPosition(p: UserLocation) {
        karta?.style?.getSourceAs<GeoJsonSource>("position")?.setGeoJson(Point.fromLngLat(p.coordinates.lng, p.coordinates.lat))
    }

    private fun sparaRatt(json: String) {
        runCatching { File(loggFil.parentFile, "rutt-${System.currentTimeMillis()}.osrm.json").writeText(json) }
    }

    private val huvudtrad = Handler(Looper.getMainLooper())
    private fun logg(rad: String) {
        val r = "${tid.format(Date())} $rad"
        Log.i(TAGG, r)
        runCatching { loggFil.appendText(r + "\n") }
        huvudtrad.post { if (::loggVy.isInitialized) loggVy.text = (r + "\n" + loggVy.text).take(6000) }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onDestroy() {
        core?.stopNavigation(); valhalla?.close(); scope.cancel(); mapView.onDestroy(); super.onDestroy()
    }
}
