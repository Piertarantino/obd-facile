package com.obdfacile.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.obdfacile.app.ConnectionState
import com.obdfacile.app.UiState

@Composable
fun InfoScreen(state: UiState, log: List<String>) {
    val info = (state.connection as? ConnectionState.Connected)?.info
    var showLog by remember { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Veicolo", style = MaterialTheme.typography.titleMedium)
                InfoRow("Telaio (VIN)", state.vin ?: "non disponibile")
                InfoRow("Protocollo", info?.protocol?.label ?: "—")
                InfoRow("Centraline OBD", info?.ecus?.joinToString() ?: "—")
                InfoRow("Tensione batteria", info?.batteryVoltage ?: "—")
                InfoRow("Adattatore", info?.elmVersion ?: "—")
                InfoRow("Collegamento", info?.transport ?: "—")
            }
        }

        state.status?.let { status ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Test di prontezza (revisione)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (status.allReady) "Tutti i test completati." else "Alcuni test non sono ancora completati: guida ancora qualche giorno prima della revisione.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    status.monitors.forEach { InfoRow(it.name, if (it.complete) "✓ pronto" else "✗ non pronto") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    Text("Console adattatore", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Nascondi" else "Mostra") }
                }
                if (showLog) {
                    Text(
                        log.takeLast(100).joinToString("\n"),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1.3f), fontWeight = FontWeight.Medium)
    }
}
