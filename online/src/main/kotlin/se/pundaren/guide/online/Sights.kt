package se.pundaren.guide.online

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.net.URLEncoder

/*
 * Sevärt längs vägen: det man annars aldrig hade hittat. Grottor, vattenfall, ruiner, utsiktstorn,
 * bunkrar, övergivna miljöer och märklig natur ur OpenStreetMap (Overpass), med text och bild från
 * Wikipedia där platsen har en artikel.
 */

/** Vad slags fynd det är. Ordningen är den som visas, och den avgör också hur ovanligt fyndet räknas. */
enum class SightKind(val title: String, val one: String, val weight: Int) {
    CAVE("Grottor", "Grotta", 10),
    WATERFALL("Vattenfall", "Vattenfall", 9),
    MILITARY("Försvar och bunkrar", "Försvarsanläggning", 9),
    ABANDONED("Övergivet", "Övergiven plats", 8),
    RUIN("Ruiner och lämningar", "Ruin eller lämning", 7),
    MINE("Gruvor", "Gruva", 7),
    NATURE("Märklig natur", "Naturformation", 6),
    VIEW("Utsikt och torn", "Utsikt", 5),
    ATTRACTION("Övriga sevärdheter", "Sevärdhet", 3),
}

data class Sight(
    /** OSM-id, t.ex. "node/123456". */
    val id: String,
    val name: String,
    val kind: SightKind,
    val lat: Double,
    val lon: Double,
    /** OSM-taggen `wikipedia`, t.ex. "sv:Styggforsen", eller null. */
    val wikipedia: String?,
    /** Kort beskrivning ur OSM om en finns. */
    val description: String?,
    /** Höjd över havet ur OSM om den är angiven. */
    val elevation: String?,
) {
    /** Hur ovanligt och välbeskrivet fyndet är. Högre är bättre. */
    val interest: Int get() = kind.weight * 2 + (if (wikipedia != null) 4 else 0) + (if (description != null) 2 else 0) + (if (unnamed) -5 else 0)
    val unnamed: Boolean get() = name.startsWith(UNNAMED_PREFIX)

    companion object {
        const val UNNAMED_PREFIX = "Namnlös "
    }
}

data class WikiSummary(val title: String, val text: String, val imageUrl: String?)

/** Sökning i OpenStreetMap efter ovanliga platser nära en linje eller en punkt. */
class Overpass(private val http: HttpPort, private val endpoint: String = ENDPOINT) {
    /**
     * @param line en punkt (sök runt den) eller flera (sök längs linjen), som latitud och longitud.
     * @param radiusMeters hur långt från linjen det får ligga.
     */
    fun sights(line: List<Pair<Double, Double>>, radiusMeters: Int, kinds: Set<SightKind>): List<Sight> {
        if (line.isEmpty() || kinds.isEmpty()) return emptyList()
        val body = "data=" + URLEncoder.encode(query(line, radiusMeters, kinds), Charsets.UTF_8)
        val response = http.post(endpoint, mapOf("Content-Type" to "application/x-www-form-urlencoded"), body)
        return parse(response).filter { it.kind in kinds }
    }

    internal companion object {
        const val ENDPOINT = "https://overpass-api.de/api/interpreter"
        private val json = Json { ignoreUnknownKeys = true }

        /** Vilka OSM-taggar som hör till vilket slags fynd. Namn krävs där taggen annars ger för mycket skräp. */
        private val filters: Map<SightKind, List<String>> = mapOf(
            SightKind.CAVE to listOf("""["natural"="cave_entrance"]"""),
            SightKind.WATERFALL to listOf("""["waterway"="waterfall"]"""),
            SightKind.MILITARY to listOf(
                """["military"~"^(bunker|trench|barracks)$"]""", """["historic"~"^(fort|fortification|battlefield|bunker)$"]""",
                """["abandoned:military"]""", """["disused:military"]""",
            ),
            SightKind.ABANDONED to listOf(
                """["abandoned"="yes"]["name"]""", """["abandoned:building"]["name"]""", """["abandoned:man_made"]["name"]""",
                """["abandoned:railway"="station"]""", """["historic"~"^(industrial|railway_station|farm|charcoal_pile|lime_kiln)$"]""",
            ),
            SightKind.RUIN to listOf(
                """["historic"~"^(ruins|castle|archaeological_site|church|wayside_shrine|rune_stone|stone|boundary_stone|tomb)$"]""",
                """["ruins"="yes"]["name"]""",
            ),
            SightKind.MINE to listOf("""["historic"~"^(mine|mine_shaft|adit)$"]""", """["man_made"~"^(adit|mineshaft)$"]"""),
            SightKind.NATURE to listOf(
                """["natural"~"^(gorge|arch|sinkhole|rock|stone|cliff|spring|hot_spring)$"]["name"]""", """["geological"]["name"]""",
                """["natural"="valley"]["name"]""",
            ),
            SightKind.VIEW to listOf("""["tourism"="viewpoint"]""", """["man_made"="tower"]["tower:type"="observation"]"""),
            SightKind.ATTRACTION to listOf("""["tourism"="attraction"]["name"]"""),
        )

        fun query(line: List<Pair<Double, Double>>, radiusMeters: Int, kinds: Set<SightKind>): String {
            val around = "(around:$radiusMeters," + line.joinToString(",") { (lat, lon) -> "%.5f,%.5f".format(java.util.Locale.ROOT, lat, lon) } + ")"
            val parts = SightKind.entries.filter { it in kinds }.flatMap { filters.getValue(it) }.joinToString("") { "nwr$it$around;" }
            return "[out:json][timeout:25];($parts);out center tags 400;"
        }

        fun parse(body: String): List<Sight> {
            val root = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrElse { throw OnlineException(0, "Svaret gick inte att läsa.") }
            val seen = HashSet<String>()
            return (root["elements"] as? JsonArray).orEmpty().mapNotNull { element ->
                val e = element as? JsonObject ?: return@mapNotNull null
                val tags = e["tags"] as? JsonObject ?: return@mapNotNull null
                fun tag(key: String) = (tags[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                val center = e["center"] as? JsonObject
                val lat = ((e["lat"] ?: center?.get("lat")) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                val lon = ((e["lon"] ?: center?.get("lon")) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                val kind = kindOf(::tag) ?: return@mapNotNull null
                val name = tag("name") ?: tag("name:sv") ?: (Sight.UNNAMED_PREFIX + kind.one.lowercase())
                // Samma plats finns ofta både som punkt och som yta. En räcker.
                if (!seen.add("$name|${"%.3f,%.3f".format(java.util.Locale.ROOT, lat, lon)}")) return@mapNotNull null
                Sight(
                    id = "${(e["type"] as? JsonPrimitive)?.contentOrNull}/${(e["id"] as? JsonPrimitive)?.contentOrNull}",
                    name = name, kind = kind, lat = lat, lon = lon,
                    wikipedia = tag("wikipedia"), description = tag("description:sv") ?: tag("description") ?: tag("inscription"),
                    elevation = tag("ele"),
                )
            }
        }

        /** Det mest ovanliga slaget vinner när en plats passar på flera. */
        private fun kindOf(tag: (String) -> String?): SightKind? {
            val historic = tag("historic")
            val natural = tag("natural")
            return when {
                natural == "cave_entrance" -> SightKind.CAVE
                tag("waterway") == "waterfall" -> SightKind.WATERFALL
                tag("military") != null || tag("abandoned:military") != null || tag("disused:military") != null ||
                    historic in setOf("fort", "fortification", "battlefield", "bunker") -> SightKind.MILITARY
                historic in setOf("mine", "mine_shaft", "adit") || tag("man_made") in setOf("adit", "mineshaft") -> SightKind.MINE
                tag("abandoned") == "yes" || tag("abandoned:building") != null || tag("abandoned:man_made") != null || tag("abandoned:railway") != null ||
                    historic in setOf("industrial", "railway_station", "farm", "charcoal_pile", "lime_kiln") -> SightKind.ABANDONED
                historic != null || tag("ruins") == "yes" -> SightKind.RUIN
                natural != null || tag("geological") != null -> SightKind.NATURE
                tag("tourism") == "viewpoint" || tag("tower:type") == "observation" -> SightKind.VIEW
                tag("tourism") == "attraction" -> SightKind.ATTRACTION
                else -> null
            }
        }
    }
}

/** Text och bild om en plats, ur den Wikipedia-artikel som OpenStreetMap pekar på. */
class Wikipedia(private val http: HttpPort) {
    /** @param tag OSM-taggen `wikipedia`, t.ex. "sv:Styggforsen". Null om artikeln inte går att hämta. */
    fun summary(tag: String): WikiSummary? {
        val language = tag.substringBefore(':', "sv").takeIf { it.matches(Regex("[a-z]{2,3}")) } ?: "sv"
        val title = tag.substringAfter(':').trim().replace(' ', '_')
        if (title.isEmpty()) return null
        val url = "https://$language.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(title, Charsets.UTF_8).replace("+", "%20")
        val root = runCatching { json.parseToJsonElement(http.get(url, mapOf("Accept" to "application/json"))) as? JsonObject }.getOrNull() ?: return null
        val text = (root["extract"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val image = ((root["thumbnail"] as? JsonObject)?.get("source") as? JsonPrimitive)?.contentOrNull
        return WikiSummary((root["title"] as? JsonPrimitive)?.contentOrNull ?: title.replace('_', ' '), text, image)
    }

    /**
     * Platser med egen artikel inom [radiusMeters] (högst 10 km) från en punkt. Orter, socknar, sjöar och
     * annat som inte är ett utflyktsmål sållas bort, och resten får sitt slag efter vad artikeln säger.
     */
    fun nearby(lat: Double, lon: Double, radiusMeters: Int = 10_000): List<Sight> {
        val url = "https://sv.wikipedia.org/w/api.php?action=query&generator=geosearch" +
            "&ggscoord=" + URLEncoder.encode("$lat|$lon", Charsets.UTF_8) + "&ggsradius=${radiusMeters.coerceIn(100, 10_000)}&ggslimit=200" +
            "&prop=" + URLEncoder.encode("coordinates|description", Charsets.UTF_8) + "&colimit=max&format=json&formatversion=2"
        val root = runCatching { json.parseToJsonElement(http.get(url)) as JsonObject }.getOrElse {
            if (it is OnlineException) throw it else throw OnlineException(0, "Svaret gick inte att läsa.")
        }
        val pages = ((root["query"] as? JsonObject)?.get("pages") as? JsonArray).orEmpty()
        return pages.mapNotNull { element ->
            val page = element as? JsonObject ?: return@mapNotNull null
            val title = (page["title"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val description = (page["description"] as? JsonPrimitive)?.contentOrNull
            val at = (page["coordinates"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@mapNotNull null
            val pLat = (at["lat"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val pLon = (at["lon"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val kind = kindOf(title, description) ?: return@mapNotNull null
            Sight("wikipedia/${(page["pageid"] as? JsonPrimitive)?.contentOrNull}", title.substringBefore(" (").trim(), kind, pLat, pLon, "sv:$title", description, null)
        }
    }

    internal companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** Det som inte är ett utflyktsmål: förvaltning, bebyggelse, vatten utan särdrag och människor. */
        private val skip = Regex(
            "kommun|socken|församling|distrikt|tingslag|härad|kontrakt|pastorat|landskap|\\blän\\b|tätort|småort|stadsdel|tätortsdel|" +
                "\\bby i\\b|\\bort i\\b|samhälle|bebyggelse|sjön?\\b|tjärn|\\bsvensk |\\bsvenskt |född|företag|skola|idrott|fotboll|hockey|" +
                "tidning|station\\b|flygplats|väg\\b|\\bälv\\b|vattendrag|\\bå i\\b|\\bö i\\b",
        )

        private val words: List<Pair<SightKind, Regex>> = listOf(
            SightKind.CAVE to Regex("grott"),
            SightKind.WATERFALL to Regex("vattenfall|forsen\\b|fallet\\b|\\bfors\\b"),
            SightKind.MILITARY to Regex("skans|fästning|befästning|bunker|försvar|\\bfort\\b|batteri|krigs|militär|flygbas|skyttevärn"),
            SightKind.ABANDONED to Regex("övergiv|ödeby|nedlagd|spökstad|före detta|ödegård|ödekyrk"),
            SightKind.MINE to Regex("gruv|hytta\\b|kalkbrott|stenbrott|masugn|bruksruin|smedja"),
            SightKind.RUIN to Regex("ruin|fornborg|\\bborg\\b|gravfält|fornlämning|runsten|slott|kloster|offerplats|hällristning|gravröse|fångstgrop|domarring"),
            SightKind.NATURE to Regex("ravin|kanjon|klint|raukar|jättegryt|flyttblock|naturreservat|nationalpark|\\bkälla\\b|krater|klippa|urskog|\\bstup|naturminne|geolog|meteorit|rullstensås"),
            SightKind.VIEW to Regex("utsikt|brandtorn|\\bklack"),
            SightKind.ATTRACTION to Regex("museum|hembygdsgård|fäbod|kyrk|kvarn|\\bbro\\b|\\bfyr\\b|minnesmärke|monument|gammelgård|kapell|sevärdhet|park\\b|bryggeri|\\bgård\\b"),
        )

        /** Platsens slag efter rubrik och kort beskrivning. Null betyder att platsen inte är ett utflyktsmål. */
        fun kindOf(title: String, description: String?): SightKind? {
            val text = (title + " " + description.orEmpty()).lowercase()
            val found = words.firstOrNull { it.second.containsMatchIn(text) }?.first
            // Ett tydligt fynd går före sållningen: "Storstupets naturreservat" är ett fynd var det än ligger.
            if (found != null && found != SightKind.ATTRACTION) return found
            // Sållningen ser på rubriken och på vad platsen är, inte på var den ligger ("kyrkobyggnad i Orsa kommun").
            val what = (title + " " + description.orEmpty().substringBefore(" i ")).lowercase()
            if (skip.containsMatchIn(what)) return null
            return found
        }
    }
}

/** Punkter med jämna mellanrum längs en linje, så att en sökning med fast radie täcker hela vägen. */
object RouteSamples {
    /** @param line latitud och longitud. Svarar med högst [max] punkter, glesare om vägen är lång. */
    fun along(line: List<Pair<Double, Double>>, stepMeters: Double = 12_000.0, max: Int = 10): List<Pair<Double, Double>> {
        if (line.size < 2) return line
        val total = line.zipWithNext { a, b -> meters(a, b) }.sum()
        val step = maxOf(stepMeters, total / (max - 1).coerceAtLeast(1))
        val out = mutableListOf(line.first())
        var since = 0.0
        line.zipWithNext { a, b ->
            since += meters(a, b)
            if (since >= step) { out += b; since = 0.0 }
        }
        if (out.last() != line.last()) out += line.last()
        return out.take(max)
    }

    fun meters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.first - a.first)
        val dLon = Math.toRadians(b.second - a.second)
        val h = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(a.first)) * Math.cos(Math.toRadians(b.first)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(h))
    }
}
