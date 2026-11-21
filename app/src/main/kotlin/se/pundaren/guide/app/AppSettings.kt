package se.pundaren.guide.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.TravelMode

/** Kartans tre lägen: tre stilfiler över samma data (B-09). */
enum class MapStyle(val asset: String, val title: String) {
    PRAKTISK("stil/praktisk.json", "Praktisk"),
    POETISK("stil/poetisk.json", "Poetisk"),
    OSANNOLIK("stil/osannolik.json", "Osannolik"),
}

data class Settings(
    val travelMode: TravelMode = TravelMode.FOOT,
    val mapStyle: MapStyle = MapStyle.PRAKTISK,
    /** Guiden får prata (svansar). Påverkar aldrig beskeden. */
    val guideEnabled: Boolean = true,
    /** Pratsamhet, 0–100 %: hur stor andel av händelserna som alls får ge en svans. */
    val chattinessPercent: Int = 100,
    /** Beskedsrösten avstängd. Sanningsremsan visar ändå allt. */
    val instructionsMuted: Boolean = false,
    /** Hem, för nödkortets riktning och avstånd. Sparas bara på enheten. */
    val home: GeoPoint? = null,
)

/** Inställningarna, sparade i appens privata lagring. Ingenting här lämnar enheten. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("installningar", Context.MODE_PRIVATE)

    private val _value = MutableStateFlow(load())
    val value: StateFlow<Settings> = _value.asStateFlow()

    private fun load(): Settings {
        val lat = prefs.getString("home_lat", null)?.toDoubleOrNull()
        val lon = prefs.getString("home_lon", null)?.toDoubleOrNull()
        return Settings(
            travelMode = runCatching { TravelMode.valueOf(prefs.getString("travel_mode", null).orEmpty()) }.getOrDefault(TravelMode.FOOT)
                .takeIf { it == TravelMode.FOOT || it == TravelMode.PASSENGER } ?: TravelMode.FOOT,
            mapStyle = runCatching { MapStyle.valueOf(prefs.getString("map_style", null).orEmpty()) }.getOrDefault(MapStyle.PRAKTISK),
            guideEnabled = prefs.getBoolean("guide_enabled", true),
            chattinessPercent = prefs.getInt("chattiness", 100).coerceIn(0, 100),
            instructionsMuted = prefs.getBoolean("instructions_muted", false),
            home = if (lat != null && lon != null) GeoPoint(lat, lon) else null,
        )
    }

    fun update(change: (Settings) -> Settings) {
        val next = change(_value.value)
        _value.value = next
        prefs.edit()
            .putString("travel_mode", next.travelMode.name)
            .putString("map_style", next.mapStyle.name)
            .putBoolean("guide_enabled", next.guideEnabled)
            .putInt("chattiness", next.chattinessPercent)
            .putBoolean("instructions_muted", next.instructionsMuted)
            .apply {
                if (next.home != null) putString("home_lat", next.home.lat.toString()).putString("home_lon", next.home.lon.toString())
                else remove("home_lat").remove("home_lon")
            }
            .apply()
    }
}
