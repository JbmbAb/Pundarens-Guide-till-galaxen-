package se.pundaren.guide.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Nycklarna till de frivilliga nättjänsterna. Ägaren skaffar dem själv och skriver in dem i Reglage. */
enum class Secret(val title: String, val source: String) {
    CLAUDE("Claude", "Claude Console"),
    RESROBOT("ResRobot v2.1", "Trafiklab"),
    REALTIME("Trafiklab Realtime", "Trafiklab"),
}

/**
 * Sparar nycklarna krypterade i appens privata lagring. Krypteringsnyckeln ligger i Androids
 * nyckellager och lämnar aldrig enheten, så en kopia av filen går inte att läsa någon annanstans.
 *
 * En nyckel visas aldrig igen efter att den har sparats och skrivs aldrig i loggen. Utåt syns bara
 * vilka som finns.
 */
class SecretStore(context: Context, private val log: (String) -> Unit) {
    private val prefs = context.applicationContext.getSharedPreferences("natnycklar", Context.MODE_PRIVATE)

    private val _stored = MutableStateFlow(Secret.entries.filter { prefs.contains(it.name) }.toSet())
    /** Vilka nycklar som är sparade. */
    val stored: StateFlow<Set<Secret>> = _stored.asStateFlow()

    /** Sparar nyckeln. En tom text tar bort den. Svarar falskt om enheten inte kunde kryptera. */
    fun put(secret: Secret, value: String): Boolean {
        val text = value.trim()
        if (text.isEmpty()) {
            clear(secret)
            return true
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val sealed = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(secret.name, b64(cipher.iv) + ":" + b64(sealed)).apply()
            _stored.value = _stored.value + secret
            log("NYCKEL ${secret.name} sparad")
            true
        } catch (e: Exception) {
            log("NYCKEL ${secret.name} kunde inte sparas (${e.javaClass.simpleName})")
            false
        }
    }

    fun clear(secret: Secret) {
        prefs.edit().remove(secret.name).apply()
        _stored.value = _stored.value - secret
        log("NYCKEL ${secret.name} borttagen")
    }

    /** Nyckeln i klartext, bara för själva anropet. Null om den saknas eller inte går att läsa. */
    fun get(secret: Secret): String? {
        val stored = prefs.getString(secret.name, null) ?: return null
        return try {
            val (iv, sealed) = stored.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        } catch (e: Exception) {
            log("NYCKEL ${secret.name} gick inte att läsa (${e.javaClass.simpleName})")
            null
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "natnycklar-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
