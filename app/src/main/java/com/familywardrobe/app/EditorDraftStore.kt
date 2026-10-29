package com.familywardrobe.app
import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Receives AccountContext; never touches authentication credentials or another account's preferences.
 *
 * The draft (up to hundreds of KB with photo-editor history) lives in its own account-private file and is
 * written by one background thread, so typing never waits for disk (audit A-01). Writes are coalesced:
 * only the latest value is persisted. Every reader in this process sees the latest accepted value at once,
 * so "a draft exists" checks (cloud apply) stay exact. The small cleanup ledger is committed synchronously
 * before the draft is queued: a photo referenced by a durable draft is always recoverable by cleanup.
 * A queued draft object must not be mutated after write(); callers publish new objects instead.
 */
class EditorDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", 0)
    private val file = AtomicFile(File(context.filesDir, FILE))
    private val path = file.baseFile.absolutePath
    /** Increments after a background write fails; the newest draft stays readable in this process. */
    val failures: StateFlow<Long> get() = synchronized(lock) { signals.getOrPut(path) { MutableStateFlow(0L) } }

    fun read(): JSONObject? {
        val queued = synchronized(lock) { pending[path] }
        val text = if (queued != null) queued.draft?.toString() ?: return null else stored() ?: return null
        return runCatching { JSONObject(text).takeIf(::valid) }.getOrNull()
    }
    fun write(value: JSONObject): Boolean {
        // The source cleanup ledger is committed before the command draft can become durable.
        val photos = PhotoSessionDraft.referencedPhotos(value.optJSONObject("fields"))
        val ledger = prefs.getStringSet("draftPhotos", emptySet()).orEmpty()
        if (!ledger.containsAll(photos) && !prefs.edit().putStringSet("draftPhotos", ledger + photos).commit()) return false
        enqueue(Pending(value))
        return true
    }
    fun clear(): Boolean { enqueue(Pending(null)); return true }

    private fun stored(): String? = runCatching {
        if (file.baseFile.exists() || File("$path.bak").exists()) file.readFully().toString(Charsets.UTF_8)
        else prefs.getString(LEGACY, null)
    }.getOrNull()
    private fun valid(it: JSONObject) =
        it.optString("kind") in setOf("garment", "wish", "look", "purchase", "import", "shop") && PhotoStore.ID.matches(it.optString("wardrobe")) &&
            it.optJSONObject("fields") != null
    private fun enqueue(next: Pending) {
        val start = synchronized(lock) { pending[path] = next; scheduled.add(path) }
        if (start) writer.execute { drain(file, prefs, path) }
    }

    private class Pending(val draft: JSONObject?)
    companion object {
        private const val FILE = "editor-draft.json"
        private const val LEGACY = "editorDraft"
        private val lock = Any()
        private val pending = HashMap<String, Pending>()
        private val scheduled = HashSet<String>()
        private val signals = HashMap<String, MutableStateFlow<Long>>()
        private val writer = Executors.newSingleThreadExecutor { Thread(it, "editor-draft").apply { isDaemon = true } }

        private fun drain(file: AtomicFile, prefs: SharedPreferences, path: String) {
            while (true) {
                val next = synchronized(lock) { pending[path] ?: run { scheduled.remove(path); return } }
                val written = runCatching { persist(file, prefs, next) }.isSuccess
                synchronized(lock) {
                    if (written) { if (pending[path] === next) pending.remove(path) }
                    else {
                        // Keep the newest value readable; the next write retries instead of spinning here.
                        scheduled.remove(path)
                        signals.getOrPut(path) { MutableStateFlow(0L) }.value += 1
                        return
                    }
                }
            }
        }
        private fun persist(file: AtomicFile, prefs: SharedPreferences, next: Pending) {
            val draft = next.draft
            if (draft == null) file.delete()
            else {
                val bytes = draft.toString().toByteArray(Charsets.UTF_8)
                file.baseFile.parentFile?.let { check(it.mkdirs() || it.isDirectory) }
                val output = file.startWrite()
                try { output.write(bytes); file.finishWrite(output) } catch (e: Exception) { file.failWrite(output); throw e }
            }
            // A draft from older versions lived inside the preferences file; it is retired once replaced.
            if (prefs.contains(LEGACY)) check(prefs.edit().remove(LEGACY).commit())
        }
    }
}
