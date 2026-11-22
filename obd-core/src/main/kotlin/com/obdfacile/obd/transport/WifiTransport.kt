package com.obdfacile.obd.transport

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Adattatori ELM327 Wi-Fi (di solito 192.168.0.10:35000).
 *
 * Su Android il [socketFactory] va preso dalla rete Wi-Fi dell'adattatore
 * (Network.getSocketFactory()): quella rete non ha internet e Android altrimenti
 * instrada il traffico sui dati mobili, e la connessione fallisce.
 */
class WifiTransport(
    private val host: String = DEFAULT_HOST,
    private val port: Int = DEFAULT_PORT,
    private val socketFactory: SocketFactory = SocketFactory.getDefault(),
    private val connectTimeoutMs: Int = 5_000,
) : StreamTransport() {

    private var socket: Socket? = null

    override val description: String get() = "Wi-Fi $host:$port"

    override fun openStreams(): Pair<InputStream, OutputStream> {
        val s = socketFactory.createSocket()
        s.tcpNoDelay = true // comandi piccoli: niente attesa di Nagle
        s.connect(InetSocketAddress(host, port), connectTimeoutMs)
        socket = s
        return s.getInputStream() to s.getOutputStream()
    }

    override fun closeResources() {
        socket?.close()
        socket = null
    }

    companion object {
        const val DEFAULT_HOST = "192.168.0.10"
        const val DEFAULT_PORT = 35000
    }
}
