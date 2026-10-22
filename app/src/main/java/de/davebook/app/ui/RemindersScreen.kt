@file:OptIn(ExperimentalMaterial3Api::class)

package de.davebook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davebook.app.AppState
import de.davebook.app.Person
import de.davebook.app.Reminder
import de.davebook.app.Reminders
import de.davebook.app.Store
import java.util.UUID

private fun suggestions(partner: Person) = listOf(
    "Genug getrunken? 💧",
    "Denk an deine Tablette 💊",
    "Kurz Pause machen 🌿",
    "Zeit für einen Snack 🍎",
    if (partner == Person.SOPHIE) "Kurz durchatmen 🧘‍♀️" else "Kurz durchatmen 🧘‍♂️",
    "Ich hab dich lieb ❤️",
    "Du bist toll, vergiss das nicht ✨",
)

/** Erinnerungen für den anderen (David für Sophie und Sophie für David). Sie erscheinen automatisch auf dessen Handy. */
@Composable
fun RemindersScreen(state: AppState) {
    val me = state.me ?: return
    val partner = me.partner
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var creating by remember { mutableStateOf(false) }
    val list = state.reminders.values
        .filter { !it.deleted && it.target == partner.id }
        .sortedWith(compareBy<Reminder> { it.id != Reminder.DRINK_ID }.thenBy { it.text.lowercase() })

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        ) {
            item {
                Column(Modifier.padding(bottom = 12.dp)) {
                    Text(
                        "Erinnerungen für ${partner.display}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kommen automatisch als Benachrichtigung auf ${partner.display}s Handy – " +
                            "auch wenn die App zu ist. Mit dem Schalter an- und ausmachen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!state.partnerConnected) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Hinweis: ${partner.display} ist noch nicht verbunden. Änderungen kommen an, sobald ihr gekoppelt seid.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        "Noch keine Erinnerungen. Tippe auf +, um eine anzulegen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(list, key = { it.id }) { r ->
                ReminderCard(
                    r = r,
                    onToggle = { on ->
                        val since = if (on && !r.enabled) System.currentTimeMillis() else r.since
                        Store.saveReminder(r.copy(enabled = on, since = since))
                    },
                    onClick = { editing = r },
                )
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Erinnerung anlegen")
        }
    }

    if (creating) {
        ReminderDialog(initial = null, me = me, onDismiss = { creating = false })
    }
    val e = editing
    if (e != null) {
        ReminderDialog(initial = e, me = me, onDismiss = { editing = null })
    }
}

@Composable
private fun ReminderCard(r: Reminder, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (r.enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🔔", fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = buildString {
                    append(Reminders.intervalLabel(r.intervalMinutes))
                    if (r.quiet) append(" · nachts stumm")
                    if (!r.enabled) append(" · aus")
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = r.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ReminderDialog(initial: Reminder?, me: Person, onDismiss: () -> Unit) {
    val partner = me.partner
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var interval by remember { mutableIntStateOf(initial?.intervalMinutes ?: 480) }
    var quiet by remember { mutableStateOf(initial?.quiet ?: true) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neue Erinnerung" else "Erinnerung bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(120) },
                    label = { Text("Was soll bei ${partner.display} erscheinen?") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    suggestions(partner).forEach { s ->
                        AssistChip(
                            onClick = { text = s },
                            label = { Text(s) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Wie oft?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Reminders.intervalOptions.forEach { m ->
                        FilterChip(
                            selected = interval == m,
                            onClick = { interval = m },
                            label = { Text(Reminders.chipLabel(m)) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Nachts stumm")
                        Text(
                            "zwischen 22 und 8 Uhr nichts senden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = quiet, onCheckedChange = { quiet = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktiv", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    val now = System.currentTimeMillis()
                    val base = initial ?: Reminder(
                        id = UUID.randomUUID().toString(),
                        text = "",
                        intervalMinutes = interval,
                        enabled = true,
                        quiet = true,
                        target = partner.id,
                        since = now,
                        by = me.id,
                        updated = 0L,
                        deleted = false,
                    )
                    val resetClock = initial == null ||
                        initial.intervalMinutes != interval ||
                        (!initial.enabled && enabled)
                    Store.saveReminder(
                        base.copy(
                            text = text.trim(),
                            intervalMinutes = interval,
                            quiet = quiet,
                            enabled = enabled,
                            since = if (resetClock) now else base.since,
                        ),
                    )
                    onDismiss()
                },
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = {
                        if (confirmDelete) {
                            Store.deleteReminder(initial)
                            onDismiss()
                        } else {
                            confirmDelete = true
                        }
                    }) {
                        Text(
                            if (confirmDelete) "Wirklich löschen?" else "Löschen",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}
