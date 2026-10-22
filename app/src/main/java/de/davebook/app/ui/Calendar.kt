@file:OptIn(ExperimentalMaterial3Api::class)

package de.davebook.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import de.davebook.app.CalEvent
import de.davebook.app.Person
import de.davebook.app.Store
import java.time.LocalDate
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

private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
    val ym = YearMonth.of(year, month)
    return ym.atDay(minOf(day, ym.lengthOfMonth()))
}

fun occursOn(e: CalEvent, day: LocalDate): Boolean {
    val d = e.dateOrNull() ?: return false
    if (!e.yearly) return d == day
    if (day.isBefore(d)) return false
    return safeDate(day.year, d.monthValue, d.dayOfMonth) == day
}

fun nextOccurrence(e: CalEvent, from: LocalDate): LocalDate? {
    val d = e.dateOrNull() ?: return null
    if (!e.yearly) return if (d.isBefore(from)) null else d
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
fun CalendarScreen(state: de.davebook.app.AppState) {
    val me = state.me ?: return
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var editing by remember { mutableStateOf<CalEvent?>(null) }
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
                EventCard(e, selected, onClick = { editing = e })
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
                    EventCard(pair.first, pair.second, onClick = { editing = pair.first })
                }
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
                    append(date.format(DateTimeFormatter.ofPattern("EE, d. MMM", GERMAN)))
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
                        "von $by",
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
