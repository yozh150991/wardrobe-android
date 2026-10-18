package com.familywardrobe.app

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Prepared for the combined Android acceptance run; fake inference, real Room/PNG/AtomicFile. */
class PhotoReprocessingTest {
    private class Fixture(val context: AccountContext, val repo: Repository, val photos: PhotoStore,
        val store: PhotoReprocessStore, val scope: CoroutineScope) {
        val notices = mutableListOf<Int>()
        fun processor(engine: PhotoAnalysisEngine = object : PhotoAnalysisEngine {
            override suspend fun requestModel() = true
            override suspend fun analyze(photoId: String, stronger: Boolean): PhotoAnalysis {
                val bitmap = photos.analysisBitmap(photoId)
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                for (y in 0 until bitmap.height) for (x in 0..3) pixels[y * bitmap.width + x] = 0
                val output = Bitmap.createBitmap(pixels, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                val file = File(context.cacheDir, "${newId()}.png")
                try { file.outputStream().use { check(output.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
                finally { bitmap.recycle(); output.recycle() }
                return PhotoAnalysis(photoId, cutoutPath = file.path, cutoutStatus = CutoutStatus.READY)
            }
        }, queue: PhotoReprocessStore = store) = PhotoReprocessor(queue, repo, photos, engine, Mutex(), scope) { notices += it }
        fun photo(): String {
            val id = newId(); val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xff326591.toInt())
            try { photos.file(id, "png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
            return id
        }
        suspend fun garment() = Garment(wardrobeId = Repository.DEFAULT_WARDROBE, name = "Top", category = "top", color = "blue",
            photoId = photo(), description = "Retain", photoFingerprint = "a".repeat(64)).also { repo.save(it) }
    }
    private fun scenario(block: suspend Fixture.() -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = AccountContext(base, AccountLocation(AuthRules.accountKey("tests", newId()), false))
        val db = Room.inMemoryDatabaseBuilder(base, WardrobeDatabase::class.java).build()
        val repo = Repository(db)
        try {
            repo.initialize("Home")
            Fixture(context, repo, PhotoStore(context), PhotoReprocessStore(context), this).block()
            coroutineContext[Job]!!.children.toList().forEach { it.join() }
        } finally {
            coroutineContext[Job]!!.children.toList().forEach { it.cancelAndJoin() }
            db.close(); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
            base.deleteSharedPreferences("account-${context.location.key}-photo-cleanup")
        }
    }
    private suspend fun PhotoReprocessor.idle() = withTimeout(10_000) { running.first { !it } }

    @Test fun reviewReplacesOnlyApprovedPhotoAndPreservesConcurrentMetadata() = scenario {
        val first = garment(); val second = garment(); val worker = processor()
        worker.begin(first.wardrobeId, listOf(first.id, second.id)); worker.idle()
        val originalBytes = photos.currentFile(first.photoId).readBytes()
        val before = worker.state.value; val reviewed = before.entries.first()
        assertEquals("review", reviewed.status)
        assertEquals(first, repo.dao.garment(first.id))
        repo.update(first.id) { it.copy(name = "Edited meanwhile", size = "L") }
        worker.accept(before.token, reviewed); worker.idle()
        assertEquals(first.copy(name = "Edited meanwhile", size = "L", photoId = reviewed.candidateId), repo.dao.garment(first.id))
        assertEquals(second, repo.dao.garment(second.id))
        assertEquals(1, worker.state.value.saved)
        worker.accept(before.token, reviewed) // stale UI callback may not approve the next item
        assertEquals(second, repo.dao.garment(second.id))
        assertFalse(photos.currentFile(first.photoId).exists())
        assertTrue(originalBytes.isNotEmpty())
        worker.cancel(before.token)
        assertTrue(photos.currentFile(reviewed.candidateId).isFile)
    }
    @Test fun changedPhotoBlocksReplacementAndSharedSourceSurvivesSkip() = scenario {
        val first = garment(); val shared = first.copy(id = newId()); repo.save(shared)
        val worker = processor(); worker.begin(first.wardrobeId, listOf(first.id)); worker.idle()
        val before = worker.state.value; val row = before.entries.single(); val manual = photo()
        repo.save(first.copy(photoId = manual), photos)
        worker.accept(before.token, row)
        assertEquals("conflict", worker.state.value.entries.single().status)
        worker.skip(before.token, worker.state.value.entries.single())
        assertEquals(manual, repo.dao.garment(first.id)!!.photoId)
        assertTrue(photos.currentFile(shared.photoId).isFile)
        assertFalse(photos.currentFile(row.candidateId).exists())
    }
    @Test fun failedInferenceAndCancelLeaveSavedPhotoUntouched() = scenario {
        val item = garment(); val bytes = photos.currentFile(item.photoId).readBytes()
        val worker = processor(object : PhotoAnalysisEngine {
            override suspend fun requestModel() = false
            override suspend fun analyze(photoId: String, stronger: Boolean) = PhotoAnalysis(photoId, cutoutStatus = CutoutStatus.MODEL_NEEDED)
        })
        worker.begin(item.wardrobeId, listOf(item.id)); worker.idle()
        assertEquals(CutoutStatus.MODEL_NEEDED, worker.state.value.entries.single().cutout)
        worker.cancel(worker.state.value.token)
        assertEquals(item, repo.dao.garment(item.id)); assertArrayEquals(bytes, photos.currentFile(item.photoId).readBytes())
    }
    @Test fun restartRecoversReviewAndRemovesInterruptedAllocation() = scenario {
        val item = garment(); val worker = processor(); worker.begin(item.wardrobeId, listOf(item.id)); worker.idle()
        val loaded = PhotoReprocessStore(context)
        assertEquals(store.state.value, loaded.state.value)
        processor(queue = loaded).recover()
        assertEquals("review", loaded.state.value.entries.single().status)
        val old = loaded.state.value; val row = old.entries.single()
        loaded.write(old.copy(entries = listOf(row.copy(status = "pending"))))
        val recovered = PhotoReprocessStore(context); processor(queue = recovered).recover()
        assertEquals("", recovered.state.value.entries.single().candidateId)
        assertFalse(photos.currentFile(row.candidateId).exists())
        assertTrue(photos.currentFile(item.photoId).exists())
    }
    @Test fun restartAfterDatabaseCommitAdvancesOnceAndCleansOldFile() = scenario {
        val item = garment(); val worker = processor(); worker.begin(item.wardrobeId, listOf(item.id)); worker.idle()
        val before = store.state.value; val row = before.entries.single()
        assertEquals(1, repo.dao.replaceProcessedPhoto(item.id, item.wardrobeId, item.photoId, row.candidateId))
        val loaded = PhotoReprocessStore(context); val recovered = processor(queue = loaded)
        recovered.recover(); recovered.recover()
        assertTrue(loaded.state.value.entries.isEmpty()); assertEquals(1, loaded.state.value.saved)
        assertFalse(photos.currentFile(item.photoId).exists()); assertTrue(photos.currentFile(row.candidateId).exists())
    }
    @Test fun lateResultCannotRecreateCancelledQueue() = scenario {
        val item = garment(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val worker = processor(object : PhotoAnalysisEngine {
            override suspend fun requestModel() = true
            override suspend fun analyze(photoId: String, stronger: Boolean): PhotoAnalysis {
                entered.complete(Unit); release.await()
                val file = File(context.cacheDir, "${newId()}.png")
                photos.currentFile(photoId).copyTo(file)
                return PhotoAnalysis(photoId, cutoutPath = file.path, cutoutStatus = CutoutStatus.READY)
            }
        })
        worker.begin(item.wardrobeId, listOf(item.id)); entered.await()
        worker.cancel(worker.state.value.token); release.complete(Unit); worker.idle()
        assertTrue(worker.state.value.entries.isEmpty()); assertEquals(item, repo.dao.garment(item.id))
        assertEquals(setOf("${item.photoId}.png"), photos.directory.listFiles()!!.map { it.name }.toSet())
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.extension == "png" })
    }
    @Test fun retiredFilesRecoverWithoutDeletingReferencedFinalPhoto() = scenario {
        val item = garment(); val discarded = photo()
        store.write(PhotoReprocessState(retiredIds = setOf(item.photoId, discarded)))
        val loaded = PhotoReprocessStore(context); processor(queue = loaded).recover()
        assertTrue(photos.currentFile(item.photoId).exists()); assertFalse(photos.currentFile(discarded).exists())
        assertTrue(loaded.state.value.retiredIds.isEmpty())
    }
    @Test fun editorDraftKeepsOldPhotoUntilItsOwnCleanup() = scenario {
        val item = garment(); repo.editorDraftPhotos = { setOf(item.photoId) }
        val worker = processor(); worker.begin(item.wardrobeId, listOf(item.id)); worker.idle()
        val before = worker.state.value; val row = before.entries.single()
        worker.accept(before.token, row)
        assertEquals(row.candidateId, repo.dao.garment(item.id)!!.photoId)
        assertTrue(photos.currentFile(item.photoId).exists())
        repo.editorDraftPhotos = { emptySet() }
        assertTrue(repo.discardPhotos(setOf(item.photoId), photos))
        assertFalse(photos.currentFile(item.photoId).exists()); assertTrue(photos.currentFile(row.candidateId).exists())
    }
    @Test fun queueAndCandidatesStayInsideAccount() = scenario {
        val item = garment(); val worker = processor(); worker.begin(item.wardrobeId, listOf(item.id)); worker.idle()
        val other = AccountContext(context.applicationContext, AccountLocation(AuthRules.accountKey("tests", newId()), false))
        try {
            assertTrue(PhotoReprocessStore(other).state.value.entries.isEmpty())
            assertFalse(PhotoStore(other).currentFile(store.state.value.entries.single().candidateId).exists())
            assertFalse(PhotoStore(other).currentFile(item.photoId).exists())
        } finally { other.filesDir.deleteRecursively(); other.cacheDir.deleteRecursively() }
    }
}
