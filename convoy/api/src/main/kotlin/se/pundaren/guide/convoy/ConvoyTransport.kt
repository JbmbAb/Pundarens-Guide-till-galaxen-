package se.pundaren.guide.convoy

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Transportens läge, som gränssnittet visar ärligt (07 T-12: utan nät kommer inga positioner fram). */
enum class TransportState { CONNECTED, DEGRADED, OFFLINE }

/** Vad ett kuvert bär. Typen står i klartext och ingår i det som kryptot skyddar mot ändring. */
enum class EnvelopeKind { POSITION, EMERGENCY, REVOKE }

/**
 * Ett förseglat meddelande (07, 3.2). Det enda transporten och servern någonsin ser: vem som sände,
 * löpnummer, sändtid och en krypterad nyttolast. Positionen går inte att läsa utan sessionsnyckeln.
 */
class SealedEnvelope(
    val sessionId: String,
    val senderId: String,
    /** Ökar strikt per avsändare (07 T-02). */
    val seq: Long,
    /** Avsändarens klocka när kuvertet förseglades, millisekunder sedan epoken. */
    val sentAtUtcMillis: Long,
    val kind: EnvelopeKind,
    nonce: ByteArray,
    ciphertext: ByteArray,
) {
    private val _nonce = nonce.copyOf()
    private val _ciphertext = ciphertext.copyOf()
    val nonce: ByteArray get() = _nonce.copyOf()
    val ciphertext: ByteArray get() = _ciphertext.copyOf()

    /** Kopia med ändrade fält. Används av testerna för att visa att varje ändring upptäcks. */
    fun altered(
        sessionId: String = this.sessionId, senderId: String = this.senderId, seq: Long = this.seq,
        sentAtUtcMillis: Long = this.sentAtUtcMillis, kind: EnvelopeKind = this.kind,
        nonce: ByteArray = this.nonce, ciphertext: ByteArray = this.ciphertext,
    ) = SealedEnvelope(sessionId, senderId, seq, sentAtUtcMillis, kind, nonce, ciphertext)
}

/** Det transporten behöver för att ansluta. Sessionsnyckeln ingår aldrig här: servern får inte se den. */
data class ConvoySessionCredentials(val serverUrl: String, val sessionId: String, val accessToken: String)

/**
 * Transporten (07, avsnitt 2). Kryptering, samtycke och tidsgräns ligger ovanför den, så att en annan
 * transport kan ersätta den utan att resten skrivs om. Ingen implementation finns än: `:convoy:wss`
 * och servern väntar på ägarbeslut F-15.
 */
interface ConvoyTransport {
    val state: StateFlow<TransportState>
    suspend fun open(session: ConvoySessionCredentials)

    /** Skickar ett redan förseglat kuvert. Transporten kan inte läsa det. */
    suspend fun send(envelope: SealedEnvelope)
    val incoming: Flow<SealedEnvelope>
    suspend fun revokeSelf()
    suspend fun close()
}
