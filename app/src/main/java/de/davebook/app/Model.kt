package de.davebook.app

import org.json.JSONArray
import org.json.JSONObject

enum class Person(
    val id: String,
    val display: String,
    /** "er" / "sie" */
    val er: String,
    /** "ihn" / "sie" */
    val ihn: String,
    /** "seiner" / "ihrer" */
    val seiner: String,
) {
    DAVID("david", "David", "er", "ihn", "seiner"),
    SOPHIE("sophie", "Sophie", "sie", "sie", "ihrer");

    val partner: Person get() = if (this == DAVID) SOPHIE else DAVID

    companion object {
        fun fromId(id: String?): Person? = entries.firstOrNull { it.id == id }
    }
}

data class CalEvent(
    val id: String,
    val title: String,
    /** yyyy-MM-dd */
    val date: String,
    /** "HH:mm" oder leer = ganztägig */
    val time: String,
    val note: String,
    val emoji: String,
    val yearly: Boolean,
    val by: String,
    val updated: Long,
    val deleted: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("date", date)
        .put("time", time)
        .put("note", note)
        .put("emoji", emoji)
        .put("yearly", yearly)
        .put("by", by)
        .put("upd", updated)
        .put("del", deleted)

    companion object {
        fun fromJson(o: JSONObject): CalEvent = CalEvent(
            id = o.getString("id"),
            title = o.optString("title"),
            date = o.optString("date"),
            time = o.optString("time"),
            note = o.optString("note"),
            emoji = o.optString("emoji", "❤️"),
            yearly = o.optBoolean("yearly", false),
            by = o.optString("by"),
            updated = o.optLong("upd", 0L),
            deleted = o.optBoolean("del", false),
        )
    }
}

data class Note(val text: String, val ts: Long) {
    fun toJson(): JSONObject = JSONObject().put("text", text).put("ts", ts)

    companion object {
        fun fromJson(o: JSONObject): Note = Note(o.optString("text"), o.optLong("ts", 0L))
    }
}

data class AppState(
    val me: Person? = null,
    /** Sophies Antwort auf "Wie sehr liebst du David?" */
    val love: String = "",
    val setupDone: Boolean = false,
    /** Gemeinsames Geheimnis (Base64). Daraus werden Kanal und Schlüssel abgeleitet. */
    val secret: String? = null,
    val pairCode: String? = null,
    val pairCodeExpires: Long = 0L,
    val partnerConnected: Boolean = false,
    val events: Map<String, CalEvent> = emptyMap(),
    /** Autor-ID -> letzte Nachricht */
    val notes: Map<String, Note> = emptyMap(),
    val heartsReceived: List<Long> = emptyList(),
    val heartsSent: List<Long> = emptyList(),
    val unseenHearts: Int = 0,
    /** Unix-Sekunden der letzten empfangenen Nachricht */
    val lastSince: Long = 0L,
    val seenIds: List<String> = emptyList(),
    val deviceId: String = "",
    /** Noch nicht gesendete Nachrichten (Klartext-JSON, wird beim Senden verschlüsselt) */
    val outbox: List<String> = emptyList(),
    val lastHello: Long = 0L,
    val lastSnapshotSent: Long = 0L,
    val batteryTipDismissed: Boolean = false,
    /** Nur zur Laufzeit, wird nicht gespeichert */
    val connected: Boolean = false,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("me", me?.id ?: "")
        o.put("love", love)
        o.put("setupDone", setupDone)
        o.put("secret", secret ?: "")
        o.put("pairCode", pairCode ?: "")
        o.put("pairCodeExpires", pairCodeExpires)
        o.put("partnerConnected", partnerConnected)
        val evs = JSONArray()
        events.values.forEach { evs.put(it.toJson()) }
        o.put("events", evs)
        val notesObj = JSONObject()
        notes.forEach { (k, v) -> notesObj.put(k, v.toJson()) }
        o.put("notes", notesObj)
        o.put("heartsReceived", longArray(heartsReceived))
        o.put("heartsSent", longArray(heartsSent))
        o.put("unseenHearts", unseenHearts)
        o.put("lastSince", lastSince)
        o.put("seenIds", stringArray(seenIds))
        o.put("deviceId", deviceId)
        o.put("outbox", stringArray(outbox))
        o.put("lastHello", lastHello)
        o.put("lastSnapshotSent", lastSnapshotSent)
        o.put("batteryTipDismissed", batteryTipDismissed)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): AppState {
            val events = mutableMapOf<String, CalEvent>()
            val evs = o.optJSONArray("events")
            if (evs != null) {
                for (i in 0 until evs.length()) {
                    val obj = evs.optJSONObject(i) ?: continue
                    try {
                        val e = CalEvent.fromJson(obj)
                        events[e.id] = e
                    } catch (ex: Exception) {
                        // kaputten Eintrag überspringen
                    }
                }
            }
            val notes = mutableMapOf<String, Note>()
            val notesObj = o.optJSONObject("notes")
            if (notesObj != null) {
                for (k in notesObj.keys()) {
                    val n = notesObj.optJSONObject(k) ?: continue
                    notes[k] = Note.fromJson(n)
                }
            }
            return AppState(
                me = Person.fromId(o.optString("me")),
                love = o.optString("love"),
                setupDone = o.optBoolean("setupDone", false),
                secret = o.optString("secret").ifEmpty { null },
                pairCode = o.optString("pairCode").ifEmpty { null },
                pairCodeExpires = o.optLong("pairCodeExpires", 0L),
                partnerConnected = o.optBoolean("partnerConnected", false),
                events = events,
                notes = notes,
                heartsReceived = readLongs(o.optJSONArray("heartsReceived")),
                heartsSent = readLongs(o.optJSONArray("heartsSent")),
                unseenHearts = o.optInt("unseenHearts", 0),
                lastSince = o.optLong("lastSince", 0L),
                seenIds = readStrings(o.optJSONArray("seenIds")),
                deviceId = o.optString("deviceId"),
                outbox = readStrings(o.optJSONArray("outbox")),
                lastHello = o.optLong("lastHello", 0L),
                lastSnapshotSent = o.optLong("lastSnapshotSent", 0L),
                batteryTipDismissed = o.optBoolean("batteryTipDismissed", false),
            )
        }

        private fun longArray(list: List<Long>): JSONArray {
            val a = JSONArray()
            list.forEach { a.put(it) }
            return a
        }

        private fun stringArray(list: List<String>): JSONArray {
            val a = JSONArray()
            list.forEach { a.put(it) }
            return a
        }

        private fun readLongs(a: JSONArray?): List<Long> {
            if (a == null) return emptyList()
            val out = ArrayList<Long>(a.length())
            for (i in 0 until a.length()) out.add(a.optLong(i))
            return out
        }

        private fun readStrings(a: JSONArray?): List<String> {
            if (a == null) return emptyList()
            val out = ArrayList<String>(a.length())
            for (i in 0 until a.length()) out.add(a.optString(i))
            return out
        }
    }
}
