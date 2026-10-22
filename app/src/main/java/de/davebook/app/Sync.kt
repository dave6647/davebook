package de.davebook.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Abgleich zwischen den beiden Handys über ntfy.sh.
 *
 * Jede Nachricht (Herz, Spruch, Termin …) wird verschlüsselt an einen geheimen Kanal geschickt.
 * Der Hintergrunddienst hält eine Verbindung zu diesem Kanal offen und bekommt neue Nachrichten sofort.
 * ntfy.sh hebt Nachrichten 12 Stunden auf; wer länger offline war, bekommt beim nächsten
 * Verbinden einen kompletten Abgleich vom anderen Handy.
 */
object Sync {
    private const val BASE = "https://ntfy.sh"
    private const val UA = "Davebook/1.0"

    @Volatile
    private var conn: HttpURLConnection? = null

    @Volatile
    private var openedThisRound = false

    private val flushSignal = Channel<Unit>(Channel.CONFLATED)
    private var started = false

    private enum class PubResult { OK, RETRY, DROP }

    fun topic(secret: String): String = "davebook_" + Crypto.hex(Crypto.sha256("davebook-topic:$secret")).take(32)

    private fun pairTopic(code: String): String = "davebook_pair_" + Crypto.hex(Crypto.sha256("davebook-pair:$code")).take(24)

    fun start() {
        if (started) return
        started = true
        Store.scope.launch {
            for (signal in flushSignal) drain()
        }
        flush()
    }

    fun flush() {
        flushSignal.trySend(Unit)
    }

    /** Bricht die laufende Verbindung ab, damit sie (z. B. mit neuem Geheimnis) neu aufgebaut wird. */
    fun restart() {
        try {
            conn?.disconnect()
        } catch (e: Exception) {
            // egal
        }
        flush()
    }

    // ------------------------------------------------------------------ Senden

    private fun drain() {
        while (true) {
            val s = Store.state.value
            val secret = s.secret ?: return
            val item = s.outbox.firstOrNull() ?: return
            val body = Crypto.encrypt(Crypto.mainKey(secret), item)
            when (post(topic(secret), body)) {
                PubResult.OK, PubResult.DROP -> Store.outboxSent(item)
                PubResult.RETRY -> {
                    Store.scope.launch {
                        delay(20_000L)
                        flush()
                    }
                    return
                }
            }
        }
    }

    private fun post(topic: String, body: String): PubResult {
        val c = try {
            URL("$BASE/$topic").openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return PubResult.RETRY
        }
        return try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            c.setRequestProperty("Firebase", "no")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            when {
                code in 200..299 -> PubResult.OK
                code == 429 || code >= 500 -> PubResult.RETRY
                else -> PubResult.DROP
            }
        } catch (e: Exception) {
            PubResult.RETRY
        } finally {
            c.disconnect()
        }
    }

    fun maybeHello(force: Boolean = false) {
        val s = Store.state.value
        if (s.secret == null || s.me == null) return
        val now = System.currentTimeMillis()
        if (!force && now - s.lastHello < 6L * 3600 * 1000) return
        Store.update { it.copy(lastHello = now) }
        Store.enqueue(JSONObject().put("t", "hello"))
        sendSnapshot()
    }

    /** Schickt den kompletten eigenen Stand (Termine, Sprüche) in kleinen Paketen. */
    fun sendSnapshot() {
        val s = Store.state.value
        val me = s.me ?: return
        if (s.secret == null) return
        val now = System.currentTimeMillis()
        Store.update { it.copy(lastSnapshotSent = now) }

        val head = JSONObject().put("t", "snap")
        val notesObj = JSONObject()
        s.notes.forEach { (k, v) -> notesObj.put(k, v.toJson()) }
        head.put("notes", notesObj)
        if (me == Person.SOPHIE && s.love.isNotEmpty()) head.put("love", s.love)
        Store.enqueue(head)

        var chunk = JSONArray()
        var size = 0
        for (e in s.events.values) {
            if (e.deleted && now - e.updated > 30L * 24 * 3600 * 1000) continue
            val js = e.toJson()
            val len = js.toString().toByteArray(Charsets.UTF_8).size
            if (size + len > 1800 && chunk.length() > 0) {
                Store.enqueue(JSONObject().put("t", "snap").put("evs", chunk))
                chunk = JSONArray()
                size = 0
            }
            chunk.put(js)
            size += len + 1
        }
        if (chunk.length() > 0) Store.enqueue(JSONObject().put("t", "snap").put("evs", chunk))

        // Erinnerungen genauso in kleinen Paketen (die unveränderte Standard-Erinnerung muss nicht mit)
        var remChunk = JSONArray()
        var remSize = 0
        for (r in s.reminders.values) {
            if (r.updated == 0L) continue
            if (r.deleted && now - r.updated > 30L * 24 * 3600 * 1000) continue
            val js = r.toJson()
            val len = js.toString().toByteArray(Charsets.UTF_8).size
            if (remSize + len > 1800 && remChunk.length() > 0) {
                Store.enqueue(JSONObject().put("t", "snap").put("rems", remChunk))
                remChunk = JSONArray()
                remSize = 0
            }
            remChunk.put(js)
            remSize += len + 1
        }
        if (remChunk.length() > 0) Store.enqueue(JSONObject().put("t", "snap").put("rems", remChunk))
    }

    // ------------------------------------------------------------------ Empfangen

    /** Läuft im Hintergrunddienst, solange er aktiv ist. */
    suspend fun runLoop() {
        var backoff = 2_000L
        while (currentCoroutineContext().isActive) {
            val secret = Store.state.value.secret
            if (secret == null) {
                delay(5_000L)
                continue
            }
            openedThisRound = false
            try {
                stream(secret)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Verbindung weg, gleich neu versuchen
            }
            Store.setConnected(false)
            if (openedThisRound) backoff = 2_000L
            delay(backoff)
            if (!openedThisRound) backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    private fun stream(secret: String) {
        val st = Store.state.value
        val since = if (st.lastSince > 0) st.lastSince.toString() else "all"
        val key = Crypto.mainKey(secret)
        val c = URL("$BASE/${topic(secret)}/json?since=$since").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 100_000
        c.setRequestProperty("User-Agent", UA)
        conn = c
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    if (Store.state.value.secret != secret) break
                    val o = try {
                        JSONObject(line)
                    } catch (e: Exception) {
                        continue
                    }
                    when (o.optString("event")) {
                        "open" -> {
                            openedThisRound = true
                            Store.setConnected(true)
                            flush()
                        }

                        "message" -> handleMessage(key, o)
                    }
                }
            }
        } finally {
            conn = null
            c.disconnect()
        }
    }

    private fun handleMessage(key: javax.crypto.spec.SecretKeySpec, o: JSONObject) {
        val id = o.optString("id")
        val time = o.optLong("time", 0L)
        if (id.isNotEmpty() && Store.state.value.seenIds.contains(id)) {
            Store.markSeen(id, time)
            return
        }
        val plain = Crypto.decrypt(key, o.optString("message"))
        if (plain != null) {
            try {
                Store.handleIncoming(JSONObject(plain))
            } catch (e: Exception) {
                // unbekanntes Format ignorieren
            }
        }
        Store.markSeen(id, time)
    }

    // ------------------------------------------------------------------ Koppeln

    /** Erzeugt einen 6-stelligen Code, über den das andere Handy das Geheimnis abholen kann. */
    suspend fun createPairCode(): String = withContext(Dispatchers.IO) {
        val s0 = Store.state.value
        val me = s0.me ?: throw IllegalStateException("Rolle fehlt")
        val secret = s0.secret ?: Crypto.newSecret().also { Store.setSecret(it) }
        val code = Crypto.newPairCode()
        val payload = JSONObject().put("t", "pair").put("s", secret).put("from", me.id)
        val body = Crypto.encrypt(Crypto.pairKey(code), payload.toString())
        if (post(pairTopic(code), body) != PubResult.OK) throw IOException("Senden fehlgeschlagen")
        Store.update { it.copy(pairCode = code, pairCodeExpires = System.currentTimeMillis() + 11L * 3600 * 1000) }
        val love = Store.state.value.love
        if (me == Person.SOPHIE && love.isNotEmpty()) {
            Store.enqueue(JSONObject().put("t", "love").put("v", love))
        }
        flush()
        code
    }

    /** Holt mit dem Code das Geheimnis vom anderen Handy. true = gekoppelt. */
    suspend fun joinWithCode(raw: String): Boolean = withContext(Dispatchers.IO) {
        val code = raw.filter { it.isDigit() }
        if (code.length != 6) return@withContext false
        val key = Crypto.pairKey(code)
        val c = URL("$BASE/${pairTopic(code)}/json?poll=1&since=all").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", UA)
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            val lines = c.inputStream.bufferedReader(Charsets.UTF_8).readLines()
            var found: String? = null
            for (line in lines.asReversed()) {
                if (line.isBlank()) continue
                val o = try {
                    JSONObject(line)
                } catch (e: Exception) {
                    continue
                }
                if (o.optString("event") != "message") continue
                val plain = Crypto.decrypt(key, o.optString("message")) ?: continue
                val p = try {
                    JSONObject(plain)
                } catch (e: Exception) {
                    continue
                }
                if (p.optString("t") != "pair") continue
                val secret = p.optString("s")
                if (secret.isEmpty()) continue
                found = secret
                break
            }
            val secret = found ?: return@withContext false
            Store.setSecret(secret)
            maybeHello(force = true)
            SyncService.start(Store.context)
            true
        } finally {
            c.disconnect()
        }
    }
}
