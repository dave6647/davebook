package de.davebook.app

import java.time.LocalDate
import java.util.Locale

/**
 * Liest Termin-Notizen, wie sie Obsidian mit dem Plugin „Full Calendar“ anlegt:
 * eine Notiz pro Termin, die Angaben stehen im Frontmatter oben in der Datei.
 *
 * ```
 * ---
 * title: Historica Kassel
 * allDay: true
 * date: 2026-10-17
 * endDate: 2026-10-18
 * ---
 * ```
 *
 * Übernommen werden nur Titel, Datum, Enddatum und Uhrzeit. Der Text der Notiz bleibt, wo er ist.
 * Bewusst ohne Android-Abhängigkeiten, damit es sich auch ohne Handy testen lässt.
 */
object EventNotes {

    data class NoteEvent(
        val title: String,
        /** yyyy-MM-dd */
        val date: String,
        /** yyyy-MM-dd (einschließlich) oder leer = eintägig */
        val endDate: String,
        /** "HH:mm" oder leer = ganztägig */
        val time: String,
    )

    sealed class Result {
        data class Event(val event: NoteEvent) : Result()

        /** Wiederkehrender Termin (wöchentlich o. Ä.), das kann Davebook noch nicht. */
        object Recurring : Result()

        /** Keine Termin-Notiz (z. B. Packliste). */
        object None : Result()
    }

    private val KEY_LINE = Regex("^([A-Za-z0-9_][A-Za-z0-9_\\- ]*?)\\s*:(?:\\s+(.*))?$")
    private val DATE = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})")
    private val TIME = Regex("^(\\d{1,2}):(\\d{2})(?::\\d{2})?\\s*([AaPp][Mm])?$")
    private val DATE_PREFIX = Regex("^\\d{4}-\\d{1,2}-\\d{1,2}\\s*[-–]?\\s*")

    /** Wie viele Zeilen höchstens gelesen werden, bevor das Frontmatter als kaputt gilt. */
    const val MAX_LINES = 200

    /**
     * Liest das Frontmatter (zwischen dem ersten und zweiten `---`).
     * Gibt null zurück, wenn die Datei keins hat. Listen und verschachtelte Werte werden übersprungen.
     */
    fun frontmatter(lines: Iterator<String>): Map<String, String>? {
        if (!lines.hasNext()) return null
        val first = lines.next().removePrefix("﻿").trimEnd()
        if (first != "---") return null
        val out = LinkedHashMap<String, String>()
        var n = 0
        while (lines.hasNext() && n < MAX_LINES) {
            n++
            val raw = lines.next().trimEnd()
            if (raw == "---" || raw == "...") return out
            if (raw.isEmpty() || raw.startsWith(" ") || raw.startsWith("\t") || raw.startsWith("-") || raw.startsWith("#")) continue
            val m = KEY_LINE.matchEntire(raw) ?: continue
            out[m.groupValues[1].trim()] = scalar(m.groupValues[2])
        }
        // Kein schließendes --- gefunden
        return null
    }

    fun frontmatter(text: String): Map<String, String>? = frontmatter(text.lineSequence().iterator())

    /** Einfacher YAML-Wert: Anführungszeichen entfernen, Kommentare abschneiden, null → leer. */
    internal fun scalar(rawValue: String): String {
        val v = rawValue.trim()
        if (v.isEmpty()) return ""
        if (v.startsWith("\"")) {
            val sb = StringBuilder()
            var i = 1
            while (i < v.length) {
                val c = v[i]
                if (c == '\\' && i + 1 < v.length) {
                    val next = v[i + 1]
                    sb.append(
                        when (next) {
                            'n' -> '\n'
                            't' -> '\t'
                            else -> next
                        },
                    )
                    i += 2
                    continue
                }
                if (c == '"') break
                sb.append(c)
                i++
            }
            return sb.toString()
        }
        if (v.startsWith("'")) {
            val sb = StringBuilder()
            var i = 1
            while (i < v.length) {
                val c = v[i]
                if (c == '\'') {
                    if (i + 1 < v.length && v[i + 1] == '\'') {
                        sb.append('\'')
                        i += 2
                        continue
                    }
                    break
                }
                sb.append(c)
                i++
            }
            return sb.toString()
        }
        val noComment = v.substringBefore(" #").trim()
        return when (noComment.lowercase()) {
            "null", "~" -> ""
            else -> noComment
        }
    }

    fun parseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        val m = DATE.find(value.trim()) ?: return null
        return try {
            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        } catch (e: Exception) {
            null
        }
    }

    /** "9:00", "09:00:00", "7:30 pm" → "HH:mm"; sonst leer. */
    fun parseTime(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val m = TIME.matchEntire(value.trim()) ?: return ""
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val ampm = m.groupValues[3].lowercase()
        if (ampm == "pm" && h < 12) h += 12
        if (ampm == "am" && h == 12) h = 0
        if (h !in 0..23 || min !in 0..59) return ""
        return String.format(Locale.ROOT, "%02d:%02d", h, min)
    }

    /** "2026-10-17 Historica Kassel.md" → "Historica Kassel" */
    fun titleFromFileName(fileName: String): String {
        val base = fileName.removeSuffix(".md").removeSuffix(".MD").trim()
        val stripped = base.replaceFirst(DATE_PREFIX, "").trim()
        return stripped.ifEmpty { base }
    }

    fun parse(fileName: String, fm: Map<String, String>?): Result {
        if (fm == null) return Result.None
        val type = fm["type"].orEmpty().lowercase()
        val looksLikeEvent = fm.containsKey("title") || fm.containsKey("allDay") ||
            fm.containsKey("startTime") || fm.containsKey("type")
        if (!looksLikeEvent) return Result.None
        if (type == "recurring" || type == "rrule") return Result.Recurring
        if (type.isNotEmpty() && type != "single") return Result.None

        val date = parseDate(fm["date"]) ?: return Result.None
        val end = parseDate(fm["endDate"])
        val endDate = if (end != null && end.isAfter(date)) end.toString() else ""
        val allDay = fm["allDay"].orEmpty().trim().equals("true", ignoreCase = true)
        val time = if (allDay) "" else parseTime(fm["startTime"])
        val title = fm["title"].orEmpty().trim().ifEmpty { titleFromFileName(fileName) }.take(80)
        if (title.isEmpty()) return Result.None
        return Result.Event(NoteEvent(title = title, date = date.toString(), endDate = endDate, time = time))
    }

    fun parse(fileName: String, text: String): Result = parse(fileName, frontmatter(text))
}
