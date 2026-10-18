package com.familywardrobe.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Durable queue, sequential ML calls, and guarded commits into the original account only. */
class BatchImporter(private val context: Context, private val repo: Repository, private val photos: PhotoStore,
    private val analyzer: PhotoAnalyzer, private val nativeQueue: Mutex, private val scope: CoroutineScope,
    private val notify: (Int) -> Unit) {
    private val prefs = context.getSharedPreferences("preferences", 0)
    val paused = MutableStateFlow(prefs.getBoolean("batchPaused", true))
    val running = MutableStateFlow(false)
    val diagnostics = PhotoDiagnosticStore(context)
    private val gate = PhotoWorkGate()
    private var job: Job? = null

    suspend fun recover() {
        repo.writes.withLock {
            repo.dao.resetInterruptedImports()
            diagnostics.retain(repo.dao.allImports().map { it.id }.toSet())
        }
        if (!paused.value) resume()
    }
    suspend fun enqueue(wardrobe: String, uris: List<Uri>) {
        uris.take(20).forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        repo.enqueueImports(wardrobe, uris.take(20).map { it.toString() })
        resume()
    }
    suspend fun enqueueCapture(wardrobe: String, uri: Uri) {
        var created: String? = null
        try {
            val id = photos.import(uri); created = id
            val fingerprint = photos.fingerprint(id)
            repo.enqueueCapturedImport(wardrobe, id, fingerprint, context.getString(R.string.batch_item_name, repo.dao.allImports().count { it.wardrobeId == wardrobe } + 1))
            created = null; resume()
        } finally {
            created?.let { id -> withContext(NonCancellable) { repo.discardPhotos(setOf(id), photos) } }
        }
    }
    fun pause() { paused.value = true; gate.cancel(); prefs.edit().putBoolean("batchPaused", true).commit() }
    fun resume() {
        paused.value = false; prefs.edit().putBoolean("batchPaused", false).commit()
        if (job?.isActive == true) return
        job = scope.launch {
            running.value = true
            try {
                while (!paused.value) {
                    val next = repo.dao.allImports().firstOrNull { it.status == "queued" } ?: break
                    nativeQueue.withLock { if (!paused.value) process(next) }
                }
            } finally { running.value = false }
        }
    }
    suspend fun retry(id: String) {
        repo.writes.withLock {
            repo.dao.importDraft(id)?.let { row ->
                require(row.status != "working")
                repo.dao.saveImport(row.copy(status = "queued"))
                diagnostics.remove(id)
            }
        }
        resume()
    }
    private suspend fun process(initial: ImportDraft) {
        val token = gate.begin("batch:${initial.id}", initial.id)
        var createdImport: String? = null
        var variant: String? = null
        var analysis: PhotoAnalysis? = null
        try {
            var row = repo.writes.withLock {
                repo.dao.importDraft(initial.id)?.takeIf { it.status == "queued" }?.copy(status = "working")?.also { repo.dao.saveImport(it) }
            } ?: return
            if (row.photoId.isBlank()) {
                val source = photos.import(Uri.parse(row.inputUri))
                createdImport = source
                val fingerprint = photos.fingerprint(source)
                row = repo.writes.withLock {
                    val current = repo.dao.importDraft(row.id) ?: return@withLock null
                    current.copy(photoId = source, originalPhotoId = source, inputUri = "", photoFingerprint = fingerprint,
                        name = current.name.ifBlank { context.getString(R.string.batch_item_name, current.position + 1) },
                        status = if (paused.value) "queued" else "working").also { repo.dao.saveImport(it) }
                } ?: return
            }
            if (paused.value || !gate.accepts(token, "batch:${row.id}", row.id)) return
            val result = analyzer.analyze(row.photoId)
            analysis = result
            if (paused.value || !gate.accepts(token, "batch:${row.id}", row.id)) return
            val candidate = result.cutoutPath?.let(::File)
            if (candidate != null && result.cutoutStatus == CutoutStatus.READY) variant = photos.copyVariant(row.photoId, candidate)
            repo.writes.withLock {
                val current = repo.dao.importDraft(row.id)
                if (current != null && current.status == "working" && !paused.value && gate.accepts(token, "batch:${row.id}", row.id)) {
                    repo.dao.saveImport(current.copy(photoId = variant ?: current.photoId,
                        category = current.category.ifBlank { result.category?.takeIf { it in Matching.categories }.orEmpty() },
                        color = result.color?.takeIf { it in Matching.colors } ?: current.color, status = "review"))
                    diagnostics.record(row.id, result.copy(sourceId = variant ?: current.photoId))
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            repo.writes.withLock {
                repo.dao.importDraft(initial.id)?.let { row ->
                    repo.dao.saveImport(row.copy(status = if (row.photoId.isBlank()) "failed" else "review"))
                    diagnostics.record(row.id, photoFailure(row.photoId, e))
                }
            }
        } finally {
            analysis?.cutoutPath?.let { File(it).delete() }
            withContext(NonCancellable) {
                repo.writes.withLock {
                    repo.dao.importDraft(initial.id)?.takeIf { it.status == "working" }?.let { repo.dao.saveImport(it.copy(status = "queued")) }
                }
                val candidates = listOfNotNull(createdImport, variant).toSet()
                if (candidates.isNotEmpty() && !repo.discardPhotos(candidates, photos)) notify(R.string.photo_cleanup_failed)
            }
        }
    }
}
