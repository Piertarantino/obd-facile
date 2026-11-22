package com.obdfacile.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.obdfacile.app.UiState
import com.obdfacile.obd.dtc.Dtc
import com.obdfacile.obd.dtc.DtcKind

@Composable
fun DtcScreen(state: UiState, onRead: () -> Unit, onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.status?.let { MilBanner(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRead, enabled = !state.busy) { Text("Leggi errori") }
            OutlinedButton(
                onClick = { confirmClear = true },
                enabled = !state.busy && state.dtcReport != null,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Cancella errori") }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        state.clearResult?.let { r ->
            Card {
                Text(
                    when {
                        r.remainingDtcCount == 0 -> "Errori cancellati. Se il guasto c'è ancora, torneranno."
                        r.remainingDtcCount != null -> "Cancellazione inviata, ma restano ${r.remainingDtcCount} errori: il guasto è probabilmente ancora presente."
                        r.acknowledged -> "Cancellazione confermata dalla centralina."
                        else -> "La centralina non ha confermato la cancellazione. Riprova a motore spento e quadro acceso."
                    },
                    Modifier.padding(16.dp),
                )
            }
        }

        val report = state.dtcReport
        if (report == null) {
            Text("Premi \"Leggi errori\" per interrogare la centralina.")
        } else if (report.isEmpty) {
            Text("Nessun codice errore. 👍", style = MaterialTheme.typography.titleMedium)
        } else {
            DtcKind.entries.forEach { kind ->
                val list = report.byKind(kind)
                if (list.isNotEmpty()) {
                    Text("${kind.label} (${list.size})", style = MaterialTheme.typography.titleMedium)
                    Text(kind.explanation, style = MaterialTheme.typography.bodySmall)
                    list.forEach { DtcCard(it) }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Cancellare gli errori?") },
            text = {
                Text(
                    "• Spegni il motore e lascia il quadro acceso.\n" +
                        "• Cancellare non ripara il guasto: se c'è ancora, l'errore torna.\n" +
                        "• Si azzerano anche i test di prontezza (readiness): prima della revisione " +
                        "bisogna guidare qualche giorno perché tornino pronti.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text("Cancella") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun DtcCard(dtc: Dtc) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(dtc.code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(dtc.description)
            Text("Centralina ${dtc.ecu}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
