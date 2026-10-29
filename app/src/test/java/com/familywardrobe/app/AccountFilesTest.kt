package com.familywardrobe.app

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** FAM-04 (0.14.25): erasing one account's data leaves other accounts and app-wide files untouched. */
class AccountFilesTest {
    private val root: File = Files.createTempDirectory("account-files").toFile()
    private val files = File(root, "files"); private val cache = File(root, "cache"); private val noBackup = File(root, "no_backup")
    private val preferences = File(root, "shared_prefs"); private val databases = File(root, "databases")
    private val deletedPreferences = mutableListOf<String>()
    private val dirs = AccountDirectories(files, cache, noBackup, preferences, { File(databases, it) },
        { deletedPreferences += it; File(preferences, "$it.xml").delete() },
        { name -> listOf("", "-wal", "-shm", "-journal").forEach { File(databases, name + it).delete() } })
    private val alice = "a".repeat(64); private val bob = "b".repeat(64)
    @After fun cleanup() { root.deleteRecursively() }

    private fun write(file: File, text: String = "x") = file.apply { parentFile.mkdirs(); writeText(text) }
    /** A separate account: its own folders, an "account-<key>-" preference prefix and a key-named database. */
    private fun separate(key: String): List<File> = listOf(
        write(File(databases, "wardrobe-$key.db")), write(File(databases, "wardrobe-$key.db-wal")),
        write(File(files, "accounts/$key/photos/p.jpg")), write(File(files, "accounts/$key/editor-draft.json")),
        write(File(preferences, "account-$key-preferences.xml")), write(File(preferences, "account-$key-weather.xml")),
        write(File(cache, "accounts/$key/restore/x")), write(File(cache, "camera/$key/c.jpg")),
        write(File(noBackup, "family-additions/$key/remote.json")), write(File(noBackup, "family-sync/$key/remote.json")),
        write(File(noBackup, "family-read/$key/session/head.zip")))
    private fun appWide(): List<File> = listOf(write(File(files, "account-storage-v1.json")), write(File(files, "account-erasure-v1.json")),
        write(File(files, "datastore/wardrobe_appearance_v1.preferences_pb")), write(File(preferences, "shop-share-inbox.xml")),
        write(File(files, "PersistedInstallation.json")))

    @Test fun separateAccountIsErasedAndOthersStay() {
        val gone = separate(alice); val kept = separate(bob) + appWide()
        AccountFiles.erase(dirs, AccountLocation(alice, legacy = false))
        gone.forEach { assertFalse("$it should be erased", it.exists()) }
        kept.forEach { assertTrue("$it should stay", it.exists()) }
        assertEquals(setOf("account-$alice-preferences", "account-$alice-weather"), deletedPreferences.toSet())
    }
    @Test fun legacyAccountLosesOnlyItsListedFilesAndPreferences() {
        val legacy = listOf(write(File(databases, "wardrobe.db")), write(File(files, "photos/p.jpg")), write(File(files, "backup-staging/s.zip")),
            write(File(files, "family-transfer/t.zip")), write(File(files, "editor-draft.json")), write(File(files, "editor-draft.json.bak")),
            write(File(files, "family-migration-v1.json")), write(File(files, "photo-reprocess.json")),
            write(File(files, "cloud-rescue-1.zip")), write(File(files, "cloud-before-replace-2.zip")),
            write(File(preferences, "preferences.xml")), write(File(preferences, "backup-health.xml")), write(File(preferences, "weather.xml")),
            write(File(cache, "accounts/$alice/x")), write(File(noBackup, "family-outfits/$alice/state.json")))
        val kept = separate(bob) + appWide()
        AccountFiles.erase(dirs, AccountLocation(alice, legacy = true))
        legacy.forEach { assertFalse("$it should be erased", it.exists()) }
        kept.forEach { assertTrue("$it should stay", it.exists()) }
        assertTrue(deletedPreferences.containsAll(AccountFiles.LEGACY_PREFERENCES))
    }
    @Test fun aDatabaseThatCannotBeDeletedFailsTheErasure() {
        separate(alice)
        val stubborn = AccountDirectories(files, cache, noBackup, preferences, { File(databases, it) }, { }, { })
        assertTrue(runCatching { AccountFiles.erase(stubborn, AccountLocation(alice, legacy = false)) }.isFailure)
    }
    @Test fun erasingAnAccountWithoutDataIsHarmless() {
        val kept = separate(bob) + appWide()
        AccountFiles.erase(dirs, AccountLocation(alice, legacy = false))
        kept.forEach { assertTrue(it.exists()) }
    }
}
