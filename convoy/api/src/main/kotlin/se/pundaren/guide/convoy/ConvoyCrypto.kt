package se.pundaren.guide.convoy

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Autentiserad kryptering med tillhörande data. Ingen egen kryptografi (byggplanen, 02 §6):
 * implementationen nedan anropar plattformens färdiga funktion och gör inget annat.
 */
interface Aead {
    val keyBytes: Int
    val nonceBytes: Int

    fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray

    /** Klartexten, eller null om nyckeln är fel eller något i chiffertexten, noncen eller [aad] har ändrats. */
    fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray?
}

/**
 * AES-256-GCM ur plattformens JCA (finns i JVM och i Android från API 10; appen kräver API 26).
 *
 * Avvikelse E6-D1: 07 pekar ut XChaCha20-Poly1305 i libsodium som kandidat. Det kräver ett nytt
 * inbyggt bibliotek i appen, och det valet är ägarens. GCM har en kortare nonce (96 bitar). Den dras
 * slumpmässigt per meddelande, vilket håller så länge en nyckel används till långt färre än 2³²
 * meddelanden; en session på högst 24 timmar sänder några tiotusental. Gränssnittet [Aead] gör att
 * primitiven kan bytas utan att protokollet ändras. Granskas i G7 av någon som inte har byggt den.
 *
 * Ägarbeslut 2026-11-21 kl. 14.19: "Kör på AES-256-GCM" (beslutsloggen, avsnitt 11). libsodium är parkerat.
 */
object AesGcmAead : Aead {
    override val keyBytes = 32
    override val nonceBytes = 12
    private const val TAG_BITS = 128

    override fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == keyBytes) { "nyckeln ska vara $keyBytes byte" }
        require(nonce.size == nonceBytes) { "noncen ska vara $nonceBytes byte" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    override fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray? {
        if (key.size != keyBytes || nonce.size != nonceBytes) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(aad)
            cipher.doFinal(ciphertext)
        } catch (_: GeneralSecurityException) {
            null
        }
    }
}

internal object ConvoyBytes {
    /**
     * Tillhörande data (07, 3.2): sessionId, senderId, seq, sentAtUtc och meddelandetypen.
     * Varje sträng har sin längd framför sig, så att två olika fältuppsättningar aldrig ger samma byte.
     */
    fun aad(sessionId: String, senderId: String, seq: Long, sentAtUtcMillis: Long, kind: EnvelopeKind): ByteArray {
        val s = sessionId.toByteArray(StandardCharsets.UTF_8)
        val p = senderId.toByteArray(StandardCharsets.UTF_8)
        return ByteBuffer.allocate(4 + s.size + 4 + p.size + 8 + 8 + 1)
            .putInt(s.size).put(s).putInt(p.size).put(p).putLong(seq).putLong(sentAtUtcMillis).put(kind.ordinal.toByte())
            .array()
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
}
