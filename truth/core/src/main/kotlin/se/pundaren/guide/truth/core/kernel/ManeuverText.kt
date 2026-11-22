package se.pundaren.guide.truth.core.kernel

import se.pundaren.guide.truth.core.geo.Geo

/**
 * Sanningsremsans korta form av nästa manöver: "Vänster om 300 m" (byggplanen, ändring 1: beskedet
 * först, rakt och i fast form). En ren tabell från ruttmotorns manövertyp och modifierare
 * (OSRM-värden i gemener) till svensk text och pilform. Ingenting här kommer från tramslagret.
 */
object ManeuverText {
    enum class Arrow {
        STRAIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, U_TURN, ROUNDABOUT, ARRIVE, DEPART,
    }

    fun arrow(type: String?, modifier: String?): Arrow = when (type) {
        "arrive" -> Arrow.ARRIVE
        "depart" -> Arrow.DEPART
        "roundabout", "rotary", "roundabout turn" -> Arrow.ROUNDABOUT
        else -> when (modifier) {
            "left" -> Arrow.LEFT
            "right" -> Arrow.RIGHT
            "slight left" -> Arrow.SLIGHT_LEFT
            "slight right" -> Arrow.SLIGHT_RIGHT
            "sharp left" -> Arrow.SHARP_LEFT
            "sharp right" -> Arrow.SHARP_RIGHT
            "u turn", "uturn" -> Arrow.U_TURN
            else -> Arrow.STRAIGHT
        }
    }

    /** Manövern i ett par ord, utan avstånd. */
    fun headline(type: String?, modifier: String?): String {
        val side = when (modifier) {
            "left", "slight left", "sharp left" -> "vänster"
            "right", "slight right", "sharp right" -> "höger"
            else -> null
        }
        return when (type) {
            "arrive" -> "Framme"
            "depart" -> "Starta"
            "roundabout", "rotary", "roundabout turn" -> "Rondell"
            "exit roundabout", "exit rotary" -> "Ut ur rondellen"
            "fork" -> if (side != null) "Håll $side" else "Fortsätt"
            "on ramp" -> if (side != null) "Påfart $side" else "Påfart"
            "off ramp" -> if (side != null) "Avfart $side" else "Avfart"
            "merge" -> if (side != null) "Väv in åt $side" else "Väv in"
            "end of road" -> if (side != null) "Vägen tar slut, $side" else "Vägen tar slut"
            else -> when (modifier) {
                "left" -> "Vänster"
                "right" -> "Höger"
                "slight left" -> "Svagt vänster"
                "slight right" -> "Svagt höger"
                "sharp left" -> "Skarpt vänster"
                "sharp right" -> "Skarpt höger"
                "u turn", "uturn" -> "U-sväng"
                "straight" -> "Rakt fram"
                else -> "Fortsätt"
            }
        }
    }

    /** "Vänster om 300 m", eller bara "Vänster" när manövern är här. */
    fun line(type: String?, modifier: String?, distanceMeters: Double?): String {
        val head = headline(type, modifier)
        return if (distanceMeters == null || distanceMeters < 15.0) head else "$head om ${Geo.formatDistance(distanceMeters)}"
    }
}
