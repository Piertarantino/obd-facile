package com.obdfacile.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.obdfacile.app.UiState
import com.obdfacile.obd.pid.MonitorStatus
import com.obdfacile.obd.pid.PidDefinition
import com.obdfacile.obd.pid.PidReading

@Composable
fun DashboardScreen(state: UiState, onStart: () -> Unit, onStop: () -> Unit) {
    // Polling attivo solo quando questa schermata è visibile e l'app è in primo piano
    LifecycleStartEffect(Unit) {
        onStart()
        onStopOrDispose { onStop() }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.status?.let { status ->
            item(span = { GridItemSpan(maxLineSpan) }) { MilBanner(status) }
        }
        if (state.supportedPids.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Questa auto non dichiara dati in tempo reale supportati.")
            }
        }
        items(state.supportedPids, key = { it.pid }) { def ->
            GaugeCard(def, state.live[def.pid])
        }
    }
}

@Composable
fun MilBanner(status: MonitorStatus) {
    val bad = status.milOn || status.dtcCount > 0
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (bad) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Text(
            when {
                status.milOn -> "Spia motore ACCESA · ${status.dtcCount} errori memorizzati"
                status.dtcCount > 0 -> "Spia motore spenta · ${status.dtcCount} errori memorizzati"
                else -> "Spia motore spenta · nessun errore memorizzato"
            },
            Modifier.padding(16.dp).fillMaxWidth(),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun GaugeCard(def: PidDefinition, reading: PidReading?) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(def.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(reading?.formatted ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(" " + def.unit, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
            }
            val fraction = reading?.let { ((it.value - def.min) / (def.max - def.min)).toFloat().coerceIn(0f, 1f) } ?: 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}
