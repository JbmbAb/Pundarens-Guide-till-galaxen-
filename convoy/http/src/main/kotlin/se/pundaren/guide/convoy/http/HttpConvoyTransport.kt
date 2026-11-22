package se.pundaren.guide.convoy.http

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.pundaren.guide.convoy.ConvoySessionCredentials
import se.pundaren.guide.convoy.ConvoyTransport
import se.pundaren.guide.convoy.SealedEnvelope
import se.pundaren.guide.convoy.TransportState
import java.io.IOException

/**
 * Transporten mot reläet. Frågar reläet efter nya kuvert med några sekunders mellanrum i stället för
 * att hålla en förbindelse öppen: det tål att mobilnätet kommer och går, och två telefoner några
 * gånger om året behöver inget mer.
 *
 * Läget är ärligt (07 T-12): CONNECTED efter ett lyckat anrop, DEGRADED när anropen har börjat
 * misslyckas, OFFLINE när inget har lyckats på [offlineAfterMillis] eller när reläet har avslutat
 * sessionen. [send] kastar [IOException] när kuvertet inte kom fram.
 */
class HttpConvoyTransport(
    private val pollMillis: Long = 5_000,
    private val offlineAfterMillis: Long = 30_000,
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 10_000,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : ConvoyTransport {
    private class Link(val client: RelayClient, val credentials: ConvoySessionCredentials, val scope: CoroutineScope)

    @Volatile private var link: Link? = null
    @Volatile private var lastOkAt = 0L

    private val _state = MutableStateFlow(TransportState.OFFLINE)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<SealedEnvelope>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val incoming: Flow<SealedEnvelope> = _incoming

    private val _serverAheadMillis = MutableStateFlow<Long?>(null)

    /** Reläets klocka minus telefonens vid senaste svar, eller null innan något svar har kommit (07, 3.3). */
    val serverAheadMillis: StateFlow<Long?> = _serverAheadMillis.asStateFlow()

    override suspend fun open(session: ConvoySessionCredentials) {
        close()
        val opened = Link(
            RelayClient(session.serverUrl, connectTimeoutMillis, readTimeoutMillis), session, CoroutineScope(SupervisorJob() + io),
        )
        lastOkAt = 0L
        link = opened
        opened.scope.launch { poll(opened) }
    }

    private suspend fun poll(l: Link) {
        var since = 0L
        while (l.scope.isActive) {
            try {
                val batch = l.client.fetch(l.credentials, since)
                since = batch.cursor
                _serverAheadMillis.value = batch.serverNowMillis - clock()
                succeeded(l)
                batch.envelopes.forEach { _incoming.tryEmit(it.envelope) }
            } catch (e: RelayException) {
                if (e.status in SESSION_OVER) {
                    if (link === l) _state.value = TransportState.OFFLINE
                    return
                }
                failed(l)
            } catch (e: IOException) {
                failed(l)
            }
            delay(pollMillis)
        }
    }

    override suspend fun send(envelope: SealedEnvelope) {
        val l = link ?: throw IOException("Transporten är inte öppen.")
        try {
            withContext(io) { l.client.send(l.credentials, envelope) }
            succeeded(l)
        } catch (e: IOException) {
            if (e is RelayException && e.status in SESSION_OVER) {
                if (link === l) _state.value = TransportState.OFFLINE
            } else {
                failed(l)
            }
            throw e
        }
    }

    override suspend fun revokeSelf() {
        val l = link ?: return
        try {
            withContext(io) { l.client.revoke(l.credentials) }
        } finally {
            if (link === l) _state.value = TransportState.OFFLINE
        }
    }

    override suspend fun close() {
        link?.scope?.cancel()
        link = null
        _state.value = TransportState.OFFLINE
    }

    private fun succeeded(l: Link) {
        if (link !== l) return
        lastOkAt = clock()
        _state.value = TransportState.CONNECTED
    }

    private fun failed(l: Link) {
        if (link !== l) return
        val recently = lastOkAt != 0L && clock() - lastOkAt < offlineAfterMillis
        _state.value = if (recently) TransportState.DEGRADED else TransportState.OFFLINE
    }

    private companion object {
        /** Okänd eller spärrad token, okänd eller avslutad session: det finns inget mer att hämta. */
        val SESSION_OVER = setOf(401, 403, 404, 410)
    }
}
