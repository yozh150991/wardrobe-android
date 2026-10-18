package com.familywardrobe.app

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AccountStorageTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val alice = AuthRules.accountKey("tests", "alice")
    private val bob = AuthRules.accountKey("tests", "bob")

    // This fixture never reads or changes the real installation's ownership registry/legacy DB.
    private fun withRegistry(block: (Context, AccountRegistry) -> Unit) {
        val directory = File(base.cacheDir, "account-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getDatabasePath(name: String) = File(directory, "databases/$name").apply { parentFile?.mkdirs() }
        }
        try { block(context, AccountRegistry(context)) } finally { directory.deleteRecursively() }
    }
    @Test fun legacyRequiresExplicitClaimAndRemainsAtOriginalLocation() = withRegistry { context, registry ->
        context.getDatabasePath("wardrobe.db").writeText("untouched fixture")
        assertTrue(registry.needsLegacyChoice(alice))
        val claimed = registry.location(alice, claimLegacy = true)
        assertEquals("wardrobe.db", claimed.databaseName)
        assertTrue(claimed.legacy)
        assertEquals("untouched fixture", context.getDatabasePath("wardrobe.db").readText())
        assertFalse(registry.needsLegacyChoice(bob))
        assertFalse(registry.location(bob).legacy)
        assertTrue(AccountRegistry(context).location(alice).legacy)
    }
    @Test fun startingEmptyDoesNotClaimOrDeleteOldData() = withRegistry { context, registry ->
        context.getDatabasePath("wardrobe.db").writeText("kept")
        assertFalse(registry.location(alice).legacy)
        assertFalse(registry.needsLegacyChoice(alice))
        assertTrue(registry.needsLegacyChoice(bob))
        assertEquals("kept", context.getDatabasePath("wardrobe.db").readText())
    }
    @Test fun anotherAccountCannotTakeAnExistingClaim() = withRegistry { _, registry ->
        registry.location(alice, claimLegacy = true)
        assertThrows(IllegalArgumentException::class.java) { registry.location(bob, claimLegacy = true) }
        assertTrue(registry.location(alice).legacy)
    }
    @Test fun corruptRegistryFailsClosedWithoutReassigningLegacy() = withRegistry { context, registry ->
        val file = File(context.filesDir, "account-storage-v1.json")
        file.writeText("broken")
        assertThrows(Exception::class.java) { registry.needsLegacyChoice(alice) }
        assertThrows(Exception::class.java) { registry.location(alice, claimLegacy = true) }
        assertEquals("broken", file.readText())
    }
    @Test fun photoAndPreferencePathsAreAccountScoped() {
        val first = AccountContext(base, AccountLocation(AuthRules.accountKey("tests", UUID.randomUUID().toString()), false))
        val second = AccountContext(base, AccountLocation(AuthRules.accountKey("tests", UUID.randomUUID().toString()), false))
        try {
            val id = newId()
            PhotoStore(first).file(id).writeText("first photo")
            first.getSharedPreferences("preferences", 0).edit().putString("wardrobe", "first").commit()
            assertFalse(PhotoStore(second).file(id).exists())
            assertNull(second.getSharedPreferences("preferences", 0).getString("wardrobe", null))
            assertNotEquals(first.filesDir.canonicalPath, second.filesDir.canonicalPath)
            assertNotEquals(first.cacheDir.canonicalPath, second.cacheDir.canonicalPath)
        } finally {
            listOf(first, second).forEach {
                it.filesDir.deleteRecursively(); it.cacheDir.deleteRecursively()
                base.deleteSharedPreferences("account-${it.location.key}-preferences")
                base.deleteSharedPreferences("account-${it.location.key}-photo-cleanup")
            }
        }
    }
    @Test fun databasesIsolateAccountsAndReopenSavedItems() = runBlocking {
        val a = AccountLocation(AuthRules.accountKey("tests", UUID.randomUUID().toString()), false)
        val b = AccountLocation(AuthRules.accountKey("tests", UUID.randomUUID().toString()), false)
        fun open(location: AccountLocation) = Room.databaseBuilder(base, WardrobeDatabase::class.java, location.databaseName)
            .addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build()
        var first = open(a)
        val second = open(b)
        try {
            Repository(first).initialize("Alice")
            Repository(second).initialize("Bob")
            first.dao().insertWish(Wish(wardrobeId = Repository.DEFAULT_WARDROBE, name = "Only Alice", category = "top"))
            assertEquals(1, first.dao().allWishes().size)
            assertTrue(second.dao().allWishes().isEmpty())
            first.close(); first = open(a)
            assertEquals("Only Alice", first.dao().allWishes().single().name)
            assertEquals("Bob", second.dao().allWardrobes().single().name)
        } finally { first.close(); second.close(); base.deleteDatabase(a.databaseName); base.deleteDatabase(b.databaseName) }
    }
}
