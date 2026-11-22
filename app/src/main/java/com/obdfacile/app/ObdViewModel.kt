package com.obdfacile.app

import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obdfacile.app.transport.DeviceItem
import com.obdfacile.app.transport.DeviceKind
import com.obdfacile.app.transport.DeviceScanner
import com.obdfacile.obd.AdapterInfo
import com.obdfacile.obd.ClearResult
import com.obdfacile.obd.ElmTimeoutException
import com.obdfacile.obd.NegativeResponseException
import com.obdfacile.obd.NoVehicleResponseException
import com.obdfacile.obd.ObdClient
import com.obdfacile.obd.ObdException
import com.obdfacile.obd.dtc.DtcReport
import com.obdfacile.obd.elm.ElmDevice
import com.obdfacile.obd.pid.MonitorStatus
import com.obdfacile.obd.pid.PidDefinition
import com.obdfacile.obd.pid.PidReading
import com.obdfacile.obd.pid.Pids
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.IOException

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val step: String) : ConnectionState
    data class Connected(val info: AdapterInfo) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

data class UiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val bluetoothEnabled: Boolean = true,
    val devices: List<DeviceItem> = emptyList(),
    val scanningBle: Boolean = false,
    val supportedPids: List<PidDefinition> = emptyList(),
    val live: Map<Int, PidReading> = emptyMap(),
    val status: MonitorStatus? = null,
    val vin: String? = null,
    val dtcReport: DtcReport? = null,
    val busy: Boolean = false,
    val clearResult: ClearResult? = null,
    val message: String? = null,
)

class ObdViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = DeviceScanner(app, app.getSystemService(BluetoothManager::class.java)?.adapter)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Console dei comandi ELM: separata dallo stato per non ridisegnare tutta la UI a ogni riga. */
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private var client: ObdClient? = null
    private var connectJob: Job? = null
    private var liveJob: Job? = null

    fun refreshDevices() {
        _state.update { it.copy(devices = scanner.bondedDevices(), bluetoothEnabled = scanner.bluetoothEnabled) }
    }

    fun scanBle() {
        if (_state.value.scanningBle) return
        viewModelScope.launch {
            _state.update { it.copy(scanningBle = true) }
            try {
                scanner.scanBle(8_000).collect { found ->
                    _state.update { s ->
                        if (s.devices.any { it.address == found.address }) s
                        else s.copy(devices = (s.devices + found).sortedByDescending { it.looksLikeObd })
                    }
                }
            } catch (e: SecurityException) {
                _state.update { it.copy(message = "Permesso Bluetooth mancante") }
            } finally {
                _state.update { it.copy(scanningBle = false) }
            }
        }
    }

    fun connectWifi(host: String, port: Int) =
        connect(DeviceItem(DeviceKind.WIFI, "Adattatore Wi-Fi", "$host:$port"))

    fun connect(target: DeviceItem) {
        if (connectJob?.isActive == true || client != null) return
        connectJob = viewModelScope.launch {
            _state.update { it.copy(connection = ConnectionState.Connecting("Preparazione…")) }
            val c = try {
                ObdClient(ElmDevice(scanner.createTransport(target), ::appendLog))
            } catch (e: Exception) {
                _state.update { it.copy(connection = ConnectionState.Failed(friendlyMessage(e))) }
                return@launch
            }
            try {
                val info = c.connect { step -> _state.update { it.copy(connection = ConnectionState.Connecting(step)) } }
                _state.update { it.copy(connection = ConnectionState.Connecting("Lettura dati supportati…")) }
                val supported = c.supportedPids()
                val status = runCatching { c.readStatus() }.getOrNull()
                val vin = runCatching { c.readVin() }.getOrNull()
                client = c
                _state.update {
                    it.copy(
                        connection = ConnectionState.Connected(info),
                        supportedPids = Pids.all.filter { d -> d.pid in supported },
                        status = status,
                        vin = vin,
                        live = emptyMap(),
                        dtcReport = null,
                        clearResult = null,
                    )
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { c.disconnect() }
                throw e
            } catch (e: Exception) {
                c.disconnect()
                _state.update { it.copy(connection = ConnectionState.Failed(friendlyMessage(e))) }
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        liveJob?.cancel()
        val c = client
        client = null
        viewModelScope.launch {
            c?.disconnect()
            _state.update { UiState(devices = it.devices, bluetoothEnabled = it.bluetoothEnabled) }
        }
    }

    /** Polling dati in tempo reale: i PID "veloci" a ogni giro, gli altri ogni [SLOW_EVERY] giri. */
    fun startLive() {
        val c = client ?: return
        if (liveJob?.isActive == true) return
        liveJob = viewModelScope.launch {
            val defs = _state.value.supportedPids
            val misses = mutableMapOf<Int, Int>()
            var cycle = 0
            var timeouts = 0
            while (isActive && defs.isNotEmpty()) {
                for (def in defs) {
                    if (!def.fast && cycle % SLOW_EVERY != 0) continue
                    if ((misses[def.pid] ?: 0) >= 3) continue // PID dichiarato ma che non risponde mai
                    try {
                        val reading = c.readPid(def)
                        timeouts = 0
                        if (reading == null) {
                            misses[def.pid] = (misses[def.pid] ?: 0) + 1
                        } else {
                            _state.update { it.copy(live = it.live + (def.pid to reading)) }
                        }
                    } catch (e: ElmTimeoutException) {
                        if (++timeouts >= 5) return@launch connectionLost(e)
                    } catch (e: ObdException) {
                        misses[def.pid] = (misses[def.pid] ?: 0) + 1
                    } catch (e: IOException) {
                        return@launch connectionLost(e)
                    }
                }
                cycle++
                yield()
            }
        }
    }

    fun stopLive() {
        liveJob?.cancel()
    }

    fun readDtcs() = withClient { c ->
        val report = c.readAllDtcs()
        val status = runCatching { c.readStatus() }.getOrNull()
        _state.update { it.copy(dtcReport = report, status = status ?: it.status, clearResult = null) }
    }

    fun clearDtcs() = withClient { c ->
        val result = c.clearDtcs()
        val report = runCatching { c.readAllDtcs() }.getOrNull()
        val status = runCatching { c.readStatus() }.getOrNull()
        _state.update { it.copy(clearResult = result, dtcReport = report, status = status ?: it.status) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun withClient(block: suspend (ObdClient) -> Unit) {
        val c = client ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block(c)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ObdException) {
                _state.update { it.copy(message = friendlyMessage(e)) }
            } catch (e: IOException) {
                connectionLost(e)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    // NonCancellable: può essere chiamata dal job di polling stesso, che qui viene cancellato.
    private suspend fun connectionLost(e: Exception) = withContext(NonCancellable) {
        val c = client
        client = null
        liveJob?.cancel()
        c?.disconnect()
        _state.update {
            UiState(
                devices = it.devices,
                bluetoothEnabled = it.bluetoothEnabled,
                connection = ConnectionState.Failed("Connessione persa: ${e.message}"),
            )
        }
    }

    private fun appendLog(line: String) {
        _log.update { (it + line).takeLast(300) }
    }

    private fun friendlyMessage(e: Exception): String = when (e) {
        is NoVehicleResponseException ->
            "L'auto non risponde. Accendi il quadro (chiave su MAR/ON o motore acceso) e controlla che l'adattatore sia ben inserito nella presa OBD."
        is ElmTimeoutException ->
            "L'adattatore non risponde. Scollegalo dalla presa per qualche secondo e riprova."
        is NegativeResponseException -> e.message ?: "Richiesta rifiutata dalla centralina"
        is ObdException -> e.message ?: "Errore di comunicazione"
        is SecurityException -> "Permesso Bluetooth mancante: concedilo dalle impostazioni dell'app."
        is IOException ->
            "Connessione all'adattatore non riuscita. È acceso e associato nelle impostazioni Bluetooth? (${e.message})"
        else -> e.message ?: e.javaClass.simpleName
    }

    override fun onCleared() {
        val c = client
        client = null
        if (c != null) kotlinx.coroutines.runBlocking { c.disconnect() }
    }

    private companion object {
        const val SLOW_EVERY = 5
    }
}
