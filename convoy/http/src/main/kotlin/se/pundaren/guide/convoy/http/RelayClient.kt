package se.pundaren.guide.convoy.http

import se.pundaren.guide.convoy.ConvoySessionCredentials
import se.pundaren.guide.convoy.EnvelopeKind
import se.pundaren.guide.convoy.SealedEnvelope
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/** Reläet svarade med en vägran. [status] är HTTP-koden. */
class RelayException(val status: Int) : IOException("Reläet svarade $status.")

/**
 * Anropen mot reläet, ett i taget och blockerande. Anropas från en bakgrundstråd. Sessionsnyckeln
 * finns aldrig här: reläet får bara hashen av inbjudans hemlighet och redan förseglade kuvert.
 */
class RelayClient(
    serverUrl: String,
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 10_000,
) {
    private val base = serverUrl.trimEnd('/') + "/v1/sessions/"

    class Joined(val accessToken: String, val expiresAtUtcMillis: Long)
    class Received(val envelope: SealedEnvelope, val receivedAtServerMillis: Long)
    class Batch(val serverNowMillis: Long, val cursor: Long, val envelopes: List<Received>)

    /** Den som skapar sessionen anmäler den och får sin åtkomsttoken. */
    fun register(sessionId: String, expiresAtUtcMillis: Long, inviteExpiresAtUtcMillis: Long, inviteSecret: ByteArray, participantId: String): String {
        val hash = hex(MessageDigest.getInstance("SHA-256").digest(inviteSecret))
        return call("POST", sessionId, "$expiresAtUtcMillis $inviteExpiresAtUtcMillis $hash $participantId", null)
    }

    /** Den inbjudna löser in inbjudan. Reläet godtar det en enda gång. */
    fun join(sessionId: String, inviteSecret: ByteArray, participantId: String): Joined {
        val fields = call("POST", "$sessionId/join", "${hex(inviteSecret)} $participantId", null).split(' ')
        if (fields.size != 2) throw IOException("Oväntat svar från reläet.")
        return Joined(fields[0], fields[1].toLongOrNull() ?: throw IOException("Oväntat svar från reläet."))
    }

    fun send(credentials: ConvoySessionCredentials, envelope: SealedEnvelope) {
        val body = "${envelope.seq} ${envelope.sentAtUtcMillis} ${envelope.kind.name} ${b64(envelope.nonce)} ${b64(envelope.ciphertext)}"
        call("POST", "${credentials.sessionId}/envelopes", body, credentials.accessToken)
    }

    /** Hämtar den andras kuvert som kommit efter [since]. Rader som inte går att läsa hoppas över. */
    fun fetch(credentials: ConvoySessionCredentials, since: Long): Batch {
        val lines = call("GET", "${credentials.sessionId}/envelopes?since=$since", null, credentials.accessToken).split('\n')
        val head = lines.first().split(' ')
        val now = head.getOrNull(0)?.toLongOrNull()
        val cursor = head.getOrNull(1)?.toLongOrNull()
        if (head.size != 2 || now == null || cursor == null) throw IOException("Oväntat svar från reläet.")
        val envelopes = lines.drop(1).mapNotNull { line ->
            val f = line.split(' ')
            if (f.size != 7) return@mapNotNull null
            runCatching {
                val envelope = SealedEnvelope(
                    credentials.sessionId, f[1], f[2].toLong(), f[3].toLong(), EnvelopeKind.valueOf(f[4]),
                    Base64.getUrlDecoder().decode(f[5]), Base64.getUrlDecoder().decode(f[6]),
                )
                Received(envelope, f[0].toLong())
            }.getOrNull()
        }
        return Batch(now, cursor, envelopes)
    }

    /** Slutar dela: reläet spärrar token och tar bort deltagarens positioner. */
    fun revoke(credentials: ConvoySessionCredentials) {
        call("DELETE", "${credentials.sessionId}/token", null, credentials.accessToken)
    }

    private fun call(method: String, path: String, body: String?, token: String?): String {
        val connection = URI(base + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw RelayException(status)
            val bytes = connection.inputStream.use { it.readNBytes(MAX_RESPONSE_BYTES) }
            return String(bytes, StandardCharsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 64 * 1024
        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
        fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
