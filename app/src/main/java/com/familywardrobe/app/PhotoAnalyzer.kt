package com.familywardrobe.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File

interface PhotoAnalysisEngine {
    suspend fun analyze(photoId: String, stronger: Boolean = false): PhotoAnalysis
    suspend fun requestModel(): Boolean
}

enum class CutoutStatus { READY, MODEL_NEEDED, PLAY_SERVICES_NEEDED, NOT_FOUND, TIMED_OUT, FAILED }

data class PhotoAnalysis(val sourceId: String, val category: String? = null, val color: String? = null,
    val cutoutPath: String? = null, val cutoutStatus: CutoutStatus = CutoutStatus.FAILED,
    val labelFailed: Boolean = false, val failure: PhotoFailureKind = PhotoFailureKind.NONE)

/** All inference is on-device. Only model installation is delegated to Play services. */
class PhotoAnalyzer(private val context: Context, private val photos: PhotoStore) : PhotoAnalysisEngine {
    private val labelerDelegate = lazy { ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.50f).build()) }
    private val labeler by labelerDelegate
    private val segmenterDelegate = lazy { SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build()) }
    private val segmenter by segmenterDelegate
    private val modules by lazy { ModuleInstall.getClient(context) }
    private val cacheDir = File(context.cacheDir, "photo-analysis").apply { mkdirs() }

    fun close() {
        if (labelerDelegate.isInitialized()) labeler.close()
        if (segmenterDelegate.isInitialized()) segmenter.close()
    }

    private fun hasPlayServices() = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    override suspend fun requestModel(): Boolean {
        check(hasPlayServices())
        return withTimeout(20_000) {
            if (modules.areModulesAvailable(segmenter).await().areModulesAvailable()) true
            else {
                modules.installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build()).await()
                // Acceptance of the install request does not imply that the download is finished.
                modules.areModulesAvailable(segmenter).await().areModulesAvailable()
            }
        }
    }

    override suspend fun analyze(photoId: String, stronger: Boolean): PhotoAnalysis = withContext(Dispatchers.Default) {
        val bitmap = withContext(Dispatchers.IO) { photos.analysisBitmap(photoId) }
        val input = InputImage.fromBitmap(bitmap, 0) // Current photo is already EXIF-normalized and includes user edits.
        var category: String? = null
        var labelFailed = false
        try {
            val labels = withTimeout(30_000) { labeler.process(input).await() }
            category = PhotoRules.category(labels.associate { it.index to it.confidence })
        } catch (_: TimeoutCancellationException) { labelFailed = true
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { labelFailed = true }

        if (!hasPlayServices()) return@withContext PhotoAnalysis(photoId, category = category,
            cutoutStatus = CutoutStatus.PLAY_SERVICES_NEEDED, labelFailed = labelFailed)
        try {
            val available = withTimeout(10_000) { modules.areModulesAvailable(segmenter).await().areModulesAvailable() }
            if (!available) return@withContext PhotoAnalysis(photoId, category = category,
                cutoutStatus = CutoutStatus.MODEL_NEEDED, labelFailed = labelFailed)
            val result = withTimeout(30_000) { segmenter.process(input).await() }
            val confidence = result.foregroundConfidenceMask?.duplicate()?.apply { rewind() }
                ?: return@withContext PhotoAnalysis(photoId, category = category, cutoutStatus = CutoutStatus.NOT_FOUND, labelFailed = labelFailed)
            val sourcePixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(sourcePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val workContext = coroutineContext
            val pixels = PhotoCutoutRules.refine(sourcePixels, bitmap.width, bitmap.height, confidence, stronger) { workContext.ensureActive() }
            if (!PhotoRules.usableCutout(pixels)) return@withContext PhotoAnalysis(photoId, category = category,
                cutoutStatus = CutoutStatus.NOT_FOUND, labelFailed = labelFailed)
            val color = PhotoRules.dominantColor(pixels)
            val foreground = Bitmap.createBitmap(pixels, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val path = withContext(Dispatchers.IO) {
                cacheDir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
                val file = File(cacheDir, "${newId()}.png")
                try {
                    file.outputStream().use { require(foreground.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    require(file.length() in 1..PhotoStore.MAX_PHOTO)
                    file.absolutePath
                } catch (e: Exception) { file.delete(); throw e }
                finally { foreground.recycle() }
            }
            PhotoAnalysis(photoId, category, color, path, CutoutStatus.READY, labelFailed)
        } catch (_: TimeoutCancellationException) {
            PhotoAnalysis(photoId, category = category, cutoutStatus = CutoutStatus.TIMED_OUT, labelFailed = labelFailed)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { photoFailure(photoId, e).copy(category = category, labelFailed = labelFailed) }
        // Do not manually recycle input bitmaps: timed-out native tasks may still reference them.
    }
}
