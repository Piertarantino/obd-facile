package com.obdfacile.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.obdfacile.app.ConnectionState
import com.obdfacile.app.ObdViewModel

private enum class Tab(val title: String, val icon: ImageVector) {
    DASHBOARD("Cruscotto", Icons.Filled.Home),
    DTC("Errori", Icons.Filled.Warning),
    INFO("Auto", Icons.Filled.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdApp(vm: ObdViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val connected = state.connection is ConnectionState.Connected

    // Schermo sempre acceso mentre si è collegati all'auto
    val view = LocalView.current
    DisposableEffect(connected) {
        view.keepScreenOn = connected
        onDispose { view.keepScreenOn = false }
    }

    if (!connected) {
        ConnectScreen(state = state, vm = vm)
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.title) },
                actions = {
                    IconButton(onClick = vm::disconnect) { Icon(Icons.Filled.Close, contentDescription = "Disconnetti") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.DASHBOARD -> DashboardScreen(state, onStart = vm::startLive, onStop = vm::stopLive)
                Tab.DTC -> DtcScreen(state, onRead = vm::readDtcs, onClear = vm::clearDtcs)
                Tab.INFO -> InfoScreen(state, log)
            }
        }
    }
}
