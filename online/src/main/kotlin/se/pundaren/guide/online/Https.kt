package se.pundaren.guide.online

import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * En nättjänst svarade inte som väntat. [status] är HTTP-koden, eller 0 när ingen kontakt fanns.
 *
 * Meddelandet innehåller aldrig adressen: Trafiklabs nycklar ligger i den, och ett felmeddelande kan
 * hamna i loggen eller på skärmen.
 */
class OnlineException(val status: Int, message: String) : IOException(message)

/** Porten mot nätet. Finns för att tjänsterna ska gå att prova utan nät. */
interface HttpPort {
    fun get(url: String, headers: Map<String, String> = emptyMap()): String
    fun post(url: String, headers: Map<String, String>, body: String): String
}

/** Blockerande HTTPS-anrop ur JDK:n, ett i taget. Anropas från en bakgrundstråd. Bara https godtas. */
class JdkHttps(
    private val connectTimeoutMillis: Int = 6_000,
    private val readTimeoutMillis: Int = 20_000,
) : HttpPort {
    override fun get(url: String, headers: Map<String, String>): String = call("GET", url, headers, null)
    override fun post(url: String, headers: Map<String, String>, body: String): String = call("POST", url, headers, body)

    private fun call(method: String, url: String, headers: Map<String, String>, body: String?): String {
        val uri = URI(url)
        if (uri.scheme != "https") throw OnlineException(0, "Bara https godtas.")
        val host = uri.host.orEmpty()
        val connection = try {
            uri.toURL().openConnection() as HttpsURLConnection
        } catch (e: IOException) {
            throw OnlineException(0, "Ingen kontakt med $host.")
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw OnlineException(status, "$host svarade $status.")
            val bytes = connection.inputStream.use { it.readNBytes(MAX_RESPONSE_BYTES) }
            return String(bytes, StandardCharsets.UTF_8)
        } catch (e: OnlineException) {
            throw e
        } catch (e: IOException) {
            // Undantagets egen text kan innehålla adressen med nyckeln och förs därför inte vidare.
            throw OnlineException(0, "Ingen kontakt med $host.")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}
