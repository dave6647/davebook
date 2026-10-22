@file:OptIn(ExperimentalMaterial3Api::class)

package de.davebook.app.ui

import android.os.PowerManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.davebook.app.AppState
import de.davebook.app.Note
import de.davebook.app.Person
import de.davebook.app.Quotes
import de.davebook.app.Store
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(state: AppState, onOpenPairing: () -> Unit) {
    val me = state.me ?: return
    val partner = me.partner
    var showNoteDialog by remember { mutableStateOf(false) }
    var burstKey by remember { mutableIntStateOf(0) }
    var flashText by remember { mutableStateOf<String?>(null) }
    var flashId by remember { mutableIntStateOf(0) }

    LaunchedEffect(partner) {
        Store.incomingHearts.collect {
            burstKey++
            flashText = "💗 ${partner.display} denkt gerade an dich!"
            flashId++
        }
    }
    LaunchedEffect(flashId) {
        if (flashId > 0) {
            delay(2800)
            flashText = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Hallo ${me.display} ❤️",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        if (state.secret == null) {
            ConnectCard(partner, onOpenPairing)
            SectionGap()
        } else if (!state.partnerConnected) {
            Text(
                "Warte darauf, dass sich ${partner.display} verbindet …",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionGap()
        }

        PartnerNoteCard(partner, state.notes[partner.id])

        Spacer(Modifier.height(8.dp))
        HeartButton(
            onTap = {
                Store.sendHeart()
                flashText = "❤️ ${partner.display} weiß jetzt, dass du an ${partner.ihn} denkst"
                flashId++
            },
            burstKey = burstKey,
        )

        Column(Modifier.heightIn(min = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val flash = flashText
            AnimatedVisibility(visible = flash != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    flash ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
            if (flash == null) {
                Text(
                    "Tippe aufs Herz – ${partner.display} bekommt sofort Bescheid",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (state.outbox.isNotEmpty() && !state.connected && state.secret != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Offline – wird gesendet, sobald du wieder Internet hast",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        StatsRow(state, partner)

        if (me == Person.DAVID && state.love.isNotEmpty()) {
            SectionGap()
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "Wie sehr Sophie dich liebt",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${state.love} ❤️",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        SectionGap()
        MyNoteCard(partner, state.notes[me.id], onWrite = { showNoteDialog = true })

        BatteryTip(state)
        Spacer(Modifier.height(24.dp))
    }

    if (showNoteDialog) {
        NoteDialog(
            partner = partner,
            initial = state.notes[me.id]?.takeIf { dayLabel(it.ts) == "heute" }?.text ?: "",
            onDismiss = { showNoteDialog = false },
            onSave = {
                Store.setMyNote(it)
                showNoteDialog = false
            },
        )
    }
}

@Composable
private fun ConnectCard(partner: Person, onOpenPairing: () -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "Noch nicht mit ${partner.display} verbunden",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Ohne Verbindung kommen Herzen, Sprüche und Termine nicht bei ${partner.display} an.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenPairing) { Text("Jetzt verbinden") }
        }
    }
}

@Composable
private fun PartnerNoteCard(partner: Person, note: Note?) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            val label = if (note != null && note.text.isNotBlank()) {
                "💌 Von ${partner.display} · ${dayLabel(note.ts)}"
            } else {
                "💌 Von ${partner.display}"
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            if (note == null || note.text.isBlank()) {
                Text(
                    "${partner.display} hat dir noch nichts dagelassen.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    note.text,
                    style = MaterialTheme.typography.titleLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun StatsRow(state: AppState, partner: Person) {
    val start = startOfTodayMillis()
    val sent = state.heartsSent.count { it >= start }
    val received = state.heartsReceived.count { it >= start }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatPill("Verschickte Liebe", "$sent ❤️", Modifier.weight(1f))
        StatPill("Erhaltene Liebe", "$received ❤️", Modifier.weight(1f))
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "heute",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MyNoteCard(partner: Person, note: Note?, onWrite: () -> Unit) {
    OutlinedCard(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "✍️ Dein Spruch für ${partner.display}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            val hasToday = note != null && note.text.isNotBlank() && dayLabel(note.ts) == "heute"
            if (note != null && note.text.isNotBlank()) {
                Text(note.text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    "dagelassen ${dayLabel(note.ts)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Lass ${partner.display} heute etwas Liebes da.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onWrite) {
                Text(if (hasToday) "Ändern" else "Spruch für heute schreiben")
            }
        }
    }
}

@Composable
private fun NoteDialog(partner: Person, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spruch für ${partner.display}") },
        text = {
            Column {
                Text(
                    "${partner.display} sieht ihn ganz oben auf ${partner.seiner} Startseite.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(300) },
                    placeholder = { Text("Ein lieber Gedanke für heute …") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { text = Quotes.random(text) }) {
                    Text("💡 Spruch-Idee")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text("Dalassen ❤️")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
private fun BatteryTip(state: AppState) {
    if (state.secret == null || state.batteryTipDismissed) return
    val context = LocalContext.current
    val tick by Store.resumeTick.collectAsState()
    val ignoring = remember(tick) {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }
    if (ignoring) return
    SectionGap()
    Card(shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("🔋 Damit Herzen sofort ankommen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Erlaube Davebook, im Hintergrund zu laufen. Sonst kann das Handy die Verbindung zum Akkusparen kappen.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = { Store.dismissBatteryTip() }) { Text("Später") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { openBatterySettings(context) }) { Text("Erlauben") }
            }
        }
    }
}
