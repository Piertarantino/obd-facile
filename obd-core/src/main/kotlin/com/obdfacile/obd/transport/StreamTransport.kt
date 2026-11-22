package com.obdfacile.obd.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Base per i trasporti a stream (socket RFCOMM Bluetooth, socket TCP Wi-Fi).
 *
 * Un coroutine dedicato legge continuamente dall'InputStream e mette i blocchi in un
 * canale: così le letture con timeout sono affidabili (InputStream.read() da solo
 * blocca senza timeout sui socket Bluetooth).
 */
abstract class StreamTransport : ObdTransport {
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private var readerScope: CoroutineScope? = null
    private var output: OutputStream? = null

    @Volatile
    private var connected = false
    override val isConnected: Boolean get() = connected

    /** Apre il canale fisico (bloccante, eseguito su Dispatchers.IO). */
    protected abstract fun openStreams(): Pair<InputStream, OutputStream>

    /** Chiude socket/risorse del canale fisico. */
    protected abstract fun closeResources()

    override suspend fun connect(): Unit = withContext(Dispatchers.IO) {
        clearInput()
        val (input, out) = openStreams()
        output = out
        connected = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        readerScope = scope
        scope.launch {
            val buf = ByteArray(1024)
            try {
                while (isActive) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) incoming.trySend(buf.copyOf(n))
                }
            } catch (_: IOException) {
                // socket chiuso o connessione persa
            } finally {
                connected = false
            }
        }
    }

    override suspend fun write(data: ByteArray): Unit = withContext(Dispatchers.IO) {
        val out = output ?: throw IOException("Adattatore non connesso")
        try {
            out.write(data)
            out.flush()
        } catch (e: IOException) {
            connected = false
            throw e
        }
    }

    override suspend fun read(timeoutMs: Long): ByteArray? {
        incoming.tryReceive().getOrNull()?.let { return it }
        if (!connected) throw IOException("Connessione con l'adattatore persa")
        return withTimeoutOrNull(timeoutMs) { incoming.receive() }
    }

    override fun clearInput() {
        while (incoming.tryReceive().isSuccess) { /* scarta */ }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        connected = false
        runCatching { closeResources() }
        readerScope?.cancel()
        readerScope = null
        output = null
    }
}
