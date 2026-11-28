package se.pundaren.guide.online

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Base64

/** Hur en av Lantmäteriets tjänster nås. Iakttaget 2026-11-28 genom att fråga varje tjänst utan inloggning. */
enum class LmAccess {
    /** Visningstjänst på maps.lantmateriet.se. Godtar bara systemkontot (Basic). */
    VIEW,

    /** Tjänst på api.lantmateriet.se. Säger sig godta systemkontot (Basic) eller API-portalens åtkomstbevis (Bearer). */
    API,
}

/** En tjänst ur ägarens behörighetslista i Geotorget, med en adress som går att fråga för att se om den svarar. */
data class LmService(val name: String, val access: LmAccess, val testUrl: String)

/** Utfallet av en kontroll. [status] är HTTP-koden, 0 utan kontakt och [NO_LOGIN] när ingen inloggning fanns att pröva. */
data class LmResult(val service: LmService, val status: Int, val via: String?) {
    /** Sant när tjänsten godtog inloggningen, även om kontrollfrågan i sig inte var en giltig fråga. */
    val accepted: Boolean get() = status in 200..299 || status in ACCEPTED_BUT_NOT_A_QUERY

    companion object {
        const val NO_LOGIN = -1

        /** Svar som bara kan komma efter godkänd inloggning: frågan nådde tjänsten men var inte en giltig fråga. */
        val ACCEPTED_BUT_NOT_A_QUERY = setOf(400, 404, 405, 406, 415, 422)
    }
}

object Lantmateriet {
    const val TOKEN_URL = "https://apimanager.lantmateriet.se/oauth2/token"
    const val VIA_ACCOUNT = "systemkontot"
    const val VIA_KEY = "API-nyckeln"

    private const val API = "https://api.lantmateriet.se"
    private const val MAPS = "https://maps.lantmateriet.se"
    private const val HERE = "limit=1&bbox=14.60,61.11,14.62,61.12"

    /**
     * Visnings- och Direkt-tjänsterna i behörighetslistan, i listans ordning. Nedladdningstjänsterna är
     * inte med: de uppgifterna kommer senare från ägarens egen PostGIS (ägarens besked 2026-11-28).
     */
    val services: List<LmService> = listOf(
        LmService("Byggnad Direkt", LmAccess.API, "$API/distribution/produkter/byggnad/v3/"),
        LmService("Fastighetsindelning Direkt", LmAccess.API, "$API/ogc-features/v1/fastighetsindelning/collections/registerenhetsomradesytor/items?$HERE"),
        LmService("Hydrografi Direkt", LmAccess.API, "$API/ogc-features/v1/hydrografi/collections/StandingWater/items?$HERE"),
        LmService("Kommun, Län och Rike Direkt", LmAccess.API, "$API/ogc-features/v1/administrativ-indelning/collections/kommuner/items?$HERE"),
        LmService("Markhöjd Direkt", LmAccess.API, "$API/distribution/produkter/markhojd/v1/hojd"),
        LmService("Marktäcke Direkt", LmAccess.API, "$API/ogc-features/v1/marktacke/collections/markytor/items?$HERE"),
        LmService("Ortofoto historiska Visning", LmAccess.VIEW, "$MAPS/historiska-ortofoton/wms/v1?request=GetCapabilities&version=1.1.1&service=WMS"),
        LmService("Referens Uppslag Adress", LmAccess.API, "$API/distribution/produkter/uppslag/adress/v3/fritext?adress=Storgatan"),
        LmService("Registerbeteckning Direkt", LmAccess.API, "$API/distribution/produkter/registerbeteckning/v5/referens/fritext?beteckning=Orsa"),
        LmService("Samfällighetsförening Direkt", LmAccess.API, "$API/distribution/produkter/samfallighetsforening/v2.2/"),
        LmService("Stompunkt Visning", LmAccess.VIEW, "$MAPS/stompunkt/wms/v1/?request=GetCapabilities&version=1.1.1&service=WMS"),
        LmService("Topografisk webbkarta Visning, översiktlig", LmAccess.VIEW, "$MAPS/open/topowebb-ccby/v1/wmts?request=GetCapabilities&version=1.0.0&service=wmts"),
        LmService("Tyngdkraft Visning", LmAccess.VIEW, "$MAPS/tyngdkraft/wms/v1?request=GetCapabilities&version=1.1.1&service=WMS"),
    )

    fun basic(user: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
}

/**
 * Frågar varje tjänst en gång och säger om den svarar med de inloggningar som finns. Ingenting sparas
 * och inga data används: det enda som kommer ut är en statuskod per tjänst.
 */
class LantmaterietCheck(private val http: HttpPort) {
    /** Byter API-portalens nyckelpar mot ett åtkomstbevis. Null om portalen inte godtar paret. */
    fun token(consumerKey: String, consumerSecret: String): String? {
        val headers = mapOf(
            "Authorization" to Lantmateriet.basic(consumerKey, consumerSecret),
            "Content-Type" to "application/x-www-form-urlencoded",
        )
        val body = try { http.post(Lantmateriet.TOKEN_URL, headers, "grant_type=client_credentials") } catch (e: OnlineException) { return null }
        val token = runCatching { ((Json.parseToJsonElement(body) as JsonObject)["access_token"] as? JsonPrimitive)?.contentOrNull }.getOrNull()
        return token?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
    }

    /**
     * @param account systemkontot som färdigt Basic-huvud, eller null om det inte är inlagt.
     * @param bearer åtkomstbeviset från [token], eller null.
     */
    fun check(service: LmService, account: String?, bearer: String?): LmResult {
        val attempts: List<Pair<String, String>> = when (service.access) {
            LmAccess.VIEW -> listOfNotNull(account?.let { it to Lantmateriet.VIA_ACCOUNT })
            LmAccess.API -> listOfNotNull(account?.let { it to Lantmateriet.VIA_ACCOUNT }, bearer?.let { it to Lantmateriet.VIA_KEY })
        }
        if (attempts.isEmpty()) return LmResult(service, LmResult.NO_LOGIN, null)
        var last = LmResult(service, 0, null)
        for ((authorization, via) in attempts) {
            val status = try {
                http.get(service.testUrl, mapOf("Authorization" to authorization))
                200
            } catch (e: OnlineException) {
                e.status
            }
            last = LmResult(service, status, via)
            if (last.accepted) return last
        }
        return last
    }
}
