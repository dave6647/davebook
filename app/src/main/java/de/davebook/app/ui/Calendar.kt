@file:OptIn(ExperimentalMaterial3Api::class)

package de.davebook.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.davebook.app.AppState
import de.davebook.app.CalEvent
import de.davebook.app.FolderImport
import de.davebook.app.ImportFolder
import de.davebook.app.Person
import de.davebook.app.Store
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

private val EMOJIS = listOf("❤️", "🎂", "🍝", "🎬", "✈️", "🎉", "💍", "🎁", "🏠", "🏖️", "🍷", "📅")
private val GERMAN: Locale = Locale.GERMAN

// ------------------------------------------------------------------ Logik

fun CalEvent.dateOrNull(): LocalDate? = try {
    LocalDate.parse(date)
} catch (e: Exception) {
    null
}

/** Letzter Tag eines mehrtägigen Termins, sonst null. */
fun CalEvent.endOrNull(): LocalDate? {
    if (endDate.isEmpty() || yearly) return null
    val start = dateOrNull() ?: return null
    val end = try {
        LocalDate.parse(endDate)
    } catch (e: Exception) {
        return null
    }
    return if (end.isAfter(start)) end else null
}

private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
    val ym = YearMonth.of(year, month)
    return ym.atDay(minOf(day, ym.lengthOfMonth()))
}

fun occursOn(e: CalEvent, day: LocalDate): Boolean {
    val d = e.dateOrNull() ?: return false
    if (!e.yearly) {
        val end = e.endOrNull() ?: return d == day
        return !day.isBefore(d) && !day.isAfter(end)
    }
    if (day.isBefore(d)) return false
    return safeDate(day.year, d.monthValue, d.dayOfMonth) == day
}

fun nextOccurrence(e: CalEvent, from: LocalDate): LocalDate? {
    val d = e.dateOrNull() ?: return null
    if (!e.yearly) {
        val last = e.endOrNull() ?: d
        return when {
            last.isBefore(from) -> null
            // läuft gerade (mehrtägig)
            d.isBefore(from) -> from
            else -> d
        }
    }
    if (!d.isBefore(from)) return d
    for (y in from.year..from.year + 1) {
        val candidate = safeDate(y, d.monthValue, d.dayOfMonth)
        if (!candidate.isBefore(from)) return candidate
    }
    return null
}

fun upcoming(events: List<CalEvent>, from: LocalDate, limit: Int): List<Pair<CalEvent, LocalDate>> =
    events
        .mapNotNull { e -> nextOccurrence(e, from)?.let { e to it } }
        .sortedWith(compareBy<Pair<CalEvent, LocalDate>> { it.second }.thenBy { it.first.time })
        .take(limit)

// ------------------------------------------------------------------ Oberfläche

@Composable
fun CalendarScreen(state: AppState, onOpenImport: () -> Unit) {
    val me = state.me ?: return
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var editing by remember { mutableStateOf<CalEvent?>(null) }
    var viewing by remember { mutableStateOf<CalEvent?>(null) }
    val open: (CalEvent) -> Unit = { if (it.imported) viewing = it else editing = it }
    var creating by remember { mutableStateOf(false) }
    val events = remember(state.events) { state.events.values.filter { !it.deleted } }
    val today = LocalDate.now()
    val dayEvents = events.filter { occursOn(it, selected) }.sortedBy { it.time }
    val next = upcoming(events, today, 8)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        ) {
            item {
                MonthHeader(
                    month = month,
                    onPrev = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) },
                    onToday = {
                        month = YearMonth.now()
                        selected = LocalDate.now()
                    },
                )
            }
            item {
                MonthGrid(month = month, selected = selected, events = events, onSelect = { selected = it })
            }
            item {
                Text(
                    selected.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", GERMAN)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                )
            }
            if (dayEvents.isEmpty()) {
                item {
                    Text(
                        "Nichts geplant – tippe auf +, um etwas einzutragen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(dayEvents, key = { "day-" + it.id }) { e ->
                EventCard(e, selected, onClick = { open(e) })
            }
            if (next.isNotEmpty()) {
                item {
                    Text(
                        "Demnächst",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                    )
                }
                items(next, key = { "next-" + it.first.id + "-" + it.second }) { pair ->
                    EventCard(pair.first, pair.second, onClick = { open(pair.first) })
                }
            }
            item {
                ImportEntry(state, onClick = onOpenImport)
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Termin eintragen")
        }
    }

    if (creating) {
        EventDialog(initial = null, defaultDate = selected, me = me, onDismiss = { creating = false })
    }
    val e = editing
    if (e != null) {
        EventDialog(initial = e, defaultDate = selected, me = me, onDismiss = { editing = null })
    }
    val v = viewing
    if (v != null) {
        ImportedEventDialog(v, state, onDismiss = { viewing = null })
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Voriger Monat")
        }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", GERMAN)),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToday),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nächster Monat")
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, selected: LocalDate, events: List<CalEvent>, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val rows = (offset + days + 6) / 7
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - offset + 1
                    if (dayNum in 1..days) {
                        val date = month.atDay(dayNum)
                        DayCell(
                            day = dayNum,
                            isSelected = date == selected,
                            isToday = date == today,
                            hasEvents = events.any { occursOn(it, date) },
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val bg = when {
        isSelected -> cs.primary
        isToday -> cs.primaryContainer
        else -> Color.Transparent
    }
    val fg = when {
        isSelected -> cs.onPrimary
        isToday -> cs.onPrimaryContainer
        else -> cs.onSurface
    }
    Box(
        modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            color = fg,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
        )
        if (hasEvents) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) cs.onPrimary else HeartDeep),
            )
        }
    }
}

@Composable
private fun EventCard(e: CalEvent, date: LocalDate, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(e.emoji.ifEmpty { "❤️" }, fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = buildString {
                    append(dateText(e, date, "EE, d. MMM"))
                    if (e.time.isNotEmpty()) append(" · ${e.time} Uhr")
                    if (e.yearly) append(" · jährlich")
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (e.note.isNotBlank()) {
                    Text(e.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val by = Person.fromId(e.by)?.display
                if (by != null) {
                    Text(
                        if (e.imported) "von $by · importiert" else "von $by",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            CountdownPill(date)
        }
    }
}

@Composable
private fun CountdownPill(date: LocalDate) {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), date)
    val text = when {
        days < 0L -> "vorbei"
        days == 0L -> "heute"
        days == 1L -> "morgen"
        else -> "in $days T."
    }
    val soon = days == 0L || days == 1L
    Surface(
        shape = RoundedCornerShape(50),
        color = if (soon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (soon) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EventDialog(initial: CalEvent?, defaultDate: LocalDate, me: Person, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableStateOf(initial?.dateOrNull() ?: defaultDate) }
    var time by remember { mutableStateOf(initial?.time ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji?.ifEmpty { null } ?: "❤️") }
    var yearly by remember { mutableStateOf(initial?.yearly ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Neuer Termin" else "Termin bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Was?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    EMOJIS.forEach { em ->
                        Box(
                            Modifier
                                .padding(end = 6.dp)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (em == emoji) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                )
                                .clickable { emoji = em },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(em, fontSize = 20.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                            date.year,
                            date.monthValue - 1,
                            date.dayOfMonth,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("📅  " + date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", GERMAN)))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            val parts = time.split(":")
                            val h = parts.getOrNull(0)?.toIntOrNull() ?: 19
                            val min = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            TimePickerDialog(
                                context,
                                { _, hh, mm -> time = String.format(Locale.GERMAN, "%02d:%02d", hh, mm) },
                                h,
                                min,
                                true,
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (time.isEmpty()) "🕐  Ganztägig" else "🕐  $time Uhr")
                    }
                    if (time.isNotEmpty()) {
                        IconButton(onClick = { time = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Uhrzeit entfernen")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Jedes Jahr (z. B. Jahrestag)", modifier = Modifier.weight(1f))
                    Switch(checked = yearly, onCheckedChange = { yearly = it })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    label = { Text("Notiz (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    val base = initial ?: CalEvent(
                        id = UUID.randomUUID().toString(),
                        title = "",
                        date = "",
                        time = "",
                        note = "",
                        emoji = "",
                        yearly = false,
                        by = me.id,
                        updated = 0L,
                        deleted = false,
                    )
                    Store.saveEvent(
                        base.copy(
                            title = title.trim(),
                            date = date.toString(),
                            time = time,
                            note = note.trim(),
                            emoji = emoji,
                            yearly = yearly,
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
                            Store.deleteEvent(initial)
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

// ------------------------------------------------------------------ Import-Ordner

private fun termine(n: Int): String = if (n == 1) "1 Termin" else "$n Termine"

/** Datum (bei mehrtägigen Terminen „von – bis“) im gewünschten Format. */
private fun dateText(e: CalEvent, date: LocalDate, pattern: String): String {
    val f = DateTimeFormatter.ofPattern(pattern, GERMAN)
    val start = e.dateOrNull()
    val end = e.endOrNull()
    return if (start != null && end != null) start.format(f) + " – " + end.format(f) else date.format(f)
}

@Composable
private fun ImportEntry(state: AppState, onClick: () -> Unit) {
    val folders = state.importFolders
    val count = folders.sumOf { it.count }
    val text = when {
        folders.isEmpty() -> "Termine aus einem Ordner übernehmen, z. B. aus Obsidian"
        folders.size == 1 -> "Import-Ordner „${folders[0].name}“ · ${termine(count)}"
        else -> "${folders.size} Import-Ordner · ${termine(count)}"
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📂", fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** Importierte Termine lassen sich in Davebook nur ansehen, geändert werden sie im Ordner (z. B. in Obsidian). */
@Composable
private fun ImportedEventDialog(e: CalEvent, state: AppState, onDismiss: () -> Unit) {
    val me = state.me
    val owner = Person.fromId(e.by)
    val folder = state.importFolders.firstOrNull { it.id == e.source }
    val start = e.dateOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.title) },
        text = {
            Column {
                if (start != null) {
                    Text("📅  " + dateText(e, start, "EEEE, d. MMMM yyyy"), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    if (e.time.isEmpty()) "🕐  Ganztägig" else "🕐  ${e.time} Uhr",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    when {
                        me != null && owner == me -> {
                            val name = folder?.let { " „${it.name}“" } ?: ""
                            "Kommt aus deinem Import-Ordner$name. Ändern oder löschen kannst du den Termin dort " +
                                "(z. B. in Obsidian), Davebook übernimmt das automatisch."
                        }

                        owner != null ->
                            "${owner.display} hat diesen Termin aus ${owner.seiner} Kalender-App übernommen. " +
                                "Ändern lässt er sich nur dort."

                        else -> "Importierter Termin. Ändern lässt er sich nur dort, wo er herkommt."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
    )
}

/** Ordner verknüpfen und wieder entfernen. Die Termine daraus erscheinen bei beiden im Kalender. */
@Composable
fun ImportFoldersDialog(state: AppState, onDismiss: () -> Unit) {
    val me = state.me ?: return
    val context = LocalContext.current
    val busy by FolderImport.busy.collectAsState()
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) FolderImport.add(context, uri)
    }
    val fromPartner = state.events.values.count { it.imported && !it.deleted && it.by == me.partner.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import-Ordner") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Übernimm Termine aus einem Ordner auf diesem Handy, z. B. aus Obsidian mit Full Calendar. " +
                        "Neue, geänderte und gelöschte Termine kommen automatisch hier und bei ${me.partner.display} an. " +
                        "Übernommen werden nur Titel, Datum und Uhrzeit, nicht der Text der Notizen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                if (state.importFolders.isEmpty()) {
                    Text(
                        "Noch kein Ordner verknüpft.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.importFolders.forEach { f ->
                    FolderRow(
                        f = f,
                        busy = busy,
                        partner = me.partner,
                        confirming = confirmRemove == f.id,
                        onRemove = { confirmRemove = f.id },
                        onConfirmRemove = {
                            FolderImport.remove(context, f.id)
                            confirmRemove = null
                        },
                        onCancelRemove = { confirmRemove = null },
                    )
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { picker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ordner hinzufügen")
                }
                if (state.importFolders.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { FolderImport.refresh() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (busy) "Wird eingelesen …" else "Jetzt aktualisieren")
                    }
                }
                if (fromPartner > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Von ${me.partner.display} importiert: ${termine(fromPartner)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tipp: Wähle den Ordner mit den Termin-Notizen, z. B. „Events“ in deinem Obsidian-Tresor. " +
                        "Unterordner (2026, 2027 …) werden mit eingelesen, andere Notizen ohne Datum übersprungen.",
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
private fun FolderRow(
    f: ImportFolder,
    busy: Boolean,
    partner: Person,
    confirming: Boolean,
    onRemove: () -> Unit,
    onConfirmRemove: () -> Unit,
    onCancelRemove: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📂", fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        f.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val path = FolderImport.pathOf(f.uri)
                    if (path != null && path != f.name) {
                        Text(path, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 2)
                    }
                    val status = if (f.lastScan == 0L) {
                        if (busy) "Wird eingelesen …" else "Noch nicht eingelesen"
                    } else {
                        val time = Instant.ofEpochMilli(f.lastScan).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("HH:mm", GERMAN))
                        termine(f.count) + " · aktualisiert " + dayLabel(f.lastScan) + " um " + time
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    if (f.skipped > 0) {
                        Text(
                            "${f.skipped} wiederkehrende " + (if (f.skipped == 1) "Termin wird" else "Termine werden") +
                                " noch nicht übernommen",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    if (f.error.isNotEmpty()) {
                        Text(f.error, style = MaterialTheme.typography.bodySmall, color = cs.error)
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Ordner entfernen")
                }
            }
            if (confirming) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ordner entfernen? ${if (f.count == 1) "Der Termin daraus verschwindet" else "Die ${f.count} Termine daraus verschwinden"} " +
                        "aus Davebook, bei dir und bei ${partner.display}. Im Ordner selbst (z. B. in Obsidian) bleibt alles, wie es ist.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancelRemove) { Text("Abbrechen") }
                    Button(
                        onClick = onConfirmRemove,
                        colors = ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError),
                    ) {
                        Text("Entfernen")
                    }
                }
            }
        }
    }
}
