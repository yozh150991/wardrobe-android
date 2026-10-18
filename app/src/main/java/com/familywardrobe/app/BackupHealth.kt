package com.familywardrobe.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** All foreground and scheduled repositories for an account share the DB + photo write lock. */
internal object AccountWriteLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()
    fun forDatabase(name: String): Mutex = locks.computeIfAbsent(name) { Mutex() }
}

data class BackupHealth(val lastSuccess: Long = 0, val lastAttempt: Long = 0,
    val failed: Boolean = false, val folder: String = "", val folderName: String = "", val enabled: Boolean = false) {
    fun overdue(now: Long = System.currentTimeMillis()) = lastSuccess == 0L || now - lastSuccess >= 7L * 86_400_000
}
data class BackupVerification(val wardrobes: Int, val garments: Int, val outfits: Int, val fingerprint: String = "")
data class RestorePreview(val uri: String, val verification: BackupVerification)
enum class ScheduledBackupState { IDLE, SCHEDULED, QUEUED, BLOCKED, RUNNING }

class BackupHealthStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("backup-health", Context.MODE_PRIVATE)
    fun read() = BackupHealth(prefs.getLong("success", 0), prefs.getLong("attempt", 0),
        prefs.getBoolean("failed", false), prefs.getString("folder", "").orEmpty(),
        prefs.getString("folderName", "").orEmpty(), prefs.getBoolean("enabled", false))
    val changes = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun attempt() { check(prefs.edit().putLong("attempt", System.currentTimeMillis()).commit()) }
    fun success() { check(prefs.edit().putLong("success", System.currentTimeMillis()).putBoolean("failed", false).commit()) }
    fun failure() { prefs.edit().putBoolean("failed", true).commit() }
    fun revision(): String = prefs.getString("revision", "").orEmpty()
    fun chooseFolder(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val folder = requireNotNull(DocumentFile.fromTreeUri(context, uri))
        require(folder.isDirectory && folder.canRead() && folder.canWrite())
        check(prefs.edit().putString("folder", uri.toString()).putString("folderName", folder.name.orEmpty())
            .putBoolean("enabled", false).putString("revision", newId()).commit())
    }
    fun setEnabled(enabled: Boolean) {
        require(!enabled || read().folder.isNotBlank())
        check(prefs.edit().putBoolean("enabled", enabled).putString("revision", newId()).commit())
    }
}

internal object BackupSchedule {
    private fun name(key: String) = "wardrobe-backup-$key"
    fun states(context: Context, key: String) = WorkManager.getInstance(context.applicationContext).let { manager ->
        combine(manager.getWorkInfosForUniqueWorkFlow(name(key)), manager.getWorkInfosForUniqueWorkFlow(name(key) + "-now")) { periodic, immediate ->
            when {
                (periodic + immediate).any { it.state == WorkInfo.State.RUNNING } -> ScheduledBackupState.RUNNING
                immediate.any { it.state == WorkInfo.State.BLOCKED } -> ScheduledBackupState.BLOCKED
                immediate.any { it.state == WorkInfo.State.ENQUEUED } -> ScheduledBackupState.QUEUED
                periodic.any { !it.state.isFinished } -> ScheduledBackupState.SCHEDULED
                else -> ScheduledBackupState.IDLE
            }
        }
    }
    fun update(context: Context, key: String, enabled: Boolean) {
        require(AuthRules.validAccountKey(key))
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) { manager.cancelUniqueWork(name(key)); manager.cancelUniqueWork(name(key) + "-now"); return }
        val request = PeriodicWorkRequestBuilder<ScheduledBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.DAYS)
            .setInputData(workDataOf("account" to key))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
            .build()
        manager.enqueueUniquePeriodicWork(name(key), ExistingPeriodicWorkPolicy.UPDATE, request)
        manager.enqueueUniqueWork(name(key) + "-now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ScheduledBackupWorker>().setInputData(workDataOf("account" to key)).build())
    }
}

/** The worker only opens the currently authenticated account; it never selects another user's data. */
class ScheduledBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WardrobeApplication
        val key = inputData.getString("account") ?: return Result.failure()
        if (!AuthRules.validAccountKey(key) || app.authBackend.signedInIdentity()?.key != key) return Result.success()
        app.accountClosures[key]?.join()
        val location = app.accountRegistry.location(key)
        val account = AccountContext(app, location)
        val health = BackupHealthStore(account)
        val selected = health.read()
        if (!selected.enabled || selected.folder.isBlank()) return Result.success()
        val revision = health.revision()
        val database = Room.databaseBuilder(app, WardrobeDatabase::class.java, location.databaseName)
            .addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build()
        var document: DocumentFile? = null
        fun guard() {
            check(app.authBackend.signedInIdentity()?.key == key && health.read().enabled && health.revision() == revision)
        }
        try {
            guard(); health.attempt()
            val folder = requireNotNull(DocumentFile.fromTreeUri(account, Uri.parse(selected.folder)))
            require(folder.isDirectory && folder.canRead() && folder.canWrite())
            val target = requireNotNull(folder.createFile("application/zip", "wardrobe-${key.take(12)}-${System.currentTimeMillis()}-${newId().take(8)}.zip"))
            document = target
            guard()
            val repo = Repository(database, AccountWriteLocks.forDatabase(location.databaseName))
            Backup(account, repo, PhotoStore(account)).export(target.uri, ::guard)
            guard(); health.success()
            document = null // Only a complete, verified copy survives.
            return Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            if (app.authBackend.signedInIdentity()?.key == key && health.revision() == revision) health.failure()
            return if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            runCatching { document?.delete() }
            database.close()
        }
    }
}
