package com.familywardrobe.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Device checks: real Room, Android bitmap/URI APIs and portable backup serialization. */
class ExperiencePersistenceTest {
    private fun scenario(test: suspend (AccountContext, Repository, PhotoStore, List<Garment>) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val key = AuthRules.accountKey("instrumentation-experience", newId())
        val context = AccountContext(base, AccountLocation(key, false))
        val db = Room.inMemoryDatabaseBuilder(base, WardrobeDatabase::class.java).build()
        val photos = PhotoStore(context)
        try {
            val repo = Repository(db)
            repo.initialize("Home")
            val items = listOf("dress", "shoes", "layer").map { category ->
                val photo = newId()
                val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.rgb(35, 70, 110))
                photos.file(photo, "png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                bitmap.recycle()
                Garment(wardrobeId = Repository.DEFAULT_WARDROBE, name = category, category = category,
                    color = "navy", photoId = photo, subcategory = "Travel", photoFingerprint = photos.fingerprint(photo))
                    .also { repo.save(it) }
            }
            test(context, repo, photos, items)
        } finally {
            db.close()
            photos.clearMemoryCache()
            context.getSharedPreferences("photo-cleanup", 0).edit().clear().commit()
            context.filesDir.deleteRecursively()
            context.cacheDir.deleteRecursively()
        }
    }

    @Test fun recommendationRulesUseActualWearAndKeepManualItemsAvailable() = scenario { _, repo, _, items ->
        val today = LocalDate.now().toString()
        val look = repo.saveLook(items.first().wardrobeId, items.take(2).map(LookRules::owned), "Dress look", "everyday", "any_weather", "owned")
        repo.dao.setNoRepeatDays(items.first().wardrobeId, 7)
        repo.scheduleOutfit(look, today)
        assertTrue(items.take(2).all { g -> DailyOutfits.items(repo.snapshot(), today).any { it.piece.id == g.id } })
        repo.toggleItemWear(items.first().id, today)
        repo.save(items.last().copy(suggestionExcluded = true))
        val eligible = DailyOutfits.items(repo.snapshot(), today).map { it.piece.id }.toSet()
        assertFalse(items.first().id in eligible)
        assertFalse(items.last().id in eligible)
        assertTrue(items[1].id in eligible)
        assertTrue(repo.dao.garment(items.last().id)!!.suggestionExcluded)
        assertTrue(LookRules.candidates(repo.snapshot(), items.first().wardrobeId, "everyday", "any_weather", "owned", emptyList()).any { it.ownedId == items.last().id })
        assertEquals(3, repo.dao.allGarments().size)
    }

    @Test fun capsuleGenerationUsesSharedItemsAndRechecksPackedScopeWhenSaving() = scenario { _, repo, _, items ->
        val trip = repo.saveCapsule(Capsule(wardrobeId = items.first().wardrobeId, name = "Trip", kind = "trip"))
        items.take(2).forEach { repo.manualCapsuleItem(trip, it.id, true) }
        val allowed = items.take(2).map { it.id }.toSet()
        val generated = DailyOutfits.generate(repo.snapshot(), items.first().wardrobeId, "everyday", "any_weather", LocalDate.now().toString(), allowed = allowed)
        assertTrue(generated.isNotEmpty())
        assertTrue(generated.all { look -> look.all { it.id in allowed } && Matching.complete(look) })
        val count = repo.dao.allGarments().size
        val look = repo.saveCapsuleDaily(trip, allowed.toList(), "Trip look", "everyday", "any_weather", false)
        assertEquals(count, repo.dao.allGarments().size)
        assertEquals(setOf(look), repo.dao.allCapsuleOutfits().filter { it.capsuleId == trip }.map { it.outfitId }.toSet())
        try {
            repo.saveCapsuleDaily(trip, items.map { it.id }, "Outside scope", "everyday", "any_weather", false)
            fail("Out-of-capsule item must be rejected")
        } catch (_: IllegalArgumentException) { }
        repo.packCapsuleItem(trip, items.first().id, true)
        try {
            repo.saveCapsuleDaily(trip, allowed.toList(), "Not all packed", "everyday", "any_weather", true)
            fail("Unpacked shoes must be rejected")
        } catch (_: IllegalArgumentException) { }
        repo.packCapsuleItem(trip, items[1].id, true)
        assertEquals(look, repo.saveCapsuleDaily(trip, allowed.toList(), "Packed", "everyday", "any_weather", true))
        BackupStateRules.validate(repo.snapshot())
    }

    @Test fun verifiedBackupPreservesRulesSubcategoriesExclusionsAndRemappedCanvas() = scenario { context, repo, photos, items ->
        repo.dao.setNoRepeatDays(items.first().wardrobeId, 7)
        repo.save(items.last().copy(suggestionExcluded = true))
        val pieces = items.map(LookRules::owned)
        val layout = OutfitCanvasState.change(pieces, "", pieces.first().key, dx = .04f, zoom = .9f, front = true)
        val look = repo.saveLook(items.first().wardrobeId, pieces, "Canvas", "everyday", "any_weather", "owned", layout = layout, background = "sage")
        val file = File(context.cacheDir, "experience.zip")
        val backup = Backup(context, repo, photos)
        backup.export(Uri.fromFile(file))
        val before = repo.snapshot()
        val verification = backup.verify(Uri.fromFile(file))
        assertEquals(BackupVerification(1, 3, 1), verification.copy(fingerprint = ""))
        assertTrue(Regex("[a-f0-9]{64}").matches(verification.fingerprint))
        assertEquals(before, repo.snapshot())
        val restoredWardrobe = backup.restore(Uri.fromFile(file))!!
        val restored = repo.snapshot()
        assertEquals(7, restored.wardrobes.single { it.id == restoredWardrobe }.noRepeatDays)
        val restoredItems = restored.garments.filter { it.wardrobeId == restoredWardrobe }
        assertTrue(restoredItems.all { it.subcategory == "Travel" && it.photoFingerprint.isNotBlank() })
        assertTrue(restoredItems.single { it.category == "layer" }.suggestionExcluded)
        val restoredLook = restored.outfits.single { it.wardrobeId == restoredWardrobe }
        assertEquals("sage", restoredLook.background)
        assertNotEquals(look, restoredLook.id)
        val positions = OutfitCanvasState.read(restoredLook.layout)
        assertEquals(restoredItems.map { "owned:${it.id}" }.toSet(), positions.map { it.key }.toSet())
        assertEquals(OutfitCanvasState.read(layout).map { it.x }, positions.map { it.x })
        assertEquals(OutfitCanvasState.read(layout).map { it.scale }, positions.map { it.scale })
        BackupStateRules.validate(restored)
        val corrupt = File(context.cacheDir, "corrupt.zip")
        ZipOutputStream(corrupt.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write("{}".toByteArray()); zip.closeEntry()
        }
        try { backup.verify(Uri.fromFile(corrupt)); fail("Damaged manifest must fail") } catch (_: Exception) { }
        assertEquals(restored, repo.snapshot())
    }

    @Test fun manualMaskCreatesCandidateAndNeverOverwritesSavedSource() = scenario { _, repo, photos, items ->
        val original = items.first()
        val bytes = photos.currentFile(original.photoId).readBytes()
        val erase = PhotoMaskRules.Stroke(.5f, .5f, .5f, .5f, .08f, false)
        val edited = photos.mask(original.photoId, listOf(erase))
        assertNotEquals(original.photoId, edited)
        assertArrayEquals(bytes, photos.currentFile(original.photoId).readBytes())
        assertEquals(original.photoId, repo.dao.garment(original.id)!!.photoId)
        val bitmap = BitmapFactory.decodeFile(photos.currentFile(edited).path)!!
        try { assertEquals(0, Color.alpha(bitmap.getPixel(64, 64))) } finally { bitmap.recycle() }
        val restored = photos.mask(original.photoId, listOf(erase, PhotoMaskRules.Stroke(.5f, .5f, .5f, .5f, .08f, true)))
        val restoredBitmap = BitmapFactory.decodeFile(photos.currentFile(restored).path)!!
        try { assertEquals(255, Color.alpha(restoredBitmap.getPixel(64, 64))) } finally { restoredBitmap.recycle() }
    }

    @Test fun cameraQueueAndSelectedTemplateKeepDurablePhotosAndUnselectedFields() = scenario { _, repo, photos, items ->
        val fingerprint = photos.fingerprint(items.first().photoId)
        repo.enqueueCapturedImport(items.first().wardrobeId, items.first().photoId, fingerprint, "Camera")
        val row = repo.dao.allImports().single()
        assertTrue(row.inputUri.isEmpty())
        assertEquals(row.photoId, row.originalPhotoId)
        assertEquals(fingerprint, row.photoFingerprint)
        repo.dao.saveImport(row.copy(status = "working", color = "navy", style = "formal"))
        repo.applyImportTemplate(row.wardrobeId, setOf(row.id), "jumpsuit", "summer", "")
        val changed = repo.dao.importDraft(row.id)!!
        assertEquals("jumpsuit", changed.category)
        assertEquals("summer", changed.season)
        assertEquals("formal", changed.style)
        assertEquals("navy", changed.color)
        assertTrue(photos.currentFile(changed.photoId).isFile)
        repo.dao.resetInterruptedImports()
        assertEquals("queued", repo.dao.importDraft(row.id)!!.status)
    }

    @Test fun changedVerifiedArchiveIsRejectedBeforeAnyRestoreWrite() = scenario { context, repo, photos, items ->
        val backup = Backup(context, repo, photos)
        val file = File(context.cacheDir, "confirmed.zip")
        backup.export(Uri.fromFile(file))
        val beforePreview = repo.snapshot()
        val verified = backup.verify(Uri.fromFile(file))
        assertEquals(beforePreview, repo.snapshot())
        repo.saveCapsule(Capsule(wardrobeId = items.first().wardrobeId, name = "New trip"))
        backup.export(Uri.fromFile(file)) // The provider's same URI now contains a different, valid archive.
        val beforeRestore = repo.snapshot()
        val files = photos.directory.listFiles().orEmpty().map { it.name }.toSet()
        try {
            backup.restore(Uri.fromFile(file), expectedFingerprint = verified.fingerprint)
            fail("A changed confirmation target must not be imported")
        } catch (_: BackupChangedException) { }
        assertEquals(beforeRestore, repo.snapshot())
        assertEquals(files, photos.directory.listFiles().orEmpty().map { it.name }.toSet())
        assertEquals(1, repo.dao.allWardrobes().size)
        assertFalse(context.cacheDir.listFiles().orEmpty().any { it.name.startsWith("restore-") })
        val current = backup.verify(Uri.fromFile(file))
        assertNotEquals(verified.fingerprint, current.fingerprint)
        assertNotNull(backup.restore(Uri.fromFile(file), expectedFingerprint = current.fingerprint))
    }

    @Test fun zoomedMaskCoordinatesTargetTheOriginalPixels() = scenario { _, repo, photos, items ->
        val garment = items.first()
        val original = photos.currentFile(garment.photoId).readBytes()
        val frame = PhotoViewportRules.viewport(128, 128, 390f, 330f, 4f, 140f, -75f)
        val point = PhotoViewportRules.point(frame[0] + .5f * frame[2], frame[1] + .5f * frame[3], frame, false)!!
        val candidate = photos.mask(garment.photoId, listOf(PhotoMaskRules.Stroke(point[0], point[1], point[0], point[1], .04f, false)))
        val bitmap = BitmapFactory.decodeFile(photos.currentFile(candidate).path)!!
        try {
            assertEquals(0, Color.alpha(bitmap.getPixel(64, 64)))
            assertEquals(255, Color.alpha(bitmap.getPixel(5, 5)))
            assertEquals(Color.rgb(35, 70, 110), bitmap.getPixel(5, 5))
        } finally { bitmap.recycle() }
        assertArrayEquals(original, photos.currentFile(garment.photoId).readBytes())
        assertEquals(garment.photoId, repo.dao.garment(garment.id)!!.photoId)
    }

    @Test fun fiveReviewedImportsAdvancePastFailureAndRemoveCommittedOriginals() = scenario { _, repo, photos, items ->
        val order = (1..6).map { index ->
            val raw = newId()
            photos.file(raw, "png").writeBytes(photos.currentFile(items.first().photoId).readBytes())
            repo.enqueueCapturedImport(items.first().wardrobeId, raw, photos.fingerprint(raw), "Photo $index")
            repo.dao.allImports().single { it.photoId == raw }.also { repo.dao.saveImport(it.copy(status = if (index == 2) "failed" else "review")) }.id
        }
        var id: String? = order.first()
        var saved = 0
        while (id != null) {
            val row = repo.dao.importDraft(id)!!
            val finalPhoto = photos.edit(row.photoId, 0, 0f, 0f, 1f, 1f)
            repo.saveImported(row.id, Garment(id = row.id, wardrobeId = row.wardrobeId, name = row.name,
                category = "dress", color = "navy", photoId = finalPhoto), photos)
            saved++
            assertNull(repo.dao.importDraft(row.id))
            assertTrue(photos.currentFile(finalPhoto).isFile)
            assertFalse(photos.currentFile(row.originalPhotoId).exists())
            val ready = repo.dao.allImports().filter { it.wardrobeId == row.wardrobeId && it.status == "review" }.map { it.id }.toSet()
            id = ImportReviewRules.next(order, ready, emptySet(), row.id)
        }
        assertEquals(5, saved)
        assertEquals(8, repo.dao.allGarments().size)
        assertEquals("failed", repo.dao.allImports().single().status)
        assertTrue(photos.currentFile(repo.dao.allImports().single().photoId).isFile)
    }
}
