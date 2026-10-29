package com.familywardrobe.app

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

internal object FamilySyncSchedule {
    private fun tag(key: String) = "wardrobe-family-$key"
    private fun name(key: String, remote: String) = "${tag(key)}-$remote"
    /** [continuation]: queued by a running worker after progress, so it starts fresh instead of using retry backoff. */
    fun enqueue(context: Context, key: String, plan: FamilySyncPlan, continuation: Boolean = false) {
        require(plan.background && plan.active)
        val request = OneTimeWorkRequestBuilder<FamilySyncWorker>()
            .setInputData(workDataOf("account" to key, "remote" to plan.remote, "plan" to plan.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).addTag(tag(key)).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(name(key, plan.remote),
            if (continuation) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
    }
    fun cancel(context: Context, key: String, remote: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(name(key, remote))
    }
    fun signOut(context: AccountContext, identity: AuthIdentity) {
        FamilySyncLocks.signOut(identity.key)
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(tag(identity.key))
        // A damaged journal already fails closed. Cancellation and the epoch also stop an in-flight worker.
        runCatching { FamilySyncStore(context, identity).pauseAll() }
    }
}
private const val MAX_ATTEMPTS = 5
class FamilySyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WardrobeApplication
        val key = inputData.getString("account") ?: return Result.failure()
        val remote = inputData.getString("remote") ?: return Result.failure()
        val id = inputData.getString("plan") ?: return Result.failure()
        if (!AuthRules.validAccountKey(key) || !FamilyRules.validId(remote) || !FamilyRules.validId(id)) return Result.failure()
        val identity = app.authBackend.signedInIdentity()?.takeIf { it.key == key } ?: return Result.success()
        try {
            val context = AccountContext(app, app.accountRegistry.location(key))
            val store = FamilySyncStore(context, identity)
            val plan = store.get(remote)?.takeIf { it.id == id && it.background && it.active } ?: return Result.success()
            // Count every start without progress, including runs Android stopped at its time limit: one
            // operation that never fits would otherwise upload the same archive forever (audit L-02).
            val attempt = if (plan.attemptCursor == plan.cursor) plan.attempts + 1 else 1
            if (attempt > MAX_ATTEMPTS) {
                store.failure(remote, id, "RETRY_LIMIT")
                return Result.failure()
            }
            store.put(plan.copy(attempts = attempt, attemptCursor = plan.cursor), plan)
            if (FamilySyncEngine(app, context, identity, remote, id, background = true).run()) return Result.success()
            val after = store.get(remote)?.takeIf { it.id == id && it.active && it.background } ?: return Result.success()
            if (after.status == FamilySyncStatus.RETRY) return Result.retry()
            // Progress within the time budget: continue in a new request, not with growing retry backoff.
            FamilySyncSchedule.enqueue(applicationContext, key, after, continuation = true)
            return Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return Result.failure() }
    }
}
