package se.pundaren.guide.online

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * Trafiklab: ResRobot v2.1 (resa från A till B, hållplatssök, närmaste hållplats) och Trafiklab
 * Realtime (avgångar med verkliga förseningar). Hela Sverige, Dalatrafik inräknad.
 *
 * Uppgifterna är Trafiklabs och visas som de kommer: ingen satir blandas in, och ingenting här rör
 * appens egen rutt. Tolkningen är tålig: fält som saknas ger en rad mindre, inte ett fel.
 */

/** En hållplats. [id] är rikshållplatsens nummer, samma i ResRobot och i Trafiklab Realtime. */
data class TransitStop(val id: String, val name: String, val lat: Double?, val lon: Double?, val distanceMeters: Int? = null)

/** En del av en resa: en promenad eller en tur med ett fordon. */
data class TransitLeg(
    val walk: Boolean,
    /** Linjens namn, till exempel "Länstrafik - Buss 132". Tomt för en promenad. */
    val line: String,
    val direction: String?,
    val from: String,
    val to: String,
    val departs: LocalDateTime,
    val arrives: LocalDateTime,
    val distanceMeters: Int?,
)

data class TransitTrip(val legs: List<TransitLeg>) {
    val departs: LocalDateTime get() = legs.first().departs
    val arrives: LocalDateTime get() = legs.last().arrives
    /** Antalet byten mellan fordon. Promenader räknas inte. */
    val changes: Int get() = (legs.count { !it.walk } - 1).coerceAtLeast(0)
}

/** En avgång från en hållplats. [realtime] är null när trafikbolaget inte lämnar någon prognos. */
data class Departure(
    val scheduled: LocalDateTime,
    val realtime: LocalDateTime?,
    val delaySeconds: Int,
    val canceled: Boolean,
    val line: String,
    val destination: String,
    val mode: String?,
    val platform: String?,
    val hasRealtime: Boolean,
)

/** Var resan börjar eller slutar: en hållplats eller en punkt på kartan. */
sealed interface TransitPlace {
    data class Stop(val id: String) : TransitPlace
    data class Point(val lat: Double, val lon: Double) : TransitPlace
}

class ResRobot(private val key: String, private val http: HttpPort) {
    /** Hållplatser vars namn liknar [query]. */
    fun findStops(query: String, max: Int = 8): List<TransitStop> {
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        // Frågetecknet sist ger Trafiklabs oskarpa sökning, som tål stavfel och ofullständiga namn.
        return stops(http.get("$BASE/location.name?input=${enc("$text?")}&maxNo=$max&format=json&accessId=${enc(key)}"))
    }

    /** Hållplatserna närmast en punkt, närmast först. Punkten skickas till Trafiklab. */
    fun nearbyStops(lat: Double, lon: Double, max: Int = 6, radiusMeters: Int = 2_000): List<TransitStop> =
        stops(http.get("$BASE/location.nearbystops?originCoordLat=$lat&originCoordLong=$lon&maxNo=$max&r=$radiusMeters&format=json&accessId=${enc(key)}"))

    /** Nästa resor från [from] till [to], tidigast först. */
    fun trips(from: TransitPlace, to: TransitPlace, max: Int = 5): List<TransitTrip> {
        val origin = when (from) {
            is TransitPlace.Stop -> "originId=${enc(from.id)}"
            is TransitPlace.Point -> "originCoordLat=${from.lat}&originCoordLong=${from.lon}"
        }
        val destination = when (to) {
            is TransitPlace.Stop -> "destId=${enc(to.id)}"
            is TransitPlace.Point -> "destCoordLat=${to.lat}&destCoordLong=${to.lon}"
        }
        val root = parse(http.get("$BASE/trip?format=json&$origin&$destination&numF=$max&passlist=0&accessId=${enc(key)}"))
        return list(root["Trip"]).mapNotNull { trip ->
            val legs = list(obj(obj(trip)?.get("LegList"))?.get("Leg")).mapNotNull(::leg)
            if (legs.isEmpty()) null else TransitTrip(legs)
        }
    }

    private fun leg(element: JsonElement): TransitLeg? {
        val leg = obj(element) ?: return null
        val origin = obj(leg["Origin"]) ?: return null
        val destination = obj(leg["Destination"]) ?: return null
        val type = text(leg["type"]).orEmpty()
        val walk = type == "WALK" || type == "TRSF"
        return TransitLeg(
            walk = walk,
            line = if (walk) "" else text(leg["name"]) ?: text(obj(leg["Product"])?.get("name")).orEmpty(),
            direction = text(leg["direction"]),
            from = text(origin["name"]) ?: return null,
            to = text(destination["name"]) ?: return null,
            departs = moment(origin) ?: return null,
            arrives = moment(destination) ?: return null,
            distanceMeters = (leg["dist"] as? JsonPrimitive)?.intOrNull,
        )
    }

    private fun moment(place: JsonObject): LocalDateTime? = runCatching {
        LocalDateTime.of(LocalDate.parse(text(place["date"])), LocalTime.parse(text(place["time"])))
    }.getOrNull()

    private fun stops(body: String): List<TransitStop> {
        val root = parse(body)
        // Dokumentationen visar två former: en lista direkt, eller en lista av höljen med hållplatsen i.
        val wrapped = list(root["stopLocationOrCoordLocation"]).mapNotNull { obj(obj(it)?.get("StopLocation")) }
        val direct = list(root["StopLocation"]).mapNotNull(::obj)
        return (wrapped + direct).mapNotNull { s ->
            TransitStop(
                id = text(s["extId"]) ?: text(s["id"]) ?: return@mapNotNull null,
                name = text(s["name"]) ?: return@mapNotNull null,
                lat = number(s["lat"]), lon = number(s["lon"]),
                distanceMeters = (s["dist"] as? JsonPrimitive)?.intOrNull,
            )
        }
    }

    private companion object {
        const val BASE = "https://api.resrobot.se/v2.1"
    }
}

class TrafiklabRealtime(private val key: String, private val http: HttpPort) {
    /** Kommande avgångar från en hållplats, med prognos där trafikbolaget lämnar en. */
    fun departures(stopId: String, max: Int = 12): List<Departure> {
        val root = parse(http.get("$BASE/departures/${enc(stopId)}?key=${enc(key)}"))
        return list(root["departures"]).mapNotNull { element ->
            val d = obj(element) ?: return@mapNotNull null
            val route = obj(d["route"])
            val scheduled = time(d["scheduled"]) ?: return@mapNotNull null
            Departure(
                scheduled = scheduled,
                realtime = time(d["realtime"]),
                delaySeconds = (d["delay"] as? JsonPrimitive)?.intOrNull ?: 0,
                canceled = (d["canceled"] as? JsonPrimitive)?.booleanOrNull ?: false,
                line = listOfNotNull(text(route?.get("designation")), text(route?.get("name"))).firstOrNull { it.isNotBlank() }.orEmpty(),
                destination = text(route?.get("direction"))?.takeIf { it.isNotBlank() } ?: text(obj(route?.get("destination"))?.get("name")).orEmpty(),
                mode = text(route?.get("transport_mode")),
                platform = platform(d["realtime_platform"]) ?: platform(d["scheduled_platform"]),
                hasRealtime = (d["is_realtime"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
        }.take(max)
    }

    /** Hållplatser vars namn liknar [query], ur Realtime-tjänstens eget register. Kräver inte ResRobot. */
    fun findStops(query: String, max: Int = 8): List<TransitStop> {
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        val root = parse(http.get("$BASE/stops/name/${enc(text)}?key=${enc(key)}"))
        return list(root["stop_groups"]).mapNotNull { element ->
            val group = obj(element) ?: return@mapNotNull null
            val first = list(group["stops"]).firstOrNull()?.let(::obj)
            TransitStop(
                id = text(group["id"]) ?: return@mapNotNull null,
                name = text(group["name"]) ?: return@mapNotNull null,
                lat = number(first?.get("lat")), lon = number(first?.get("lon")),
            )
        }.take(max)
    }

    private fun platform(element: JsonElement?): String? = text(obj(element)?.get("designation"))?.takeIf { it.isNotBlank() }

    private fun time(element: JsonElement?): LocalDateTime? = runCatching { LocalDateTime.parse(text(element)) }.getOrNull()

    private companion object {
        const val BASE = "https://realtime-api.trafiklab.se/v1"
    }
}

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun parse(body: String): JsonObject =
    runCatching { json.parseToJsonElement(body) as JsonObject }.getOrElse { throw OnlineException(0, "Svaret gick inte att läsa.") }

private fun obj(element: JsonElement?): JsonObject? = element as? JsonObject

/** En lista, även när tjänsten lämnar ett ensamt objekt i stället för en lista med ett. */
private fun list(element: JsonElement?): List<JsonElement> = when (element) {
    is JsonArray -> element
    is JsonObject -> listOf(element)
    else -> emptyList()
}

private fun text(element: JsonElement?): String? = (element as? JsonPrimitive)?.contentOrNull

/** Ett tal, oavsett om tjänsten skriver det som tal eller som text. */
private fun number(element: JsonElement?): Double? = (element as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
