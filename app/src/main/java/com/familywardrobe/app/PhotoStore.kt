package com.familywardrobe.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class PhotoStore(private val context: Context) {
    val finalResolution = PhotoResolutionRules.finalLimit(Runtime.getRuntime().maxMemory())
    val directory = File(context.filesDir, "photos").apply { mkdirs() }
    private val cleanupPrefs = context.getSharedPreferences("photo-cleanup", Context.MODE_PRIVATE)
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun clearMemoryCache() { cache.evictAll() }
    /** Also cleans a completed candidate if cancellation happens at dispatcher result delivery. */
    private suspend fun candidate(block: suspend () -> String): String {
        var created: String? = null
        try {
            return withContext(Dispatchers.IO) { coroutineContext.ensureActive(); block().also { created = it; coroutineContext.ensureActive() } }
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { created?.let { remove(setOf(it)) } }
            throw e
        }
    }
    fun file(id: String, extension: String = "jpg"): File {
        require(ID.matches(id) && extension in ALL_EXTENSIONS)
        return File(directory, "$id.$extension")
    }
    fun newCapture(): Uri {
        // Only camera/ is exposed by FileProvider, never the private account cache.
        val namespace = (context as? AccountContext)?.location?.key ?: "local"
        val dir = File(context.applicationContext.cacheDir, "camera/$namespace").apply { mkdirs() }
        // Pending camera files only; saved item photographs are never deleted here.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7 * 86_400_000L }?.forEach { it.delete() }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "${newId()}.jpg"))
    }
    suspend fun import(uri: Uri): String = candidate {
        val workContext = coroutineContext
        val id = newId()
        val original = File(context.cacheDir, "import-$id.tmp")
        val preview = file(id)
        try {
            (context.contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException()).use { input ->
                original.outputStream().use { copyLimited(input, it, MAX_PHOTO) { workContext.ensureActive() } }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(original.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw UnreadablePhotoException()
            val options = BitmapFactory.Options().apply {
                inSampleSize = PhotoResolutionRules.sample(bounds.outWidth, bounds.outHeight,
                    PhotoResolutionRules.sourceLimit(finalResolution), PhotoResolutionRules.pixelLimit(finalResolution))
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(original.path, options) ?: throw UnreadablePhotoException()
            val exif = ExifInterface(original.path)
            val matrix = Matrix().apply {
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            preview.outputStream().use { require(rotated.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            if (rotated !== decoded) rotated.recycle()
            decoded.recycle()
            if (uri.authority == "${context.packageName}.files") runCatching { context.contentResolver.delete(uri, null, null) }
            id
        } catch (e: Exception) { preview.delete(); throw e }
        finally { original.delete() }
    }
    suspend fun bitmap(id: String, detailed: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        // Cards decode a small preview; zoom alone requests the full saved raster.
        val key = "$id:$detailed"
        cache.get(key) ?: runCatching {
            val preview = currentFile(id)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(preview.path, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = PhotoResolutionRules.sample(bounds.outWidth, bounds.outHeight,
                    if (detailed) 4096 else 768, if (detailed) 16_000_000L else 768L * 768)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(preview.path, options)?.also { cache.put(key, it) }
        }.getOrNull()
    }
    suspend fun fingerprint(id: String): String = withContext(Dispatchers.IO) {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        currentFile(id).inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    suspend fun mask(sourceId: String, strokes: List<PhotoMaskRules.Stroke>): String = candidate {
        val bitmap = requireNotNull(BitmapFactory.decodeFile(currentFile(sourceId).path))
        val id = newId(); val target = file(id, "png")
        var output: Bitmap? = null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val workContext = coroutineContext
            val edited = PhotoMaskRules.paint(pixels, bitmap.width, bitmap.height, strokes) { workContext.ensureActive() }
            require(edited.any { (it ushr 24) > 0 }) { "Empty image" }
            val fresh = Bitmap.createBitmap(edited, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { output = it }
            target.outputStream().use { require(fresh.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            validateImage(target); id
        } catch (e: Exception) { target.delete(); throw e }
        finally { output?.recycle(); bitmap.recycle() }
    }
    fun currentFile(id: String): File = file(id, "png").takeIf { it.isFile } ?: file(id)

    internal fun analysisBitmap(id: String): Bitmap {
        val source = currentFile(id)
        if (!source.isFile) throw java.io.FileNotFoundException()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw UnreadablePhotoException()
        val options = BitmapFactory.Options().apply {
            inSampleSize = PhotoResolutionRules.sample(bounds.outWidth, bounds.outHeight,
                PhotoResolutionRules.sourceLimit(finalResolution), PhotoResolutionRules.pixelLimit(finalResolution))
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(source.path, options) ?: throw UnreadablePhotoException()
    }

    suspend fun editSession(sourceId: String, history: PhotoEditHistory, id: String = newId()): String = candidate {
        require(ID.matches(id) && !file(id, "png").exists() && !file(id).exists())
        val target = file(id, "png")
        var rendered: PhotoSessionImage? = null
        try {
            val result = renderPhotoSession(this, sourceId, history).also { rendered = it }
            require(result.frame.pixels.any { (it ushr 24) > 0 }) { "Empty image" }
            coroutineContext.ensureActive()
            target.outputStream().use { require(result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            validateImage(target)
            coroutineContext.ensureActive()
            id
        } catch (e: Exception) { target.delete(); throw e }
        finally { rendered?.bitmap?.recycle() }
    }

    /** A candidate has its own ID; only Save changes the garment's reference. */
    suspend fun copyVariant(sourceId: String, cutout: File, id: String = newId()): String = candidate {
        require(ID.matches(id) && !file(id, "png").exists() && !file(id).exists())
        try {
            require(cutout.isFile && cutout.length() in 1..MAX_PHOTO)
            validateCutout(cutout, currentFile(sourceId))
            val bitmap = requireNotNull(BitmapFactory.decodeFile(cutout.path))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            require(pixels.any { (it ushr 24) > 0 }) { "Empty cutout" }
            val bounds = PhotoPresentationGeometry.bounds(pixels, bitmap.width, bitmap.height)
            val size = PhotoResolutionRules.canvas(bounds, finalResolution, 0.08f)
            val fitted = PhotoPresentationGeometry.fit(bounds, size, 0.08f)
            val canvas = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            try {
                Canvas(canvas).drawBitmap(bitmap, Rect(bounds[0], bounds[1], bounds[2], bounds[3]),
                    RectF(fitted[0], fitted[1], fitted[2], fitted[3]), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                file(id, "png").outputStream().use { require(canvas.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { canvas.recycle(); bitmap.recycle() }
            require(file(id, "png").length() in 1..MAX_PHOTO)
            id
        } catch (e: Exception) { file(id, "png").delete(); throw e }
    }

    suspend fun edit(sourceId: String, quarterTurns: Int, left: Float, top: Float,
        right: Float, bottom: Float): String = candidate {
        val source = currentFile(sourceId)
        val decoded = requireNotNull(BitmapFactory.decodeFile(source.path))
        var rotated: Bitmap? = null
        var cropped: Bitmap? = null
        val id = newId()
        val target = file(id, source.extension)
        try {
            val turned = PhotoTransform.rotate(decoded, quarterTurns).also { rotated = it }
            val box = PhotoGeometry.crop(turned.width, turned.height, left, top, right, bottom)
            val output = Bitmap.createBitmap(turned, box[0], box[1], box[2], box[3]).also { cropped = it }
            val format = if (source.extension == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            target.outputStream().use { require(output.compress(format, 92, it)) }
            require(target.length() in 1..MAX_PHOTO)
            id
        } catch (e: Exception) { target.delete(); throw e }
        finally {
            if (cropped !== rotated && cropped !== decoded) cropped?.recycle()
            if (rotated !== decoded) rotated?.recycle()
            decoded.recycle()
        }
    }

    fun validateImage(file: File) {
        require(file.length() in 1..MAX_PHOTO)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000)
        require(bounds.outMimeType == if (file.extension == "png") "image/png" else "image/jpeg")
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path))
        bitmap.recycle()
    }

    /** Remove obsolete originals only when the current display photo is readable. */
    suspend fun removeLegacyCopies(): Boolean = withContext(Dispatchers.IO) {
        var cleaned = true
        val ids = directory.listFiles().orEmpty().map { it.nameWithoutExtension }.filter { ID.matches(it) }.toSet()
        for (id in ids) {
            val current = currentFile(id)
            val obsolete = listOf(file(id, "original")) + if (current.extension == "png") listOf(file(id)) else emptyList()
            if (obsolete.none { it.exists() }) continue
            if (runCatching { validateImage(current) }.isFailure) { cleaned = false; continue }
            obsolete.forEach { if (it.exists() && !it.delete()) cleaned = false }
        }
        cleaned
    }

    fun validateCutout(cutout: File, preview: File) {
        val mask = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val base = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(cutout.path, mask)
        BitmapFactory.decodeFile(preview.path, base)
        require(mask.outMimeType == "image/png" && mask.outWidth > 0 && mask.outHeight > 0)
        require(PhotoResolutionRules.matchesMask(base.outWidth, base.outHeight, mask.outWidth, mask.outHeight, finalResolution))
        require(mask.outWidth.toLong() * mask.outHeight <= 16_000_000)
    }
    private val pinned get() = PINS.computeIfAbsent(directory.absolutePath) { java.util.concurrent.ConcurrentHashMap() }
    private val cleanupLock get() = CLEANUP_LOCKS.computeIfAbsent(directory.absolutePath) { Any() }
    /** A photo being archived is never deleted mid-copy; its removal is deferred to the retry queue. */
    fun pin(ids: Set<String>) { ids.forEach { id -> pinned.merge(id, 1) { old, added -> old + added } } }
    fun unpin(ids: Set<String>) { ids.forEach { id -> pinned.computeIfPresent(id) { _, count -> if (count <= 1) null else count - 1 } } }
    suspend fun remove(ids: Set<String>): Boolean = withContext(Dispatchers.IO) {
        // One read-modify-write of the retry queue at a time (foreground, workers, reprocessing).
        synchronized(cleanupLock) {
            var removed = true
            val pending = cleanupPrefs.getStringSet("pending", emptySet()).orEmpty().toMutableSet()
            ids.forEach { id ->
                if (pinned.containsKey(id)) { pending.add(id); return@forEach }
                var photoRemoved = true
                cache.remove("$id:false"); cache.remove("$id:true")
                for (extension in ALL_EXTENSIONS) {
                    // A filesystem cleanup failure must not turn a committed DB deletion into a retry.
                    val ok = runCatching { val f = file(id, extension); !f.exists() || f.delete() }.getOrDefault(false)
                    if (!ok) { removed = false; photoRemoved = false }
                }
                if (photoRemoved) pending.remove(id) else pending.add(id)
            }
            cleanupPrefs.edit().putStringSet("pending", pending).commit()
            removed
        }
    }
    suspend fun retryRemovals(protectedIds: Set<String>): Boolean {
        val pending = synchronized(cleanupLock) {
            val retained = cleanupPrefs.getStringSet("pending", emptySet()).orEmpty().filter { ID.matches(it) }.toSet() - protectedIds
            cleanupPrefs.edit().putStringSet("pending", retained).commit()
            retained
        }
        return remove(pending)
    }
    companion object {
        private val PINS = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, Int>>()
        private val CLEANUP_LOCKS = java.util.concurrent.ConcurrentHashMap<String, Any>()
        // original is accepted only for cleanup of releases <= 0.2.0.
        val ALL_EXTENSIONS = listOf("original", "jpg", "png")
        val ID = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
        const val MAX_PHOTO = 25L * 1024 * 1024
        fun copyLimited(input: InputStream, output: OutputStream, maximum: Long, checkpoint: () -> Unit = {}): Long {
            val buffer = ByteArray(64 * 1024); var total = 0L
            while (true) {
                checkpoint()
                val n = input.read(buffer); if (n < 0) break
                total += n; if (total > maximum) throw PhotoTooLargeException()
                output.write(buffer, 0, n)
            }
            return total
        }
    }
}
