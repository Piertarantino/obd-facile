package com.obdfacile.app.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.obdfacile.obd.transport.ObdTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID

/**
 * Adattatori Bluetooth Low Energy (Vgate iCar Pro BLE, OBDLink CX, Veepeak BLE, cloni "V-LINK"...).
 * Necessari su iPhone e comodi su Android perché non richiedono l'associazione.
 *
 * Non esiste un servizio standard: si provano le coppie note (FFF0, FFE0, 18F0) e in
 * mancanza si sceglie qualunque servizio con una caratteristica "notify" e una "write".
 */
@SuppressLint("MissingPermission")
class BleTransport(
    private val context: Context,
    private val device: BluetoothDevice,
) : ObdTransport {

    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var ready: CompletableDeferred<Unit>? = null
    private var writeAck: CompletableDeferred<Unit>? = null
    private var maxChunk = 20

    @Volatile
    private var connected = false
    override val isConnected: Boolean get() = connected

    override val description: String get() = "BLE ${device.name ?: device.address}"

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // MTU più grande = risposte lunghe (VIN, DTC) in meno pacchetti
                if (!g.requestMtu(247)) g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false
                ready?.completeExceptionally(IOException("Dispositivo BLE disconnesso"))
                writeAck?.completeExceptionally(IOException("Dispositivo BLE disconnesso"))
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) maxChunk = mtu - 3
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val pair = findCharacteristics(g)
            if (pair == null) {
                ready?.completeExceptionally(IOException("Servizio OBD non trovato su questo dispositivo BLE"))
                return
            }
            val (notify, write) = pair
            writeChar = write
            g.setCharacteristicNotification(notify, true)
            val cccd = notify.getDescriptor(CCCD_UUID)
            if (cccd == null) {
                connected = true
                ready?.complete(Unit)
                return
            }
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, value)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            connected = true
            ready?.complete(Unit)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            incoming.trySend(value.copyOf())
        }

        @Deprecated("Android < 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            c.value?.let { incoming.trySend(it.copyOf()) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writeAck?.complete(Unit)
        }
    }

    override suspend fun connect() {
        val d = CompletableDeferred<Unit>()
        ready = d
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        try {
            withTimeout(15_000) { d.await() }
        } catch (e: Exception) {
            disconnect()
            throw IOException("Connessione BLE non riuscita", e)
        }
    }

    override suspend fun write(data: ByteArray) {
        val g = gatt ?: throw IOException("Adattatore non connesso")
        val ch = writeChar ?: throw IOException("Adattatore non connesso")
        val type = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        var offset = 0
        while (offset < data.size) {
            val chunk = data.copyOfRange(offset, minOf(data.size, offset + maxChunk))
            val ack = CompletableDeferred<Unit>()
            writeAck = ack
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, chunk, type) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.value = chunk
                ch.writeType = type
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
            if (!ok) throw IOException("Scrittura BLE fallita")
            withTimeoutOrNull(2_000) { ack.await() }
            offset += chunk.size
        }
    }

    override suspend fun read(timeoutMs: Long): ByteArray? {
        incoming.tryReceive().getOrNull()?.let { return it }
        if (!connected) throw IOException("Connessione BLE persa")
        return withTimeoutOrNull(timeoutMs) { incoming.receive() }
    }

    override fun clearInput() {
        while (incoming.tryReceive().isSuccess) { /* scarta */ }
    }

    override suspend fun disconnect() {
        connected = false
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        writeChar = null
    }

    private fun findCharacteristics(g: BluetoothGatt): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        for ((service, notifyUuid, writeUuid) in KNOWN) {
            val s = g.getService(service) ?: continue
            val n = s.getCharacteristic(notifyUuid) ?: continue
            val w = s.getCharacteristic(writeUuid) ?: continue
            return n to w
        }
        for (s in g.services) {
            val n = s.characteristics.firstOrNull {
                it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            } ?: continue
            val w = s.characteristics.firstOrNull {
                it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            } ?: continue
            return n to w
        }
        return null
    }

    private companion object {
        fun uuid16(short: String): UUID = UUID.fromString("0000$short-0000-1000-8000-00805F9B34FB")
        val CCCD_UUID: UUID = uuid16("2902")
        val KNOWN = listOf(
            Triple(uuid16("FFF0"), uuid16("FFF1"), uuid16("FFF2")),
            Triple(uuid16("FFE0"), uuid16("FFE1"), uuid16("FFE1")),
            Triple(uuid16("18F0"), uuid16("2AF0"), uuid16("2AF1")),
        )
    }
}
