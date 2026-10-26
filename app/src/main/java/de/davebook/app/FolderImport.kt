package de.davebook.app

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID

/**
 * Übernimmt Termine aus Ordnern auf diesem Handy, z. B. dem Events-Ordner eines Obsidian-Tresors
 * (Plugin „Full Calendar“, eine Notiz pro Termin).
 *
 * Der Ordner wird über die Android-Ordnerauswahl freigegeben (nur lesen). Davebook liest ihn beim
 * Öffnen der App und alle 15 Minuten im Hintergrund neu ein: Neue und geänderte Notizen werden zu
 * Terminen, gelöschte Notizen verschwinden. Wird der Ordner entfernt, verschwinden alle Termine daraus,
 * im Ordner selbst ändert Davebook nie etwas. Die Termine gehen wie alle anderen auch an das andere Handy.
 */
object FolderImport {
    private const val SCAN_EVERY_MS = 15L * 60 * 1000
    private const val RESUME_MIN_GAP_MS = 60_000L
    private const val MAX_FILES = 3000
    private const val MAX_DEPTH = 8
    const val EMOJI = "📅"

    private val mutex = Mutex()

    /** Dokument-Adresse → (zuletzt geändert, Ergebnis). So werden unveränderte Notizen nicht neu gelesen. */
    private val cache = HashMap<String, Pair<Long, EventNotes.Result>>()

    @Volatile
    private var lastScan = 0L
    private var started = false

    private val _busy = MutableStateFlow(false)

    /** true, solange gerade eingelesen wird */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun start() {
        if (started) return
        started = true
        Store.scope.launch {
            delay(20_000L)
            while (true) {
                scanAll()
                delay(SCAN_EVERY_MS)
            }
        }
    }

    /** Beim Öffnen der App neu einlesen (höchstens einmal pro Minute). */
    fun onResume() {
        if (Store.state.value.importFolders.isEmpty()) return
        if (System.currentTimeMillis() - lastScan < RESUME_MIN_GAP_MS) return
        refresh()
    }

    fun refresh() {
        Store.scope.launch { scanAll() }
    }

    /** Verknüpft den Ordner, der in der Android-Ordnerauswahl gewählt wurde. */
    fun add(ctx: Context, tree: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Ohne dauerhafte Freigabe klappt das Lesen nur bis zum nächsten Neustart; der Ordner zeigt dann einen Hinweis
        }
        val existing = Store.state.value.importFolders.firstOrNull { it.uri == tree.toString() }
        if (existing == null) {
            val id = UUID.randomUUID().toString().replace("-", "").take(10)
            Store.addImportFolder(ImportFolder(id = id, uri = tree.toString(), name = folderName(ctx, tree)))
        }
        refresh()
    }

    /** Entfernt den Ordner und alle Termine, die aus ihm stammen (bei beiden). Im Ordner selbst bleibt alles. */
    fun remove(ctx: Context, id: String) {
        val f = Store.state.value.importFolders.firstOrNull { it.id == id } ?: return
        Store.removeImportFolder(id)
        if (Store.state.value.importFolders.none { it.uri == f.uri }) {
            try {
                ctx.contentResolver.releasePersistableUriPermission(Uri.parse(f.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // war schon weg
            }
        }
    }

    /** Pfad zum Anzeigen, z. B. „Documents/Events & Reisen/Events“ (nur bei normalem Gerätespeicher). */
    fun pathOf(uri: String): String? {
        val treeId = try {
            DocumentsContract.getTreeDocumentId(Uri.parse(uri))
        } catch (e: Exception) {
            return null
        }
        val path = treeId.substringAfter(':', "")
        if (path.isEmpty() || path.all { it.isDigit() }) return null
        return path
    }

    // ------------------------------------------------------------------ Einlesen

    suspend fun scanAll() {
        mutex.withLock {
            val s = Store.state.value
            if (s.importFolders.isEmpty() || s.me == null) return
            _busy.value = true
            try {
                val ctx = Store.context
                for (f in s.importFolders) scanFolder(ctx, f)
            } finally {
                lastScan = System.currentTimeMillis()
                _busy.value = false
            }
        }
    }

    private fun scanFolder(ctx: Context, f: ImportFolder) {
        val me = Store.state.value.me ?: return
        val tree = Uri.parse(f.uri)
        val cr = ctx.contentResolver
        val found = HashMap<String, CalEvent>()
        val keep = HashSet<String>()
        var skipped = 0
        try {
            val files = ArrayList<Child>()
            collect(cr, tree, DocumentsContract.getTreeDocumentId(tree), 0, files)
            for (file in files) {
                val id = eventId(f.id, file.docId)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, file.docId)
                val key = docUri.toString()
                val cached = cache[key]
                val result = if (cached != null && file.modified != 0L && cached.first == file.modified) {
                    cached.second
                } else {
                    try {
                        read(cr, docUri, file.name).also { cache[key] = file.modified to it }
                    } catch (e: Exception) {
                        // Gerade nicht lesbar (z. B. wird noch synchronisiert): bisherigen Stand behalten
                        keep.add(id)
                        continue
                    }
                }
                when (result) {
                    is EventNotes.Result.Event -> found[id] = CalEvent(
                        id = id,
                        title = result.event.title,
                        date = result.event.date,
                        time = result.event.time,
                        note = "",
                        emoji = EMOJI,
                        yearly = false,
                        by = me.id,
                        updated = 0L,
                        deleted = false,
                        endDate = result.event.endDate,
                        source = f.id,
                    )

                    EventNotes.Result.Recurring -> skipped++
                    EventNotes.Result.None -> Unit
                }
            }
        } catch (e: SecurityException) {
            Store.updateImportFolder(f.id) {
                it.copy(error = "Kein Zugriff mehr auf den Ordner. Bitte entfernen und neu hinzufügen.")
            }
            return
        } catch (e: Exception) {
            // Nichts löschen, wenn der Ordner gerade nicht lesbar ist (z. B. Speicherkarte weg)
            Store.updateImportFolder(f.id) { it.copy(error = "Der Ordner ist gerade nicht lesbar.") }
            return
        }
        Store.applyImport(f.id, found, keep, skipped)
    }

    private fun eventId(folderId: String, docId: String): String =
        "imp-" + Crypto.hex(Crypto.sha256("$folderId\u0000$docId")).take(20)

    private data class Child(val docId: String, val name: String, val mime: String, val modified: Long)

    private fun children(cr: ContentResolver, tree: Uri, parentId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
        )
        val out = ArrayList<Child>()
        val c = cr.query(uri, cols, null, null, null) ?: throw IOException("Ordner nicht lesbar")
        c.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                out.add(
                    Child(
                        docId = id,
                        name = it.getString(1) ?: "",
                        mime = it.getString(2) ?: "",
                        modified = if (it.isNull(3)) 0L else it.getLong(3),
                    ),
                )
            }
        }
        return out
    }

    private fun collect(cr: ContentResolver, tree: Uri, parentId: String, depth: Int, out: MutableList<Child>) {
        for (child in children(cr, tree, parentId)) {
            if (out.size >= MAX_FILES) return
            // .obsidian, .trash und andere versteckte Ordner/Dateien auslassen
            if (child.name.startsWith(".")) continue
            if (child.mime == Document.MIME_TYPE_DIR) {
                if (depth < MAX_DEPTH) collect(cr, tree, child.docId, depth + 1, out)
            } else if (child.name.endsWith(".md", ignoreCase = true)) {
                out.add(child)
            }
        }
    }

    private fun read(cr: ContentResolver, uri: Uri, name: String): EventNotes.Result {
        val input = cr.openInputStream(uri) ?: throw IOException("Datei nicht lesbar")
        return input.bufferedReader(Charsets.UTF_8).use { r ->
            EventNotes.parse(name, EventNotes.frontmatter(r.lineSequence().iterator()))
        }
    }

    private fun folderName(ctx: Context, tree: Uri): String {
        try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            ctx.contentResolver.query(docUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (e: Exception) {
            // unten Ersatzname
        }
        return pathOf(tree.toString())?.substringAfterLast('/')?.ifEmpty { null } ?: "Ordner"
    }
}
