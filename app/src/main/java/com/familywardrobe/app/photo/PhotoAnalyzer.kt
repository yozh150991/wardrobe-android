package com.familywardrobe.app.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.familywardrobe.app.newId
import com.familywardrobe.app.rules.PhotoCutoutRules
import com.familywardrobe.app.rules.PhotoRules
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

interface PhotoAnalysisEngine {
    suspend fun analyze(photoId: String, stronger: Boolean = false): PhotoAnalysis
    suspend fun requestModel(): Boolean
}

enum class CutoutStatus { READY, MODEL_NEEDED, PLAY_SERVICES_NEEDED, NOT_FOUND, TIMED_OUT, FAILED }

data class PhotoAnalysis(val sourceId: String, val category: String? = null, val color: String? = null,
    val cutoutPath: String? = null, val cutoutStatus: CutoutStatus = CutoutStatus.FAILED,
    val labelFailed: Boolean = false, val failure: PhotoFailureKind = PhotoFailureKind.NONE)

private const val TAG = "WardrobePhoto"

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
        val sourcePixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(sourcePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val workContext = coroutineContext
        // A photo that already has transparency (a cutout cleaned again, manual erasing) is shown to the model on a
        // backdrop that contrasts with the garment: transparent pixels have no colour, and on black a dark garment is lost.
        val shown = PhotoCutoutRules.modelInput(sourcePixels) { workContext.ensureActive() }
            ?.let { Bitmap.createBitmap(it, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888) }
        val input = InputImage.fromBitmap(shown ?: bitmap, 0) // Current photo is already EXIF-normalized and includes user edits.
        var category: String? = null
        var labelFailed = false
        try {
            val labels = withTimeout(30_000) { labeler.process(input).await() }
            category = PhotoRules.category(labels.associate { it.index to it.confidence })
        } catch (_: TimeoutCancellationException) { labelFailed = true; Log.w(TAG, "Labels timed out")
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { labelFailed = true; Log.w(TAG, "Labels failed", e) }

        if (!hasPlayServices()) return@withContext PhotoAnalysis(photoId, category = category,
            cutoutStatus = CutoutStatus.PLAY_SERVICES_NEEDED, labelFailed = labelFailed)
        // QA-61-30: the stage and the mask shape go to logcat (tag WardrobePhoto), never pixels or paths, so a failure
        // reported by a user can be told apart: the model, the segmenter, the mask check or saving the cutout.
        var stage = "model"
        try {
            val available = withTimeout(10_000) { modules.areModulesAvailable(segmenter).await().areModulesAvailable() }
            if (!available) return@withContext PhotoAnalysis(photoId, category = category,
                cutoutStatus = CutoutStatus.MODEL_NEEDED, labelFailed = labelFailed)
            stage = "segment"
            val result = withTimeout(30_000) { segmenter.process(input).await() }
            stage = "refine"
            val confidence = result.foregroundConfidenceMask?.duplicate()?.apply { rewind() }
                ?: return@withContext PhotoAnalysis(photoId, category = category, cutoutStatus = CutoutStatus.NOT_FOUND, labelFailed = labelFailed)
                    .also { Log.w(TAG, "Segmenter returned no mask") }
            val pixels = try {
                PhotoCutoutRules.refine(sourcePixels, bitmap.width, bitmap.height, confidence, stronger) { workContext.ensureActive() }
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Mask rejected: " + PhotoCutoutRules.describe(confidence, bitmap.width, bitmap.height)); throw e
            }
            // PHOTO-19: debug builds keep the rules' input and output for tuning on real masks.
            if (PhotoDebugCapture.enabled(context)) withContext(Dispatchers.IO) {
                PhotoDebugCapture.save(context, sourcePixels, bitmap.width, bitmap.height, confidence, pixels, stronger)
            }
            if (!PhotoRules.usableCutout(pixels)) return@withContext PhotoAnalysis(photoId, category = category,
                cutoutStatus = CutoutStatus.NOT_FOUND, labelFailed = labelFailed).also { Log.i(TAG, "No usable cutout") }
            stage = "save"
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
            Log.w(TAG, "Cutout timed out at $stage")
            PhotoAnalysis(photoId, category = category, cutoutStatus = CutoutStatus.TIMED_OUT, labelFailed = labelFailed)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cutout failed at $stage", e)
            photoFailure(photoId, e).copy(category = category, labelFailed = labelFailed)
        }
        // Do not manually recycle input bitmaps: timed-out native tasks may still reference them.
    }
}
