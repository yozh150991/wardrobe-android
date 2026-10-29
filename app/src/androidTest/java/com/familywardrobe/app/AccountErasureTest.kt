package com.familywardrobe.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * FAM-04 (0.14.25): the local erasure after an account deletion, including the marker that finishes it after a crash.
 * Uses random separate accounts that are never registered, so the installation's registry is not changed.
 */
@RunWith(AndroidJUnit4::class)
class AccountErasureTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WardrobeApplication
    private val deleted = AuthRules.accountKey("tests", "deleted-${UUID.randomUUID()}")
    private val other = AuthRules.accountKey("tests", "other-${UUID.randomUUID()}")
    @After fun cleanup() {
        AccountErasure.clear(app, deleted); AccountErasure.clear(app, other)
        runBlocking { AccountFiles.erase(directories(), AccountLocation(other, false)) }
    }
    private fun directories() = AccountDirectories(app.filesDir, app.cacheDir, app.noBackupFilesDir,
        File(app.applicationInfo.dataDir, "shared_prefs"), app::getDatabasePath, { app.deleteSharedPreferences(it) }, { app.deleteDatabase(it) })

    /** A real Room database, photo, preferences, cache and a shared-draft journal for [key]. */
    private fun populate(key: String): List<File> {
        val location = AccountLocation(key, false)
        val context = AccountContext(app, location)
        Room.databaseBuilder(app, WardrobeDatabase::class.java, location.databaseName).addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build()
            .also { db -> db.openHelper.writableDatabase; db.close() }
        val photo = PhotoStore(context).file(newId()).apply { writeText("photo") }
        context.getSharedPreferences("preferences", 0).edit().putString("wardrobe", key).commit()
        val journal = File(app.noBackupFilesDir, "family-additions/$key/${newId()}.json").apply { parentFile!!.mkdirs(); writeText("{}") }
        val cached = File(context.cacheDir, "x").apply { writeText("cache") }
        return listOf(app.getDatabasePath(location.databaseName), photo, journal, cached,
            File(app.applicationInfo.dataDir, "shared_prefs/account-$key-preferences.xml"))
    }

    @Test fun deletedAccountIsErasedAtResumeAndAnotherAccountStays() = runBlocking {
        val gone = populate(deleted); val kept = populate(other)
        gone.forEach { assertTrue("$it prepared", it.exists()) }
        AccountErasure.mark(app, deleted, AccountErasure.Stage.DELETED)
        assertEquals(listOf(deleted), AccountErasure.resume(app))
        gone.forEach { assertFalse("$it should be erased", it.exists()) }
        kept.forEach { assertTrue("$it should stay", it.exists()) }
        assertFalse(deleted in AccountErasure.pending(app))
        assertEquals(other, AccountContext(app, AccountLocation(other, false)).getSharedPreferences("preferences", 0).getString("wardrobe", null))
    }
    @Test fun requestedDeletionIsErasedOnlyWhenFirebaseReportsTheUserGone() = runBlocking {
        val files = populate(deleted)
        AccountErasure.mark(app, deleted, AccountErasure.Stage.REQUESTED)
        assertEquals(emptyList<String>(), AccountErasure.resume(app))
        files.forEach { assertTrue("$it must stay while the deletion is unconfirmed", it.exists()) }
        assertEquals(AccountErasure.Stage.REQUESTED, AccountErasure.pending(app)[deleted])
        assertEquals(listOf(deleted), AccountErasure.resume(app, gone = deleted))
        files.forEach { assertFalse(it.exists()) }
    }
    @Test fun theOpenAccountIsNeverErased() = runBlocking {
        val files = populate(deleted)
        AccountErasure.mark(app, deleted, AccountErasure.Stage.DELETED)
        assertEquals(emptyList<String>(), AccountErasure.resume(app, gone = deleted, open = deleted))
        files.forEach { assertTrue(it.exists()) }
    }
    @Test fun markerKeepsSeveralAccountsAndADamagedMarkerErasesNothing() = runBlocking {
        AccountErasure.mark(app, deleted, AccountErasure.Stage.REQUESTED)
        AccountErasure.mark(app, other, AccountErasure.Stage.DELETED)
        assertEquals(mapOf(deleted to AccountErasure.Stage.REQUESTED, other to AccountErasure.Stage.DELETED),
            AccountErasure.pending(app).filterKeys { it in setOf(deleted, other) })
        AccountErasure.clear(app, other)
        assertEquals(AccountErasure.Stage.REQUESTED, AccountErasure.pending(app)[deleted])
        val marker = File(app.filesDir, "account-erasure-v1.json")
        val saved = marker.readText()
        try {
            marker.writeText("broken")
            assertEquals(emptyMap<String, AccountErasure.Stage>(), AccountErasure.pending(app))
            assertEquals(emptyList<String>(), AccountErasure.resume(app))
        } finally { marker.writeText(saved) }
    }
}
