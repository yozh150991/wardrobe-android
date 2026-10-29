package com.familywardrobe.app

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

internal object FamilySyncSchedule {
    private fun tag(key: String) = "wardrobe-family-$key"
    private fun name(key: String, remote: String) = "${tag(key)}-$remote"
    private fun autoName(key: String, remote: String) = "${tag(key)}-auto-$remote"
    /**
     * Any network (0.14.23, the user's choice): shared changes are small since the delta model (a change text and at
     * most one photo); only a full-archive draft prepared before the server update can still be large.
     */
    private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    /** [continuation]: queued by a running worker after progress, so it starts fresh instead of using retry backoff. */
    fun enqueue(context: Context, key: String, plan: FamilySyncPlan, continuation: Boolean = false) {
        require(plan.background && plan.active)
        val request = OneTimeWorkRequestBuilder<FamilySyncWorker>()
            .setInputData(workDataOf("account" to key, "remote" to plan.remote, "plan" to plan.id))
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).addTag(tag(key)).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(name(key, plan.remote),
            if (continuation) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
    }
    /**
     * Wakes automatic sending for one wardrobe. A run that has not started yet will see the newest queue, so it is
     * kept; a running one gets one follow-up, so a draft saved during its last check is never left behind.
     */
    fun kick(context: Context, key: String, remote: String) {
        val manager = WorkManager.getInstance(context.applicationContext)
        val states = runCatching { manager.getWorkInfosForUniqueWork(autoName(key, remote)).get().map { it.state } }.getOrDefault(emptyList())
        if (states.count { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED } > 0) return
        val request = OneTimeWorkRequestBuilder<FamilyAutoSyncWorker>()
            .setInputData(workDataOf("account" to key, "remote" to remote))
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).addTag(tag(key)).build()
        manager.enqueueUniqueWork(autoName(key, remote),
            if (WorkInfo.State.RUNNING in states) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
    }
    fun cancel(context: Context, key: String, remote: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(name(key, remote))
    }
    fun cancelAuto(context: Context, key: String, remote: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(autoName(key, remote))
    }
    fun signOut(context: AccountContext, identity: AuthIdentity) {
        FamilySyncLocks.signOut(identity.key)
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(tag(identity.key))
        // A damaged journal already fails closed. Cancellation and the epoch also stop an in-flight worker.
        runCatching { FamilySyncStore(context, identity).pauseAll() }
    }
}
private const val MAX_ATTEMPTS = 5
internal enum class FamilyBackgroundRun { DONE, CONTINUE, RETRY, STOP }

/** One background run of a stored batch; shared by approved batches and automatic sending. */
internal suspend fun runFamilyBackgroundPlan(app: WardrobeApplication, context: AccountContext, identity: AuthIdentity,
    remote: String, id: String): FamilyBackgroundRun {
    val store = FamilySyncStore(context, identity)
    val plan = store.get(remote)?.takeIf { it.id == id && it.background && it.active } ?: return FamilyBackgroundRun.STOP
    // Count every start without progress, including runs Android stopped at its time limit: one
    // operation that never fits would otherwise upload the same archive forever (audit L-02).
    val attempt = if (plan.attemptCursor == plan.cursor) plan.attempts + 1 else 1
    if (attempt > MAX_ATTEMPTS) {
        store.failure(remote, id, "RETRY_LIMIT")
        return FamilyBackgroundRun.STOP
    }
    store.put(plan.copy(attempts = attempt, attemptCursor = plan.cursor), plan)
    if (FamilySyncEngine(app, context, identity, remote, id, background = true).run()) return FamilyBackgroundRun.DONE
    val after = store.get(remote)?.takeIf { it.id == id && it.active && it.background } ?: return FamilyBackgroundRun.STOP
    return if (after.status == FamilySyncStatus.RETRY) FamilyBackgroundRun.RETRY else FamilyBackgroundRun.CONTINUE
}

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
            return when (runFamilyBackgroundPlan(app, context, identity, remote, id)) {
                FamilyBackgroundRun.DONE, FamilyBackgroundRun.STOP -> Result.success()
                FamilyBackgroundRun.RETRY -> Result.retry()
                FamilyBackgroundRun.CONTINUE -> {
                    // Progress within the time budget: continue in a new request, not with growing retry backoff.
                    FamilySyncStore(context, identity).get(remote)?.let { FamilySyncSchedule.enqueue(applicationContext, key, it, continuation = true) }
                    Result.success()
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return Result.failure() }
    }
}

/**
 * Automatic sending of one wardrobe's queue (0.14.23). Each round takes the whole current queue as a new background
 * batch (the switch is the approval) and runs it with the same engine; drafts saved meanwhile get the next round.
 */
class FamilyAutoSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WardrobeApplication
        val key = inputData.getString("account") ?: return Result.failure()
        val remote = inputData.getString("remote") ?: return Result.failure()
        if (!AuthRules.validAccountKey(key) || !FamilyRules.validId(remote)) return Result.failure()
        val identity = app.authBackend.signedInIdentity()?.takeIf { it.key == key } ?: return Result.success()
        try {
            val context = AccountContext(app, app.accountRegistry.location(key))
            val store = FamilySyncStore(context, identity)
            repeat(ROUNDS) {
                val plan = FamilySyncLocks.account(key).withLock {
                    val setting = store.auto(remote); val current = store.get(remote)
                    if (!setting.enabled) {
                        // Switched off: no new automatic batch, but one already started finishes as the approved batch it is.
                        current?.takeIf { it.active && it.background }?.let { FamilySyncSchedule.enqueue(applicationContext, key, it, continuation = true) }
                        return Result.success()
                    }
                    when (val step = FamilyAutoSync.decide(current, FamilySyncOperations(context, identity, remote).snapshot(), setting.released)) {
                        FamilyAutoSync.Step.Idle, FamilyAutoSync.Step.Wait -> return Result.success()
                        is FamilyAutoSync.Step.Continue -> step.plan
                        is FamilyAutoSync.Step.Start -> FamilySyncPlan(newId(), remote, step.operations, background = true).also { store.put(it, current) }
                    }
                }
                when (runFamilyBackgroundPlan(app, context, identity, remote, plan.id)) {
                    FamilyBackgroundRun.DONE -> Unit
                    FamilyBackgroundRun.STOP -> return Result.success()
                    FamilyBackgroundRun.RETRY -> return Result.retry()
                    FamilyBackgroundRun.CONTINUE -> { FamilySyncSchedule.kick(applicationContext, key, remote); return Result.success() }
                }
            }
            FamilySyncSchedule.kick(applicationContext, key, remote)
            return Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return Result.failure() }
    }
    private companion object { const val ROUNDS = 5 }
}
