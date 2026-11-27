package se.pundaren.guide.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.pundaren.guide.online.ClaudeChooser
import se.pundaren.guide.online.Departure
import se.pundaren.guide.online.HttpPort
import se.pundaren.guide.online.JdkHttps
import se.pundaren.guide.online.OnlineException
import se.pundaren.guide.online.ResRobot
import se.pundaren.guide.online.TrafiklabRealtime
import se.pundaren.guide.online.TransitPlace
import se.pundaren.guide.online.TransitStop
import se.pundaren.guide.online.TransitTrip
import se.pundaren.guide.truth.api.GeoPoint

/** Ett svar från en nättjänst, eller en mening om varför det inte kom något. */
sealed interface Fetched<out T> {
    data class Ok<T>(val value: T) : Fetched<T>
    data class Failed(val message: String) : Fetched<Nothing>
}

data class TripSearch(val from: String, val to: String, val trips: List<TransitTrip>)
data class DepartureBoard(val stop: TransitStop, val departures: List<Departure>)

/**
 * De frivilliga nättjänsterna: Trafiklab och Claude. Allt här körs på en bakgrundstråd och bara när
 * användaren har bett om det. Saknas nyckel eller nät kommer ett rakt besked tillbaka, och resten av
 * appen fungerar som förut.
 *
 * Loggen får veta att ett anrop gjordes och hur det gick, aldrig nyckeln, texten eller hållplatsen.
 */
class OnlineServices(
    private val secrets: SecretStore,
    private val log: (String) -> Unit,
    private val http: HttpPort = JdkHttps(connectTimeoutMillis = 6_000, readTimeoutMillis = 15_000),
) {
    /**
     * Låter Claude välja ett beslut. Null betyder att Guiden väljer själv: nyckeln saknas, nätet svarar
     * inte eller svaret gick inte att använda.
     */
    suspend fun chooseDecree(input: String, options: List<ClaudeChooser.Option>, recent: List<String>): String? = withContext(Dispatchers.IO) {
        val key = secrets.get(Secret.CLAUDE) ?: return@withContext null
        try {
            ClaudeChooser(key, http).choose(input, options, recent).also { log("CLAUDE val: ${it ?: "inget giltigt"}") }
        } catch (e: OnlineException) {
            log("CLAUDE fel ${e.status}")
            null
        }
    }

    /** Resor från en hållplats eller den egna positionen till en hållplats. */
    suspend fun trips(fromText: String, here: GeoPoint?, toText: String): Fetched<TripSearch> = transit(Secret.RESROBOT) { key ->
        val robot = ResRobot(key, http)
        val to = robot.findStops(toText).firstOrNull() ?: return@transit Fetched.Failed("Hittar ingen hållplats som heter \"${toText.trim()}\".")
        val (origin, fromName) = if (here != null) {
            TransitPlace.Point(here.lat, here.lon) to "Din position"
        } else {
            val from = robot.findStops(fromText).firstOrNull() ?: return@transit Fetched.Failed("Hittar ingen hållplats som heter \"${fromText.trim()}\".")
            TransitPlace.Stop(from.id) to from.name
        }
        val trips = robot.trips(origin, TransitPlace.Stop(to.id))
        log("KOLLEKTIVTRAFIK resa: ${trips.size} förslag")
        Fetched.Ok(TripSearch(fromName, to.name, trips))
    }

    /** Avgångar med prognos från en namngiven hållplats, eller från den som ligger närmast. */
    suspend fun departures(fromText: String, here: GeoPoint?): Fetched<DepartureBoard> = transit(Secret.RESROBOT) { key ->
        val realtimeKey = secrets.get(Secret.REALTIME) ?: return@transit Fetched.Failed(missing(Secret.REALTIME))
        val robot = ResRobot(key, http)
        val stop = (if (here != null) robot.nearbyStops(here.lat, here.lon) else robot.findStops(fromText)).firstOrNull()
            ?: return@transit Fetched.Failed(if (here != null) "Ingen hållplats inom två kilometer." else "Hittar ingen hållplats som heter \"${fromText.trim()}\".")
        val departures = TrafiklabRealtime(realtimeKey, http).departures(stop.id)
        log("KOLLEKTIVTRAFIK avgångar: ${departures.size} rader")
        Fetched.Ok(DepartureBoard(stop, departures))
    }

    private suspend fun <T> transit(secret: Secret, call: (String) -> Fetched<T>): Fetched<T> = withContext(Dispatchers.IO) {
        val key = secrets.get(secret) ?: return@withContext Fetched.Failed(missing(secret))
        try {
            call(key)
        } catch (e: OnlineException) {
            log("KOLLEKTIVTRAFIK fel ${e.status}")
            Fetched.Failed(
                when (e.status) {
                    0 -> "Ingen kontakt med Trafiklab. Kontrollera nätet och försök igen."
                    401, 403 -> "Trafiklab godtog inte nyckeln. Kontrollera den under Reglage."
                    429 -> "Nyckelns kvot hos Trafiklab är slut för den här perioden."
                    else -> "Trafiklab svarade ${e.status}."
                },
            )
        }
    }

    private fun missing(secret: Secret) = "Nyckeln för ${secret.title} saknas. Lägg in den under Reglage, Nättjänster."
}
