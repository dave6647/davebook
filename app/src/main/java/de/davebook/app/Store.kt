package de.davebook.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Der lokale Zustand der App. Liegt als JSON im App-Speicher,
 * wird bei jeder Änderung gespeichert und an die Oberfläche gemeldet.
 */
object Store {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _incomingHearts = MutableSharedFlow<Person>(extraBufferCapacity = 16)
    val incomingHearts: SharedFlow<Person> = _incomingHearts.asSharedFlow()

    /** Wird bei jedem Zurückkehren in die App erhöht (z. B. um Einstellungen neu zu prüfen). */
    val resumeTick = MutableStateFlow(0)

    @Volatile
    var appVisible = false

    private lateinit var appContext: Context
    private lateinit var file: File
    private val saveSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var initialized = false

    val context: Context get() = appContext

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        file = File(appContext.filesDir, "state.json")
        var s = load()
        if (s.deviceId.isEmpty()) s = s.copy(deviceId = UUID.randomUUID().toString())
        if (!s.reminders.containsKey(Reminder.DRINK_ID)) {
            val drink = Reminder.defaultDrink(System.currentTimeMillis())
            s = s.copy(reminders = s.reminders + (drink.id to drink))
        }
        _state.value = s.copy(connected = false)
        scope.launch {
            for (signal in saveSignal) writeNow()
        }
        saveSignal.trySend(Unit)
        Sync.start()
        // Erinnerungen neu planen, sobald sich Rolle, Einrichtung oder Erinnerungen ändern
        scope.launch {
            state
                .map { Triple(it.me, it.setupDone, it.reminders) }
                .distinctUntilChanged()
                .collect { Reminders.reschedule(appContext) }
        }
    }

    private fun load(): AppState = try {
        if (file.exists()) AppState.fromJson(JSONObject(file.readText())) else AppState()
    } catch (e: Exception) {
        AppState()
    }

    private fun writeNow() {
        try {
            val tmp = File(file.parentFile, "state.json.tmp")
            tmp.writeText(_state.value.toJson().toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            // Speichern fehlgeschlagen, beim nächsten Mal wieder versuchen
        }
    }

    fun update(f: (AppState) -> AppState) {
        _state.update(f)
        saveSignal.trySend(Unit)
    }

    // ---------------------------------------------------------------- Einrichtung

    fun setMe(p: Person) = update { it.copy(me = p) }

    fun setLove(value: String) {
        update { it.copy(love = value) }
        if (_state.value.secret != null) {
            enqueue(JSONObject().put("t", "love").put("v", value))
        }
    }

    fun finishSetup() = update { it.copy(setupDone = true) }

    fun setSecret(secret: String) {
        val changed = _state.value.secret != secret
        if (!changed) return
        update {
            it.copy(
                secret = secret,
                lastSince = 0L,
                seenIds = emptyList(),
                partnerConnected = false,
                pairCode = null,
                pairCodeExpires = 0L,
            )
        }
        Sync.restart()
    }

    fun setConnected(c: Boolean) {
        _state.update { if (it.connected == c) it else it.copy(connected = c) }
    }

    fun markSeen(id: String, time: Long) = update { s ->
        val ids = if (id.isEmpty() || s.seenIds.contains(id)) s.seenIds else (s.seenIds + id).takeLast(300)
        s.copy(seenIds = ids, lastSince = maxOf(s.lastSince, time))
    }

    fun dismissBatteryTip() = update { it.copy(batteryTipDismissed = true) }

    fun clearUnseen() = update { if (it.unseenHearts == 0) it else it.copy(unseenHearts = 0) }

    // ---------------------------------------------------------------- Senden

    fun enqueue(payload: JSONObject) {
        val s = _state.value
        val me = s.me ?: return
        payload.put("from", me.id)
        payload.put("dev", s.deviceId)
        if (!payload.has("ts")) payload.put("ts", System.currentTimeMillis())
        val str = payload.toString()
        update { it.copy(outbox = (it.outbox + str).takeLast(200)) }
        Sync.flush()
    }

    fun outboxSent(item: String) = update { s ->
        val i = s.outbox.indexOf(item)
        if (i < 0) s else s.copy(outbox = s.outbox.filterIndexed { idx, _ -> idx != i })
    }

    fun sendHeart() {
        val now = System.currentTimeMillis()
        update { it.copy(heartsSent = (it.heartsSent + now).takeLast(300)) }
        enqueue(JSONObject().put("t", "heart"))
    }

    fun setMyNote(text: String) {
        val me = _state.value.me ?: return
        val n = Note(text.trim(), System.currentTimeMillis())
        update { it.copy(notes = it.notes + (me.id to n)) }
        enqueue(JSONObject().put("t", "note").put("text", n.text).put("nts", n.ts))
    }

    fun saveEvent(e: CalEvent) {
        val ev = e.copy(updated = System.currentTimeMillis())
        update { it.copy(events = it.events + (ev.id to ev)) }
        enqueue(JSONObject().put("t", "ev").put("e", ev.toJson()))
    }

    fun deleteEvent(e: CalEvent) = saveEvent(e.copy(deleted = true))

    fun saveReminder(r: Reminder) {
        val rem = r.copy(updated = System.currentTimeMillis())
        update { it.copy(reminders = it.reminders + (rem.id to rem)) }
        enqueue(JSONObject().put("t", "rem").put("r", rem.toJson()))
    }

    fun deleteReminder(r: Reminder) = saveReminder(r.copy(deleted = true, enabled = false))

    fun markReminderFired(id: String, time: Long) =
        update { it.copy(reminderLastFired = it.reminderLastFired + (id to time)) }

    // ---------------------------------------------------------------- Empfangen

    fun handleIncoming(p: JSONObject) {
        val s = _state.value
        if (p.optString("dev") == s.deviceId) return
        val from = Person.fromId(p.optString("from")) ?: return
        val me = s.me
        val fromPartner = me != null && from == me.partner
        val now = System.currentTimeMillis()
        val ts = p.optLong("ts", now)
        val recent = now - ts < 12L * 3600 * 1000

        if (fromPartner && !s.partnerConnected) update { it.copy(partnerConnected = true) }

        when (p.optString("t")) {
            "heart" -> {
                if (!fromPartner) return
                var count = 0
                val visible = appVisible
                update { st ->
                    val unseen = if (visible) 0 else st.unseenHearts + 1
                    count = unseen
                    st.copy(heartsReceived = (st.heartsReceived + ts).takeLast(300), unseenHearts = unseen)
                }
                if (visible) {
                    _incomingHearts.tryEmit(from)
                } else if (recent) {
                    Notifier.heart(appContext, from, count)
                }
            }

            "note" -> {
                val n = Note(p.optString("text"), p.optLong("nts", ts))
                var isNew = false
                update { st ->
                    val old = st.notes[from.id]
                    isNew = old == null || n.ts > old.ts
                    if (isNew) st.copy(notes = st.notes + (from.id to n)) else st
                }
                if (isNew && fromPartner && recent && n.text.isNotBlank()) {
                    Notifier.note(appContext, from, n.text)
                }
            }

            "ev" -> {
                val obj = p.optJSONObject("e") ?: return
                val e = try {
                    CalEvent.fromJson(obj)
                } catch (ex: Exception) {
                    return
                }
                var created = false
                update { st ->
                    val old = st.events[e.id]
                    created = old == null && !e.deleted
                    if (old == null || e.updated > old.updated) st.copy(events = st.events + (e.id to e)) else st
                }
                if (created && fromPartner && recent) Notifier.event(appContext, from, e)
            }

            "rem" -> {
                val obj = p.optJSONObject("r") ?: return
                val r = try {
                    Reminder.fromJson(obj)
                } catch (ex: Exception) {
                    return
                }
                update { st ->
                    val old = st.reminders[r.id]
                    if (old == null || r.updated > old.updated) st.copy(reminders = st.reminders + (r.id to r)) else st
                }
            }

            "love" -> {
                if (from == Person.SOPHIE) {
                    val v = p.optString("v")
                    if (v.isNotEmpty()) update { it.copy(love = v) }
                }
            }

            "hello" -> {
                if (now - _state.value.lastSnapshotSent > 30_000L) Sync.sendSnapshot()
            }

            "snap" -> mergeSnapshot(from, p)
        }
    }

    private fun mergeSnapshot(from: Person, p: JSONObject) {
        val evs = p.optJSONArray("evs")
        val rems = p.optJSONArray("rems")
        val notesObj = p.optJSONObject("notes")
        val love = p.optString("love")
        update { st ->
            var reminders = st.reminders
            if (rems != null) {
                for (i in 0 until rems.length()) {
                    val obj = rems.optJSONObject(i) ?: continue
                    val r = try {
                        Reminder.fromJson(obj)
                    } catch (ex: Exception) {
                        continue
                    }
                    val old = reminders[r.id]
                    if (old == null || r.updated > old.updated) reminders = reminders + (r.id to r)
                }
            }
            var events = st.events
            if (evs != null) {
                for (i in 0 until evs.length()) {
                    val obj = evs.optJSONObject(i) ?: continue
                    val e = try {
                        CalEvent.fromJson(obj)
                    } catch (ex: Exception) {
                        continue
                    }
                    val old = events[e.id]
                    if (old == null || e.updated > old.updated) events = events + (e.id to e)
                }
            }
            var notes = st.notes
            if (notesObj != null) {
                for (k in notesObj.keys()) {
                    val obj = notesObj.optJSONObject(k) ?: continue
                    val n = Note.fromJson(obj)
                    val old = notes[k]
                    if (old == null || n.ts > old.ts) notes = notes + (k to n)
                }
            }
            val newLove = if (from == Person.SOPHIE && love.isNotEmpty()) love else st.love
            st.copy(events = events, notes = notes, love = newLove, reminders = reminders)
        }
    }
}
