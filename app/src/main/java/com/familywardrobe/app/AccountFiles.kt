package com.familywardrobe.app

import java.io.File

/** The app's storage roots; tests pass temporary folders. Preferences and databases go through the platform calls. */
internal class AccountDirectories(val files: File, val cache: File, val noBackup: File, val preferences: File,
    val database: (String) -> File, val deletePreferences: (String) -> Unit, val deleteDatabase: (String) -> Unit)

/**
 * FAM-04 (0.14.25): every place one account's data lives on this device. A separate account has its own folders
 * and "account-<key>-" preferences; the old global storage (legacy) shares the app's folders, so its account files
 * and preference names are listed (tools/check_account_erasure.py fails when a new account store is missing here).
 * Nothing of another account and nothing app-wide (registry, erasure marker, appearance, shared-link inbox) is touched.
 */
internal object AccountFiles {
    /** Account-scoped preference files of the old global storage. */
    val LEGACY_PREFERENCES = listOf("preferences", "backup-health", "photo-cleanup", "import-photo-diagnostics", "weather")
    /** Account files of the old global storage; everything else in that folder belongs to the app or other accounts. */
    val LEGACY_FILES = listOf("photos", "backup-staging", "family-transfer", "editor-draft.json",
        "family-migration-v1.json", "photo-reprocess.json")
    val LEGACY_PREFIXES = listOf("cloud-rescue-", "cloud-before-replace-")

    fun erase(dirs: AccountDirectories, location: AccountLocation) {
        val key = location.key
        dirs.deleteDatabase(location.databaseName)
        if (location.legacy) {
            // AtomicFile keeps ".bak" (and on newer Android ".new") next to the file.
            for (name in LEGACY_FILES) for (variant in listOf(name, "$name.bak", "$name.new")) File(dirs.files, variant).deleteRecursively()
            dirs.files.listFiles().orEmpty().filter { f -> LEGACY_PREFIXES.any { f.name.startsWith(it) } }.forEach { it.deleteRecursively() }
            LEGACY_PREFERENCES.forEach(dirs.deletePreferences)
        } else File(dirs.files, "accounts/$key").deleteRecursively()
        dirs.preferences.listFiles().orEmpty().filter { it.name.startsWith("account-$key-") && it.name.endsWith(".xml") }
            .forEach { dirs.deletePreferences(it.name.removeSuffix(".xml")) }
        File(dirs.cache, "accounts/$key").deleteRecursively(); File(dirs.cache, "camera/$key").deleteRecursively()
        // Shared drafts, journals and read caches: <noBackup>/<kind>/<account key>.
        dirs.noBackup.listFiles().orEmpty().filter { it.isDirectory }.forEach { File(it, key).deleteRecursively() }
        check(!dirs.database(location.databaseName).exists()) { "Database still present" }
        check(!File(dirs.files, if (location.legacy) "photos" else "accounts/$key").exists()) { "Account files still present" }
    }
}
