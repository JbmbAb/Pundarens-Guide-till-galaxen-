package se.pundaren.guide.truth.core.handduk

import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.core.geo.Sun
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class CheckStatus { OK, WARNING, UNKNOWN }

/** En punkt i avfärdskontrollen: vad som kontrollerades, utfallet och det sanna värdet bakom. */
data class CheckItem(val id: String, val title: String, val status: CheckStatus, val value: String)

/**
 * Handduken ("Towel Secured", byggplanen: varje skämt ska bära last). Under skämtet ligger en
 * avfärdskontroll med fem sanna värden: batteri, att kartpaketet täcker turen, att hem är sparat,
 * dagsljus kvar vid ankomst och att positionen är aktuell. "Okänd" visas när ett värde saknas;
 * ingenting gissas (G6).
 */
data class DepartureCheck(val items: List<CheckItem>) {
    /** Handduken är säkrad bara när varje punkt är kontrollerad och godkänd. */
    val secured: Boolean get() = items.all { it.status == CheckStatus.OK }

    val headline: String
        get() {
            val open = items.count { it.status != CheckStatus.OK }
            return if (open == 0) "Handduken är säkrad." else "Handduken är inte säkrad: $open av ${items.size} punkter återstår."
        }

    companion object {
        /** Kandidatvärden, inte frysta. */
        const val BATTERY_WARNING_PERCENT = 30
        const val DAYLIGHT_MARGIN_SECONDS = 30 * 60L

        private val clock = DateTimeFormatter.ofPattern("HH.mm")

        fun build(
            batteryPercent: Int,
            charging: Boolean,
            /** Kartpaketets gränsrektangel i WGS84 (väst, syd, öst, nord), eller null om manifestet saknar den. */
            packageBbox: List<Double>?,
            origin: GeoPoint?,
            destination: GeoPoint?,
            home: GeoPoint?,
            arrivalUtc: Instant?,
            positionQuality: PositionQuality,
            zone: ZoneId,
        ): DepartureCheck = DepartureCheck(
            listOf(
                battery(batteryPercent, charging),
                coverage(packageBbox, origin, destination),
                home(home),
                daylight(arrivalUtc, destination, zone),
                position(positionQuality),
            ),
        )

        private fun battery(percent: Int, charging: Boolean) = CheckItem(
            "batteri", "Batteri",
            if (percent >= BATTERY_WARNING_PERCENT || charging) CheckStatus.OK else CheckStatus.WARNING,
            when {
                charging -> "$percent %, laddar"
                percent >= BATTERY_WARNING_PERCENT -> "$percent %"
                else -> "$percent %, under $BATTERY_WARNING_PERCENT %"
            },
        )

        private fun coverage(bbox: List<Double>?, origin: GeoPoint?, destination: GeoPoint?): CheckItem {
            val title = "Kartpaketet täcker turen"
            if (bbox == null || bbox.size != 4) return CheckItem("kartpaket", title, CheckStatus.UNKNOWN, "Okänd: paketet anger ingen täckning")
            if (origin == null || destination == null) return CheckItem("kartpaket", title, CheckStatus.UNKNOWN, "Okänd: start eller mål saknas")
            fun inside(p: GeoPoint) = p.lon >= bbox[0] && p.lon <= bbox[2] && p.lat >= bbox[1] && p.lat <= bbox[3]
            return when {
                !inside(origin) -> CheckItem("kartpaket", title, CheckStatus.WARNING, "Starten ligger utanför kartpaketet")
                !inside(destination) -> CheckItem("kartpaket", title, CheckStatus.WARNING, "Målet ligger utanför kartpaketet")
                else -> CheckItem("kartpaket", title, CheckStatus.OK, "Start och mål ligger inom paketet")
            }
        }

        private fun home(home: GeoPoint?) = CheckItem(
            "hem", "Hem är sparat",
            if (home != null) CheckStatus.OK else CheckStatus.WARNING,
            if (home != null) "Sparat. Nödkortet kan visa riktning och avstånd hem" else "Inte sparat. Nödkortet kan inte visa vägen hem",
        )

        private fun daylight(arrivalUtc: Instant?, destination: GeoPoint?, zone: ZoneId): CheckItem {
            val title = "Dagsljus vid ankomst"
            if (arrivalUtc == null || destination == null) return CheckItem("dagsljus", title, CheckStatus.UNKNOWN, "Okänd: ingen ankomsttid")
            val at = clock.format(arrivalUtc.atZone(zone))
            val left = Sun.daylightSecondsLeft(arrivalUtc, destination)
            return when {
                left == null -> CheckItem("dagsljus", title, CheckStatus.OK, "Solen går inte ned det dygnet (ankomst $at)")
                left <= 0L -> CheckItem("dagsljus", title, CheckStatus.WARNING, "Mörkt vid ankomst $at")
                left < DAYLIGHT_MARGIN_SECONDS -> CheckItem("dagsljus", title, CheckStatus.WARNING, "Bara ${left / 60} min dagsljus kvar vid ankomst $at")
                else -> CheckItem("dagsljus", title, CheckStatus.OK, "${duration(left)} dagsljus kvar vid ankomst $at")
            }
        }

        private fun position(quality: PositionQuality) = CheckItem(
            "position", "Aktuell position",
            if (quality == PositionQuality.GOOD) CheckStatus.OK else CheckStatus.WARNING,
            when (quality) {
                PositionQuality.GOOD -> "Positionen är aktuell"
                PositionQuality.DEGRADED -> "Positionen är osäker"
                PositionQuality.LOST -> "Ingen aktuell position"
            },
        )

        private fun duration(seconds: Long): String {
            val minutes = seconds / 60
            return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
        }
    }
}
