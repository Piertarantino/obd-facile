package com.obdfacile.app.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.obdfacile.obd.transport.StreamTransport
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Adattatori ELM327 Bluetooth "classico" (SPP / RFCOMM): la maggior parte di quelli economici.
 * L'adattatore va prima associato nelle impostazioni Bluetooth di Android (PIN 1234 o 0000).
 */
@SuppressLint("MissingPermission")
class BluetoothClassicTransport(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
) : StreamTransport() {

    private var socket: BluetoothSocket? = null

    override val description: String
        get() = "Bluetooth ${device.name ?: device.address}"

    override fun openStreams(): Pair<InputStream, OutputStream> {
        // Una ricerca dispositivi in corso rallenta o fa fallire la connessione RFCOMM.
        runCatching { adapter.cancelDiscovery() }

        // Tentativi in ordine: socket standard, socket non cifrato, canale 1 diretto
        // (alcuni cloni cinesi non pubblicano correttamente il servizio SPP).
        val attempts: List<() -> BluetoothSocket> = listOf(
            { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                m.invoke(device, 1) as BluetoothSocket
            },
        )
        var lastError: Exception? = null
        for (create in attempts) {
            var s: BluetoothSocket? = null
            try {
                s = create()
                s.connect()
                socket = s
                return s.inputStream to s.outputStream
            } catch (e: Exception) {
                lastError = e
                runCatching { s?.close() }
            }
        }
        throw IOException("Impossibile connettersi a ${device.name ?: device.address}", lastError)
    }

    override fun closeResources() {
        socket?.close()
        socket = null
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
