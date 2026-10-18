package com.familywardrobe.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** One candidate at a time, explicit review, no edits to garment metadata or recognition fields. */
internal class PhotoReprocessor(private val store: PhotoReprocessStore, private val repo: Repository,
    private val photos: PhotoStore, private val analyzer: PhotoAnalysisEngine, private val nativeQueue: Mutex,
    private val scope: CoroutineScope, private val notify: (Int) -> Unit) {
    val state = store.state
    val running = MutableStateFlow(false)
    private val gate = PhotoWorkGate()
    private var job: Job? = null
    init { repo.processingPhotos = { state.value.photos() } }

    suspend fun begin(wardrobe: String, ids: List<String>) {
        repo.writes.withLock {
            check(!state.value.unreadable && state.value.entries.isEmpty() && !running.value)
            require(ids.isNotEmpty() && ids.distinct().size <= 500)
            val byId = repo.dao.allGarments().associateBy { it.id }
            val entries = ids.distinct().mapNotNull { id -> byId[id]?.takeIf { it.wardrobeId == wardrobe }
                ?.let { PhotoReprocessEntry(it.id, it.wardrobeId, it.photoId) } }
            require(entries.size == ids.distinct().size && entries.all { PhotoStore.ID.matches(it.sourceId) })
            store.write(PhotoReprocessState(newId(), entries, retiredIds = state.value.retiredIds))
            drainRetiredLocked()
        }
        prepare()
    }
    suspend fun recover() {
        repo.writes.withLock {
            drainRetiredLocked()
            val entry = state.value.entries.firstOrNull() ?: return@withLock
            val current = repo.dao.garment(entry.itemId)
            // A crash after Room committed but before queue advancement must not save twice.
            if (entry.candidateId.isNotBlank() && current != null && current.photoId == entry.candidateId && current.wardrobeId == entry.wardrobeId) {
                advanceLocked(saved = true)
            } else if (current == null || current.wardrobeId != entry.wardrobeId || current.photoId != entry.sourceId) {
                replaceLocked(entry.copy(status = "conflict"))
            } else if (entry.candidateId.isNotBlank() && entry.status == "pending") {
                replaceLocked(entry.copy(candidateId = "")); drainRetiredLocked()
            } else if (entry.status == "review") {
                try { withContext(Dispatchers.IO) { photos.validateImage(photos.currentFile(entry.candidateId)) } }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    replaceLocked(entry.copy(status = "failed", candidateId = "", failure = photoFailure(entry.sourceId, e).failure))
                    drainRetiredLocked()
                }
            }
        }
        // Recovery deliberately waits for an explicit Resume; it never starts ML on login.
    }
    fun pause() { gate.cancel() }
    fun prepare(stronger: Boolean = false, installModel: Boolean = false) {
        if (job?.isActive == true || state.value.unreadable) return
        val initial = state.value.entries.firstOrNull() ?: return
        if (initial.status == "conflict") return
        val owner = state.value.token
        val token = gate.begin(owner, initial.itemId)
        running.value = true
        job = scope.launch {
            var analysis: PhotoAnalysis? = null
            var fresh: String? = null
            fun accepts() = gate.accepts(token, owner, initial.itemId) && state.value.token == owner &&
                state.value.entries.firstOrNull()?.itemId == initial.itemId
            try {
                nativeQueue.withLock native@{
                    if (!accepts()) return@native
                    val ready = repo.writes.withLock {
                        if (!accepts()) return@withLock false
                        val current = repo.dao.garment(initial.itemId)
                        if (current == null || current.wardrobeId != initial.wardrobeId || current.photoId != initial.sourceId) {
                            replaceLocked(initial.copy(status = "conflict")); false
                        } else true
                    }
                    if (!ready || !accepts()) return@native
                    if (installModel) analyzer.requestModel()
                    val result = analyzer.analyze(initial.sourceId, stronger)
                    analysis = result
                    if (!accepts()) return@native
                    if (result.cutoutStatus == CutoutStatus.READY && result.cutoutPath != null) {
                        val id = newId()
                        repo.writes.withLock {
                            if (!accepts()) return@withLock
                            // Persist allocation before writing, so interrupted files can be recovered.
                            replaceLocked(initial.copy(candidateId = id, status = "pending"))
                            fresh = id
                            drainRetiredLocked()
                        }
                        if (fresh == null || !accepts()) return@native
                        photos.copyVariant(initial.sourceId, File(result.cutoutPath), id)
                        repo.writes.withLock {
                            if (accepts()) replaceLocked(initial.copy(candidateId = id, status = "review", cutout = CutoutStatus.READY))
                        }
                    } else repo.writes.withLock {
                        if (accepts()) {
                            // A failed retry leaves an already reviewed candidate available.
                            if (initial.status != "review") replaceLocked(initial.copy(status = "failed", cutout = result.cutoutStatus, failure = result.failure))
                            else notify(R.string.photo_reprocess_retry_failed)
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) { if (accepts()) failed(owner, initial, e) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (accepts()) failed(owner, initial, e) }
            finally {
                analysis?.cutoutPath?.let { File(it).delete() }
                try {
                    withContext(NonCancellable) {
                        repo.writes.withLock {
                            val current = state.value.entries.firstOrNull()
                            if (fresh != null && current != null && current.candidateId == fresh && current.status == "pending") {
                                replaceLocked(current.copy(candidateId = ""))
                            }
                            fresh?.let { cleanupLocked(setOf(it)) }
                            drainRetiredLocked()
                        }
                    }
                } catch (_: Exception) { notify(R.string.photo_reprocess_write_failed) }
                finally { running.value = false }
            }
        }
    }
    private suspend fun failed(owner: String, initial: PhotoReprocessEntry, error: Exception) {
        try { repo.writes.withLock {
            if (state.value.token == owner && state.value.entries.firstOrNull()?.itemId == initial.itemId) {
                val current = state.value.entries.first()
                if (current.status == "review") notify(R.string.photo_reprocess_retry_failed)
                else {
                    val failure = photoFailure(initial.sourceId, error)
                    replaceLocked(current.copy(status = "failed", candidateId = "", cutout = failure.cutoutStatus, failure = failure.failure))
                }
            }
        } } catch (e: CancellationException) { throw e }
        catch (_: Exception) { notify(R.string.photo_reprocess_write_failed) }
    }
    suspend fun accept(owner: String, expected: PhotoReprocessEntry) {
        check(!running.value)
        repo.writes.withLock {
            val entry = state.value.entries.firstOrNull() ?: return
            if (state.value.token != owner || entry != expected) return
            check(entry.status == "review" && entry.candidateId.isNotBlank())
            withContext(Dispatchers.IO) { photos.validateImage(photos.currentFile(entry.candidateId)) }
            val changed = repo.dao.replaceProcessedPhoto(entry.itemId, entry.wardrobeId, entry.sourceId, entry.candidateId)
            if (changed != 1) {
                val current = repo.dao.garment(entry.itemId)
                if (current == null || current.photoId != entry.candidateId || current.wardrobeId != entry.wardrobeId) {
                    replaceLocked(entry.copy(status = "conflict")); return@withLock
                }
            }
            advanceLocked(saved = true)
        }
        if (state.value.entries.firstOrNull()?.status == "pending") prepare()
    }
    suspend fun skip(owner: String, expected: PhotoReprocessEntry) {
        check(!running.value)
        repo.writes.withLock {
            if (state.value.token != owner || state.value.entries.firstOrNull() != expected) return
            advanceLocked(saved = false)
        }
        if (state.value.entries.firstOrNull()?.status == "pending") prepare()
    }
    suspend fun cancel(owner: String) {
        if (state.value.token != owner) return
        pause()
        repo.writes.withLock {
            val previous = state.value
            if (previous.token != owner) return
            store.write(PhotoReprocessState(saved = previous.saved, skipped = previous.skipped,
                retiredIds = previous.retiredIds + previous.photos()))
            drainRetiredLocked()
        }
    }
    private fun replaceLocked(entry: PhotoReprocessEntry) {
        val previous = state.value
        val retired = previous.entries.firstOrNull()?.candidateId?.takeIf { it.isNotBlank() && it != entry.candidateId }
        store.write(previous.copy(entries = listOf(entry) + previous.entries.drop(1),
            retiredIds = previous.retiredIds + listOfNotNull(retired)))
    }
    private suspend fun advanceLocked(saved: Boolean) {
        val previous = state.value
        val entry = previous.entries.firstOrNull() ?: return
        store.write(previous.copy(entries = previous.entries.drop(1), saved = previous.saved + if (saved) 1 else 0,
            skipped = previous.skipped + if (saved) 0 else 1,
            retiredIds = previous.retiredIds + listOf(entry.sourceId, entry.candidateId).filter { it.isNotBlank() }))
        drainRetiredLocked()
    }
    private suspend fun drainRetiredLocked() {
        val previous = state.value
        if (previous.retiredIds.isEmpty()) return
        if (!cleanupLocked(previous.retiredIds)) return
        // Retire only after deletion succeeds; a crash replays this idempotent cleanup.
        store.write(previous.copy(retiredIds = emptySet()))
    }
    private suspend fun cleanupLocked(ids: Set<String>): Boolean {
        val removed = photos.remove(ids - repo.referencedPhotos())
        if (!removed) notify(R.string.photo_cleanup_failed)
        return removed
    }
}
