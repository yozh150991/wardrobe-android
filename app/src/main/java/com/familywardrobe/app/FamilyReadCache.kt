package com.familywardrobe.app

import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File

/** A read-only snapshot/photo namespace. It never opens or writes the private Room database. */
internal class FamilyReadCache(context: AccountContext, sessionId: String) {
    private val root = File(context.noBackupFilesDir, "family-read/${context.location.key}/$sessionId")
    init { require(FamilyRules.validId(sessionId)) }
    private var initialized = false
    @Synchronized private fun prepare() {
        if (initialized) return
        // Called on IO, never during AccountSession construction on the main thread.
        // Previous process/session leases are invalid; cached files cannot grant access.
        check(root.parentFile?.deleteRecursively() != false)
        check(root.mkdirs())
        initialized = true
    }
    private val candidates = java.util.concurrent.ConcurrentHashMap.newKeySet<Candidate>()
    internal data class Candidate(val directory: File, val snapshot: Snapshot, val photos: PhotoStore)
    fun newDownload(): File { prepare(); return File(root, "download-${newId()}.zip") }
    fun decode(archive: File, remote: FamilyRemote, decoder: FamilyEnvelope, guard: () -> Unit): Candidate {
        val head = requireNotNull(remote.head)
        val directory = File(root, "view-${newId()}")
        var photos: PhotoStore? = null
        try {
            val extracted = FamilyCacheZip.extract(archive, head.size, head.hash, directory, guard)
            val snapshot = decoder.decode(extracted.manifest, remote.identity, head.base)
            val photoFiles = JSONObject(extracted.manifest).getJSONObject("snapshot").getJSONObject("photoFiles")
            val expected = snapshot.photoIds().map { "photos/$it.${photoFiles.getString(it)}" }.toSet() + "manifest.json"
            require(extracted.names == expected)
            val isolated = object : ContextWrapper(base) {
                override fun getFilesDir(): File = directory
                override fun getCacheDir(): File = File(directory, "temporary").apply { check(mkdirs() || isDirectory) }
            }
            val store = PhotoStore(isolated); photos = store
            for (id in snapshot.photoIds()) {
                guard()
                val file = store.currentFile(id)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192 &&
                    bounds.outWidth.toLong() * bounds.outHeight <= 16L * 1024 * 1024)
                require(bounds.outMimeType == if (file.extension == "png") "image/png" else "image/jpeg")
                val options = BitmapFactory.Options().apply {
                    inSampleSize = PhotoResolutionRules.sample(bounds.outWidth, bounds.outHeight, 1024, 1_048_576L)
                }
                val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, options)); bitmap.recycle()
            }
            guard()
            return Candidate(directory, snapshot, store).also { candidates.add(it) }
        } catch (e: Exception) {
            photos?.clearMemoryCache(); directory.deleteRecursively(); throw e
        }
    }
    private val base = context
    fun discard(candidate: Candidate) {
        candidates.remove(candidate)
        candidate.photos.clearMemoryCache()
        candidate.directory.deleteRecursively()
    }
    fun close() { candidates.toList().forEach(::discard); root.parentFile?.deleteRecursively() }
}
