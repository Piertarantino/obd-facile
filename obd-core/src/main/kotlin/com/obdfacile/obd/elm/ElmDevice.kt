package com.obdfacile.obd.elm

import com.obdfacile.obd.ElmTimeoutException
import com.obdfacile.obd.transport.ObdTransport
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Dialogo a basso livello con un adattatore ELM327 (o compatibile).
 *
 * Regole che rendono il dialogo affidabile anche con i cloni economici:
 *  - un solo comando alla volta (Mutex): l'ELM327 è half-duplex, un byte inviato mentre
 *    sta elaborando interrompe il comando in corso;
 *  - un comando, una volta inviato, non viene mai abbandonato a metà (NonCancellable):
 *    si aspetta sempre il prompt '>' prima di inviarne un altro;
 *  - prima di ogni comando si scartano i residui nel buffer di ricezione;
 *  - in caso di timeout si invia un CR per interrompere l'adattatore e riallinearsi.
 */
class ElmDevice(
    private val transport: ObdTransport,
    private val log: (String) -> Unit = {},
) {
    private val mutex = Mutex()

    val transportDescription: String get() = transport.description
    val isConnected: Boolean get() = transport.isConnected

    suspend fun open() = transport.connect()

    suspend fun close() = transport.disconnect()

    /**
     * Invia [cmd] e restituisce le righe di risposta (già ripulite, in maiuscolo).
     * Lancia [ElmTimeoutException] se il prompt non arriva entro [timeoutMs].
     */
    suspend fun command(cmd: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): List<String> =
        mutex.withLock {
            withContext(NonCancellable) {
                transport.clearInput()
                log("> $cmd")
                transport.write((cmd + "\r").toByteArray(Charsets.US_ASCII))
                val raw = readUntilPrompt(timeoutMs)
                if (raw == null) {
                    // Adattatore ancora occupato: un carattere qualsiasi interrompe la ricerca.
                    transport.write("\r".toByteArray(Charsets.US_ASCII))
                    readUntilPrompt(INTERRUPT_TIMEOUT_MS)
                    log("! timeout su $cmd")
                    throw ElmTimeoutException(cmd)
                }
                val lines = ElmText.splitLines(raw, echo = cmd)
                log("< " + lines.joinToString(" | "))
                lines
            }
        }

    private suspend fun readUntilPrompt(timeoutMs: Long): String? {
        val sb = StringBuilder()
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (true) {
            val remainingMs = (deadline - System.nanoTime()) / 1_000_000
            if (remainingMs <= 0) return null
            val chunk = transport.read(remainingMs) ?: continue
            for (b in chunk) {
                val c = (b.toInt() and 0xFF).toChar()
                if (c == '>') return sb.toString()
                if (c != '\u0000') sb.append(c) // alcuni cloni inviano byte nulli
            }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 2_500L
        const val INTERRUPT_TIMEOUT_MS = 800L
    }
}
