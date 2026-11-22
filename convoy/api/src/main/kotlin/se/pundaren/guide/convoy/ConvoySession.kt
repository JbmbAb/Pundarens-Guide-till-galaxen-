package se.pundaren.guide.convoy

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

/** Kandidatvärden ur 07 (markerade * där). Inte frysta; de prövas i G7. */
object ConvoyLimits {
    val MAX_SESSION: Duration = Duration.ofHours(24)
    val INVITE_VALID: Duration = Duration.ofMinutes(10)
    const val FRESH_SECONDS = 30L
    const val RECENT_SECONDS = 120L
    const val LAST_SEEN_SECONDS = 900L
    const val STATUS = "KANDIDAT_OFRYST"
}

/**
 * En expedition i Convoy (07, 3.1): en delad hemlig nyckel, ett sessionsnamn och en sluttid.
 * Nyckeln lämnar aldrig telefonerna: den visas i QR-koden och skickas aldrig till servern.
 */
class ConvoySession(
    val sessionId: String,
    sessionKey: ByteArray,
    val expiresAtUtc: Instant,
    val serverUrl: String,
) {
    private var key: ByteArray? = sessionKey.copyOf()

    /** Nyckeln, eller null när sessionen har avslutats och nyckeln raderats (07, 3.4). */
    internal fun keyOrNull(): ByteArray? = synchronized(this) { key }

    val hasKey: Boolean get() = synchronized(this) { key != null }

    /** Raderar nyckeln ur minnet. Därefter kan sessionen varken sända eller läsa något. */
    fun wipeKey() = synchronized(this) {
        key?.fill(0)
        key = null
    }

    fun isExpired(nowUtc: Instant): Boolean = !nowUtc.isBefore(expiresAtUtc)

    companion object {
        /**
         * Skapar en ny session på den här telefonen: 128 bitar slump till namnet och 256 bitar till
         * nyckeln. Sluttiden är obligatorisk och får ligga högst 24 timmar bort (07 T-09).
         */
        fun create(nowUtc: Instant, expiresAtUtc: Instant, serverUrl: String, random: SecureRandom = SecureRandom()): ConvoySession {
            require(expiresAtUtc.isAfter(nowUtc)) { "Sluttiden måste ligga framåt i tiden." }
            require(!expiresAtUtc.isAfter(nowUtc.plus(ConvoyLimits.MAX_SESSION))) { "Sluttiden får ligga högst 24 timmar bort." }
            val id = ByteArray(16).also(random::nextBytes)
            val key = ByteArray(AesGcmAead.keyBytes).also(random::nextBytes)
            return ConvoySession(ConvoyBytes.hex(id), key, expiresAtUtc, serverUrl).also { key.fill(0) }
        }

        fun newParticipantId(random: SecureRandom = SecureRandom()): String = ConvoyBytes.hex(ByteArray(8).also(random::nextBytes))
    }
}

/**
 * Inbjudan som QR-kodens innehåll (07, 3.1 punkt 3). Den bär sessionsnyckeln, så den som ser koden
 * kan läsa positionerna. Därför gäller den kort tid, kan bara lösas in en gång hos servern, och de
 * två deltagarna jämför en verifieringskod muntligt efteråt (07 T-01).
 */
class ConvoyInvite(
    val serverUrl: String,
    val sessionId: String,
    sessionKey: ByteArray,
    inviteSecret: ByteArray,
    val expiresAtUtc: Instant,
    val issuedAtUtc: Instant,
) {
    private val key = sessionKey.copyOf()
    private val secret = inviteSecret.copyOf()
    val inviteSecret: ByteArray get() = secret.copyOf()

    /** Giltig i tio minuter från utfärdandet och aldrig efter sessionens sluttid. Servern avgör engångsbruket. */
    fun isValid(nowUtc: Instant): Boolean =
        !nowUtc.isBefore(issuedAtUtc) && nowUtc.isBefore(issuedAtUtc.plus(ConvoyLimits.INVITE_VALID)) && nowUtc.isBefore(expiresAtUtc)

    /**
     * Deltagare B går med. B väljer själv sin sluttid och kan välja en tidigare än A men aldrig en
     * senare (07, 3.1 punkt 4). Null om inbjudan inte längre gäller eller sluttiden är otillåten.
     */
    fun join(nowUtc: Instant, ownExpiresAtUtc: Instant = expiresAtUtc): ConvoySession? {
        if (!isValid(nowUtc)) return null
        if (ownExpiresAtUtc.isAfter(expiresAtUtc) || !ownExpiresAtUtc.isAfter(nowUtc)) return null
        return ConvoySession(sessionId, key, ownExpiresAtUtc, serverUrl)
    }

    /** QR-kodens text. */
    fun encode(): String {
        val url = serverUrl.toByteArray(StandardCharsets.UTF_8)
        val id = sessionId.toByteArray(StandardCharsets.UTF_8)
        val body = ByteBuffer.allocate(2 + url.size + 2 + id.size + 1 + key.size + 1 + secret.size + 8 + 8)
            .putShort(url.size.toShort()).put(url).putShort(id.size.toShort()).put(id)
            .put(key.size.toByte()).put(key).put(secret.size.toByte()).put(secret)
            .putLong(expiresAtUtc.toEpochMilli()).putLong(issuedAtUtc.toEpochMilli())
            .array()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(body)
    }

    companion object {
        const val PREFIX = "pundaren-convoy:1:"

        fun issue(session: ConvoySession, nowUtc: Instant, random: SecureRandom = SecureRandom()): ConvoyInvite {
            val key = checkNotNull(session.keyOrNull()) { "Sessionen är avslutad." }
            return ConvoyInvite(session.serverUrl, session.sessionId, key, ByteArray(16).also(random::nextBytes), session.expiresAtUtc, nowUtc)
        }

        /** Läser QR-kodens text. Null om den inte är en inbjudan i det här formatet. */
        fun decode(text: String): ConvoyInvite? {
            if (!text.startsWith(PREFIX)) return null
            return runCatching {
                val b = ByteBuffer.wrap(Base64.getUrlDecoder().decode(text.removePrefix(PREFIX)))
                fun bytes(n: Int): ByteArray { require(n in 0..b.remaining()); return ByteArray(n).also { b.get(it) } }
                val url = String(bytes(b.short.toInt()), StandardCharsets.UTF_8)
                val id = String(bytes(b.short.toInt()), StandardCharsets.UTF_8)
                val key = bytes(b.get().toInt())
                val secret = bytes(b.get().toInt())
                require(key.size == AesGcmAead.keyBytes && secret.size == 16 && b.remaining() == 16)
                ConvoyInvite(url, id, key, secret, Instant.ofEpochMilli(b.long), Instant.ofEpochMilli(b.long))
            }.getOrNull()
        }
    }
}

/** Samtycket (04, `convoy_consent`): varje deltagare godkänner själv sin delning på sin egen telefon. */
data class ConvoyConsent(
    val sessionId: String,
    val participantId: String,
    val grantedAtUtc: Instant,
    val expiresAtUtc: Instant,
    val revokedAtUtc: Instant? = null,
) {
    fun isActive(nowUtc: Instant): Boolean = revokedAtUtc == null && !nowUtc.isBefore(grantedAtUtc) && nowUtc.isBefore(expiresAtUtc)
}

/**
 * Verifieringskoden (07, 3.1 punkt 6): sex siffror härledda ur sessionsnyckeln och de två
 * deltagarnas id. Båda telefonerna visar den och deltagarna jämför den muntligt. Någon som löst in
 * en fotograferad inbjudan får ett annat id och därmed en annan kod.
 */
object ConvoyVerification {
    fun code(session: ConvoySession, participantA: String, participantB: String): String? {
        val key = session.keyOrNull() ?: return null
        val (first, second) = listOf(participantA, participantB).sorted()
        val mac = ConvoyBytes.hmacSha256(key, "pundaren-convoy-verifiering|${session.sessionId}|$first|$second".toByteArray(StandardCharsets.UTF_8))
        val number = (ByteBuffer.wrap(mac, 0, 4).int and Int.MAX_VALUE) % 1_000_000
        return "%03d %03d".format(java.util.Locale.ROOT, number / 1000, number % 1000)
    }
}
