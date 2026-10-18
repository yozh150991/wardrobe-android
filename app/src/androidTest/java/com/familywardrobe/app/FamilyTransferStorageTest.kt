package com.familywardrobe.app

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** Native storage integration; run with the other deferred Android acceptance tests. */
class FamilyTransferStorageTest {
    private fun scenario(block: suspend (AccountContext,AuthIdentity,Repository,PhotoStore) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val account = AuthIdentity("family-transfer-test",newId(),"owner@example.test")
        val context = AccountContext(base,AccountLocation(account.key,false)); val name = "family-${newId()}.db"
        val db = Room.databaseBuilder(context,WardrobeDatabase::class.java,name).build()
        try { val repo = Repository(db); repo.initialize("Home"); block(context,account,repo,PhotoStore(context)) }
        finally { db.close(); context.deleteDatabase(name); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively() }
    }
    @Test fun actualArchiveIsScopedDurableAndMatchesEnvelope() = scenario { context,account,repo,photos ->
        val own = repo.snapshot().wardrobes.single(); val other = Wardrobe(name="Private"); repo.dao.insertWardrobe(other)
        for (ward in listOf(own,other)) {
            val id = newId(); val bitmap = Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
            try { photos.file(id,"png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() }
            repo.dao.saveGarment(Garment(wardrobeId=ward.id,name="Shirt",category="top",color="white",photoId=id))
        }
        val before = repo.snapshot(); val store = FamilyMigrationStore(context,account)
        val archive = FamilyArchive(context,repo,photos,store,{})
        val record = archive.stage(archive.prepare(own.id)); val file = archive.checkedFile(record)
        assertEquals(record.fingerprint,FamilyArchive.digest(file)); assertEquals(file.length(),record.archiveSize)
        ZipFile(file).use { zip ->
            val text = zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().use { it.readText() }
            val selected = FamilyEnvelope(Backup(context,repo,photos)).decode(text,record.identity,0)
            assertEquals(WardrobeSnapshots.select(before,own.id),selected)
            assertEquals(setOf("manifest.json")+selected.photoIds().map { "photos/$it.png" },zip.entries().asSequence().map { it.name }.toSet())
            assertFalse(JSONObject(text).has("members"))
        }
        val reopened = FamilyArchive(context,repo,photos,FamilyMigrationStore(context,account),{})
        assertEquals(record,reopened.stage(reopened.prepare(own.id))); assertEquals(before,repo.snapshot())
    }
    @Test fun rotateKeepsRootAndRejectsOldCallback() = scenario { context,account,repo,photos ->
        val store = FamilyMigrationStore(context,account); val archive = FamilyArchive(context,repo,photos,store,{})
        val old = archive.stage(archive.prepare(repo.snapshot().wardrobes.single().id)); val next = archive.rotate(old,false)
        assertEquals(old.remoteId,next.remoteId); assertEquals(old.fingerprint,next.fingerprint); assertNotEquals(old.archiveId,next.archiveId)
        try { store.uploaded(old); fail("Old upload accepted") } catch (_: IllegalArgumentException) {}
        assertEquals(next,store.records().single()); assertTrue(archive.checkedFile(next).isFile)
        val reset = archive.rotate(next,true); assertEquals(FamilyMigrationPhase.PREPARED,reset.phase)
        assertEquals(next.remoteId,reset.remoteId); assertEquals(0L,reset.archiveSize)
    }
    @Test fun corruptBytesCannotBeUploadedOrSilentlyRebuilt() = scenario { context,account,repo,photos ->
        val archive = FamilyArchive(context,repo,photos,FamilyMigrationStore(context,account),{})
        val record = archive.stage(archive.prepare(repo.snapshot().wardrobes.single().id)); archive.checkedFile(record).writeText("corrupt")
        assertEquals(record,archive.stage(record))
        try { archive.checkedFile(record); fail("Corruption accepted") } catch (e: FamilyTransferException) { assertEquals(FamilyProblem.ARCHIVE,e.problem) }
    }
}
