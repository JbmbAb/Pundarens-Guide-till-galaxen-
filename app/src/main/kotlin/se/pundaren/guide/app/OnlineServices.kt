package se.pundaren.guide.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.pundaren.guide.online.ClaudeChooser
import se.pundaren.guide.online.Departure
import se.pundaren.guide.online.HttpPort
import se.pundaren.guide.online.JdkHttps
import se.pundaren.guide.online.Lantmateriet
import se.pundaren.guide.online.LantmaterietCheck
import se.pundaren.guide.online.LmResult
import se.pundaren.guide.online.OnlineException
import se.pundaren.guide.online.Overpass
import se.pundaren.guide.online.Sight
import se.pundaren.guide.online.SightKind
import se.pundaren.guide.online.WikiSummary
import se.pundaren.guide.online.Wikipedia
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

    /** Kort tålamod: för tjänster som är trevliga att ha men inte får hålla upp svaret. */
    private val quick: HttpPort = JdkHttps(connectTimeoutMillis = 4_000, readTimeoutMillis = 9_000)

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

    /**
     * Sevärt: ovanliga platser nära punkterna i [samples]. Wikipedia är huvudkällan, och OpenStreetMaps
     * sökserver läggs till när den svarar (den är ofta överbelastad, och då klarar sig sökningen utan den).
     */
    suspend fun sights(samples: List<Pair<Double, Double>>, kinds: Set<SightKind>): Fetched<List<Sight>> = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext Fetched.Ok(emptyList())
        val wiki = Wikipedia(http)
        // En fråga i taget med en liten paus: Wikipedia bromsar den som frågar för fort (svar 429).
        // En nekad fråga prövas en gång till efter en stund, och sökningen går vidare även om den nekas igen.
        val fromWikipedia = mutableListOf<Sight>()
        var answered = 0
        var lastError = 0
        for ((index, sample) in samples.withIndex()) {
            if (index > 0) Thread.sleep(250)
            for (attempt in 1..2) {
                try {
                    fromWikipedia += wiki.nearby(sample.first, sample.second)
                    answered++
                    break
                } catch (e: OnlineException) {
                    lastError = e.status
                    if (e.status == 429 && attempt == 1) Thread.sleep(2_000) else break
                }
            }
        }
        if (answered == 0) {
            log("SEVÄRT Wikipedia fel $lastError")
            return@withContext Fetched.Failed(
                when (lastError) {
                    0 -> "Ingen kontakt med nätet. Sevärt behöver nät för att leta."
                    429 -> "Wikipedia vill ha en paus. Vänta en minut och sök igen."
                    else -> "Wikipedia svarade $lastError."
                },
            )
        }
        if (answered < samples.size) log("SEVÄRT Wikipedia svarade på $answered av ${samples.size} frågor")
        val fromOsm = try { Overpass(quick).sights(samples, 10_000, kinds) } catch (e: OnlineException) { log("SEVÄRT OpenStreetMap svarade inte (${e.status})"); emptyList() }
        // OpenStreetMaps fynd först: de bär mer detaljer. Samma plats från Wikipedia stryks om den ligger intill med samma namn.
        val all = (fromOsm + fromWikipedia).filter { it.kind in kinds }
        val kept = mutableListOf<Sight>()
        for (sight in all) {
            val twin = kept.indexOfFirst { it.name.equals(sight.name, ignoreCase = true) && Math.abs(it.lat - sight.lat) < 0.02 && Math.abs(it.lon - sight.lon) < 0.04 }
            if (twin < 0) kept += sight
            else if (kept[twin].wikipedia == null && sight.wikipedia != null) kept[twin] = kept[twin].copy(wikipedia = sight.wikipedia, description = kept[twin].description ?: sight.description)
        }
        log("SEVÄRT ${fromWikipedia.size} ur Wikipedia, ${fromOsm.size} ur OpenStreetMap, ${kept.size} kvar")
        Fetched.Ok(kept)
    }

    /** Artikelns inledning och bild. Null om den inte går att hämta. */
    suspend fun wikiSummary(tag: String): WikiSummary? = withContext(Dispatchers.IO) { runCatching { Wikipedia(http).summary(tag) }.getOrNull() }

    /** En bild som råa byte. Null om den inte går att hämta. */
    suspend fun image(url: String): ByteArray? = withContext(Dispatchers.IO) { runCatching { http.bytes(url) }.getOrNull() }

    /**
     * Frågar alla Lantmäteriets tjänster i ägarens behörighetslista en gång var, med de inloggningar som
     * är inlagda, och svarar med en statuskod per tjänst. Inga data hämtas eller sparas.
     */
    suspend fun checkLantmateriet(): List<LmResult> = withContext(Dispatchers.IO) {
        val user = secrets.get(Secret.LM_USER)
        val password = secrets.get(Secret.LM_PASSWORD)
        val account = if (user != null && password != null) Lantmateriet.basic(user, password) else null
        val check = LantmaterietCheck(http)
        val key = secrets.get(Secret.LM_KEY)
        val secret = secrets.get(Secret.LM_SECRET)
        val bearer = if (key != null && secret != null) check.token(key, secret) else null
        if (key != null && secret != null) log("LANTMÄTERIET åtkomstbevis: ${if (bearer != null) "utfärdat" else "nekat"}")
        Lantmateriet.services.map { check.check(it, account, bearer) }
            .also { results -> log("LANTMÄTERIET kontroll: ${results.count { it.accepted }} av ${results.size} tjänster svarar") }
    }

    private fun missing(secret: Secret) = "Nyckeln för ${secret.title} saknas. Lägg in den under Reglage, Nättjänster."
}
