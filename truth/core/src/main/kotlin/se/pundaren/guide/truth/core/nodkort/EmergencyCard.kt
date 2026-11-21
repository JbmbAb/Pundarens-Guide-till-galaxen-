package se.pundaren.guide.truth.core.nodkort

import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.api.PositionQuality
import se.pundaren.guide.truth.core.geo.Geo
import se.pundaren.guide.truth.core.geo.Sweref99
import kotlin.math.roundToInt

/**
 * Nödkortet (byggplanen: "Don't Panic, långt tryck"). Bara sanna värden ur Hard Truth Core:
 * koordinater att läsa upp, riktning och avstånd hem och batteri. Inget trams, inga animationer.
 *
 * Kortet ersätter inte 112. Det gör bara att rätt uppgifter finns framme när man ringer.
 */
data class EmergencyCard(
    /** Sant om det finns en position att läsa upp. */
    val hasPosition: Boolean,
    /** "N 61° 07,207′  E 14° 37,335′" – grader och decimalminuter (WGS84). */
    val degreesMinutes: String?,
    /** "61,12012° N, 14,62225° E" – decimalgrader (WGS84). */
    val decimalDegrees: String?,
    /** "N 6777000  E 479700" – SWEREF 99 TM. */
    val sweref99tm: String?,
    val accuracyMeters: Int?,
    val fixAgeSeconds: Int?,
    val quality: PositionQuality,
    /** Väderstreck mot hem, t.ex. "sydväst". Null om hem eller position saknas. */
    val homeCompass: String?,
    val homeBearingDegrees: Int?,
    val homeDistanceMeters: Int?,
    val homeIsSet: Boolean,
    val batteryPercent: Int,
) {
    /** Radernas text i den ordning de visas. Samma text läses upp om användaren ber om det. */
    fun lines(): List<String> = buildList {
        add(NOT_112)
        if (hasPosition) {
            add("Position (WGS84): $degreesMinutes")
            add("Decimalgrader: $decimalDegrees")
            add("SWEREF 99 TM: $sweref99tm")
            add(positionAgeLine())
        } else {
            add("Position: okänd. Ingen position har tagits emot.")
        }
        add(
            when {
                !homeIsSet -> "Hem: inte sparat."
                homeDistanceMeters == null -> "Hem: sparat, men riktning och avstånd kräver en position."
                else -> "Hem: ${Geo.formatDistance(homeDistanceMeters.toDouble())} åt $homeCompass ($homeBearingDegrees°), fågelvägen."
            },
        )
        add("Batteri: $batteryPercent %.")
    }

    private fun positionAgeLine(): String {
        val accuracy = accuracyMeters?.let { "±$it m" } ?: "okänd noggrannhet"
        val age = fixAgeSeconds?.let { if (it < 60) "$it s gammal" else "${it / 60} min gammal" } ?: "okänd ålder"
        val warning = when (quality) {
            PositionQuality.GOOD -> ""
            PositionQuality.DEGRADED -> " Positionen är osäker."
            PositionQuality.LOST -> " Positionen är gammal och kan vara fel."
        }
        return "Noggrannhet: $accuracy, $age.$warning"
    }

    companion object {
        const val NOT_112 = "Ring 112 vid nödläge. Det här kortet ersätter inte 112."

        fun build(
            position: GeoPoint?,
            accuracyMeters: Double?,
            fixAgeSeconds: Double?,
            quality: PositionQuality,
            home: GeoPoint?,
            batteryPercent: Int,
        ): EmergencyCard = EmergencyCard(
            hasPosition = position != null,
            degreesMinutes = position?.let(Geo::formatDegreesMinutes),
            decimalDegrees = position?.let(Geo::formatDecimal),
            sweref99tm = position?.let(Sweref99::format),
            accuracyMeters = accuracyMeters?.roundToInt(),
            fixAgeSeconds = fixAgeSeconds?.roundToInt(),
            quality = quality,
            homeCompass = if (position != null && home != null) Geo.compassSv(Geo.bearingDegrees(position, home)) else null,
            homeBearingDegrees = if (position != null && home != null) Geo.bearingDegrees(position, home).roundToInt() % 360 else null,
            homeDistanceMeters = if (position != null && home != null) Geo.distanceMeters(position, home).roundToInt() else null,
            homeIsSet = home != null,
            batteryPercent = batteryPercent,
        )
    }
}
