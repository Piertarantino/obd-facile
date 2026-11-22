package com.obdfacile.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.obdfacile.app.ConnectionState
import com.obdfacile.app.ObdViewModel
import com.obdfacile.app.UiState
import com.obdfacile.app.transport.DeviceItem
import com.obdfacile.app.transport.DeviceKind
import com.obdfacile.obd.transport.WifiTransport

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(state: UiState, vm: ObdViewModel) {
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshDevices()
    }
    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshDevices()
    }
    LaunchedEffect(Unit) { permissions.launch(requiredPermissions()) }

    val connecting = state.connection as? ConnectionState.Connecting
    var host by rememberSaveable { mutableStateOf(WifiTransport.DEFAULT_HOST) }
    var port by rememberSaveable { mutableStateOf(WifiTransport.DEFAULT_PORT.toString()) }

    Scaffold(topBar = { TopAppBar(title = { Text("OBD Facile") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Gratis, senza pubblicità, per sempre. L'auto è tua: riparala tu. Mortacci loro.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Come collegarsi", fontWeight = FontWeight.Bold)
                        Text("1. Inserisci l'adattatore nella presa OBD (di solito sotto il volante).")
                        Text("2. Accendi il quadro o il motore.")
                        Text("3. Bluetooth classico: associa l'adattatore nelle impostazioni di Android (PIN 1234 o 0000).")
                        Text("4. Scegli l'adattatore qui sotto.")
                    }
                }
            }

            if (connecting != null) {
                item {
                    Card {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(connecting.step)
                        }
                    }
                }
                item { OutlinedButton(onClick = vm::disconnect) { Text("Annulla") } }
                return@LazyColumn
            }

            (state.connection as? ConnectionState.Failed)?.let { failed ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(failed.message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (!state.bluetoothEnabled) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text("Il Bluetooth è spento.")
                            Button(onClick = { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) {
                                Text("Attiva Bluetooth")
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Adattatori Bluetooth associati") }
            val bonded = state.devices.filter { it.kind == DeviceKind.BLUETOOTH }
            if (bonded.isEmpty()) item { Text("Nessun dispositivo associato.") }
            items(bonded, key = { "bt-" + it.address }) { DeviceRow(it) { vm.connect(it) } }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Adattatori Bluetooth LE", Modifier.weight(1f))
                    OutlinedButton(onClick = vm::scanBle, enabled = !state.scanningBle) {
                        Text(if (state.scanningBle) "Ricerca…" else "Cerca")
                    }
                }
            }
            items(state.devices.filter { it.kind == DeviceKind.BLE }, key = { "ble-" + it.address }) {
                DeviceRow(it) { vm.connect(it) }
            }

            item { SectionTitle("Adattatore Wi-Fi") }
            item {
                Text(
                    "Collega prima il telefono alla rete Wi-Fi dell'adattatore.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(host, { host = it }, label = { Text("Indirizzo") }, modifier = Modifier.weight(2f), singleLine = true)
                    OutlinedTextField(port, { port = it }, label = { Text("Porta") }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }
            item {
                Button(onClick = { vm.connectWifi(host.trim(), port.toIntOrNull() ?: WifiTransport.DEFAULT_PORT) }) {
                    Text("Connetti via Wi-Fi")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun DeviceRow(device: DeviceItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(device.name, fontWeight = if (device.looksLikeObd) FontWeight.Bold else FontWeight.Normal)
            Text(
                device.address + if (device.looksLikeObd) "  ·  probabile adattatore OBD" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
