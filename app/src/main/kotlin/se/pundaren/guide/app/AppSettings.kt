package se.pundaren.guide.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.TravelMode

/**
 * Kartans lägen. De tre första är tre stilfiler över samma offlinedata (B-09). Det fjärde lägger
 * Lantmäteriets topografiska karta ovanpå den praktiska stilen och hämtar kartbilder över nätet.
 */
enum class MapStyle(val asset: String, val title: String, val online: Boolean = false) {
    PRAKTISK("stil/praktisk.json", "Praktisk"),
    POETISK("stil/poetisk.json", "Poetisk"),
    OSANNOLIK("stil/osannolik.json", "Osannolik"),
    LANTMATERIET("stil/praktisk.json", "Lantmäteriet", online = true),
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
    /** Vald röst för beskeden respektive Guiden (namn ur talmotorn). Null betyder appens eget val. */
    val instructionVoice: String? = null,
    val tailVoice: String? = null,
    /** Adressen till Convoy-reläet (https). Tom tills ägaren har skrivit in den. */
    val convoyRelayUrl: String = "",
    /** Absurditetsgraden, 0–100 %: hur långt Guidens beslut får gå. Rör aldrig rutt eller besked. */
    val absurdityPercent: Int = 60,
    /** Antalet registrerade löften. Löftenas text sparas aldrig. */
    val promiseCount: Int = 0,
    /** Antalet upplåsta steg i Omvändelseprogrammet, 0–12. */
    val conversionStep: Int = 0,
    /** Claude får välja beslut vid DON'T PANIC. Då lämnar det användaren skrev enheten. Av tills ägaren slår på det. */
    val claudeChooses: Boolean = false,
)

/** Inställningarna, sparade i appens privata lagring. Ingenting här lämnar enheten. Nätnycklarna ligger i [SecretStore]. */
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
            instructionVoice = prefs.getString("instruction_voice", null),
            tailVoice = prefs.getString("tail_voice", null),
            convoyRelayUrl = prefs.getString("convoy_relay_url", null).orEmpty(),
            absurdityPercent = prefs.getInt("absurdity", 60).coerceIn(0, 100),
            promiseCount = prefs.getInt("promise_count", 0).coerceAtLeast(0),
            conversionStep = prefs.getInt("conversion_step", 0).coerceIn(0, 12),
            claudeChooses = prefs.getBoolean("claude_chooses", false),
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
            .putString("instruction_voice", next.instructionVoice)
            .putString("tail_voice", next.tailVoice)
            .putString("convoy_relay_url", next.convoyRelayUrl)
            .putInt("absurdity", next.absurdityPercent)
            .putInt("promise_count", next.promiseCount)
            .putInt("conversion_step", next.conversionStep)
            .putBoolean("claude_chooses", next.claudeChooses)
            .apply {
                if (next.home != null) putString("home_lat", next.home.lat.toString()).putString("home_lon", next.home.lon.toString())
                else remove("home_lat").remove("home_lon")
            }
            .apply()
    }
}
