@file:OptIn(ExperimentalMaterial3Api::class)

package de.davebook.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.davebook.app.AppState
import de.davebook.app.BuildConfig
import de.davebook.app.Person
import de.davebook.app.Store

private const val PAIR_SHOW = "show"
private const val PAIR_JOIN = "join"

@Composable
fun DavebookRoot(onRequestNotifications: () -> Unit) {
    val state by Store.state.collectAsState()
    if (!state.setupDone || state.me == null) {
        MaterialTheme(colorScheme = LightColors) {
            SetupFlow(state, onFinished = {
                Store.finishSetup()
                onRequestNotifications()
            })
        }
    } else {
        MainScaffold(state)
    }
}

@Composable
private fun MainScaffold(state: AppState) {
    val me = state.me ?: return
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var pairMode by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Davebook", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    label = { Text("Herz") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text("Kalender") },
                )
                if (me == Person.DAVID) {
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                        label = { Text("Erinnerungen") },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                tab == 1 -> CalendarScreen(state)
                tab == 2 && me == Person.DAVID -> RemindersScreen(state)
                else -> HomeScreen(state, onOpenPairing = {
                    pairMode = if (me == Person.SOPHIE) PAIR_SHOW else PAIR_JOIN
                })
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onPairShow = {
                showSettings = false
                pairMode = PAIR_SHOW
            },
            onPairJoin = {
                showSettings = false
                pairMode = PAIR_JOIN
            },
        )
    }
    val mode = pairMode
    if (mode != null) {
        PairDialog(state, mode, onDismiss = { pairMode = null })
    }
}

@Composable
private fun SettingsDialog(
    state: AppState,
    onDismiss: () -> Unit,
    onPairShow: () -> Unit,
    onPairJoin: () -> Unit,
) {
    val me = state.me ?: return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InfoRow("Du bist", me.display)
                InfoRow(
                    "Verbindung",
                    when {
                        state.secret == null -> "nicht gekoppelt"
                        state.connected -> "online ✅"
                        else -> "verbinde …"
                    },
                )
                InfoRow(me.partner.display, if (state.partnerConnected) "verbunden ❤️" else "noch nicht verbunden")
                if (me == Person.DAVID && state.love.isNotEmpty()) InfoRow("Sophies Liebe", state.love)
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onPairShow, modifier = Modifier.fillMaxWidth()) {
                    Text("Kopplungscode anzeigen")
                }
                FilledTonalButton(onClick = onPairJoin, modifier = Modifier.fillMaxWidth()) {
                    Text("Code eingeben")
                }
                OutlinedButton(onClick = { openBatterySettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Akku-Optimierung ausschalten")
                }
                OutlinedButton(onClick = { openNotificationSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Benachrichtigungen")
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Davebook ${BuildConfig.VERSION_NAME} · Nachrichten sind Ende-zu-Ende verschlüsselt",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fertig") }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PairDialog(state: AppState, mode: String, onDismiss: () -> Unit) {
    val partner = state.me?.partner ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == PAIR_SHOW) "Kopplungscode" else "Code eingeben") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (mode == PAIR_SHOW) {
                    Text(
                        "${partner.display} tippt diesen Code in ${partner.seiner} App ein " +
                            "(beim Einrichten oder unter Einstellungen → Code eingeben).",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    PairCodeDisplay(state)
                } else {
                    Text(
                        "Gib den Code ein, den ${partner.display}s App anzeigt " +
                            "(Einstellungen → Kopplungscode anzeigen).",
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    PairJoinForm(onSuccess = onDismiss)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
    )
}
