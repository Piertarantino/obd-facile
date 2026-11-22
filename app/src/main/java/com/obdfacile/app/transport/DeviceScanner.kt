package com.obdfacile.app.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.obdfacile.obd.transport.ObdTransport
import com.obdfacile.obd.transport.WifiTransport
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.net.SocketFactory

enum class DeviceKind { BLUETOOTH, BLE, WIFI }

data class DeviceItem(val kind: DeviceKind, val name: String, val address: String) {
    /** Euristica sul nome per mettere in cima gli adattatori OBD. */
    val looksLikeObd: Boolean
        get() = OBD_HINTS.any { name.contains(it, ignoreCase = true) }

    private companion object {
        val OBD_HINTS = listOf("OBD", "ELM", "VGATE", "V-LINK", "VLINK", "ICAR", "KONNWEI", "VIECAR", "VEEPEAK", "CAN", "SCAN")
    }
}

@SuppressLint("MissingPermission")
class DeviceScanner(private val context: Context, private val adapter: BluetoothAdapter?) {

    val bluetoothAvailable: Boolean get() = adapter != null
    val bluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    fun bondedDevices(): List<DeviceItem> = runCatching {
        adapter?.bondedDevices.orEmpty()
            .map { DeviceItem(DeviceKind.BLUETOOTH, it.name ?: it.address, it.address) }
            .sortedByDescending { it.looksLikeObd }
    }.getOrDefault(emptyList())

    /** Scansione BLE per [durationMs]; emette ogni dispositivo con un nome. */
    fun scanBle(durationMs: Long): Flow<DeviceItem> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            close()
            return@callbackFlow
        }
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name ?: return
                trySend(DeviceItem(DeviceKind.BLE, name, result.device.address))
            }

            override fun onScanFailed(errorCode: Int) {
                close()
            }
        }
        scanner.startScan(cb)
        launch {
            delay(durationMs)
            close()
        }
        awaitClose { runCatching { scanner.stopScan(cb) } }
    }

    fun createTransport(item: DeviceItem): ObdTransport = when (item.kind) {
        DeviceKind.BLUETOOTH -> BluetoothClassicTransport(requireNotNull(adapter), adapter.getRemoteDevice(item.address))
        DeviceKind.BLE -> BleTransport(context, requireNotNull(adapter).getRemoteDevice(item.address))
        DeviceKind.WIFI -> {
            val host = item.address.substringBefore(':')
            val port = item.address.substringAfter(':', "").toIntOrNull() ?: WifiTransport.DEFAULT_PORT
            WifiTransport(host, port, wifiSocketFactory())
        }
    }

    /**
     * La rete Wi-Fi dell'adattatore non ha internet: senza legare il socket a quella
     * rete Android userebbe i dati mobili e la connessione fallirebbe.
     */
    @Suppress("DEPRECATION")
    private fun wifiSocketFactory(): SocketFactory {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return SocketFactory.getDefault()
        val wifi = cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        return wifi?.socketFactory ?: SocketFactory.getDefault()
    }
}
