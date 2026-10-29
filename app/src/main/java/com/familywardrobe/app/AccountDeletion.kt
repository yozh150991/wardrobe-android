package com.familywardrobe.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * FAM-04 (0.14.25): erases one account's data on this device after the server deleted the account.
 *
 * A small app-wide marker survives process death between the server deletion and the local erasure:
 * REQUESTED is written before the server call, DELETED right after its success. At the next start a DELETED
 * account is erased; a REQUESTED one only when Firebase reports that the user no longer exists.
 */
internal object AccountErasure {
    enum class Stage { REQUESTED, DELETED }
    private const val MARKER = "account-erasure-v1.json"

    private fun file(context: Context) = AtomicFile(File(context.applicationContext.filesDir, MARKER))
    private fun read(context: Context): Map<String, Stage> {
        val file = file(context)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyMap()
        // A damaged marker never erases anything; it is replaced by the next write.
        return runCatching {
            val json = JSONObject(file.readFully().toString(Charsets.UTF_8))
            require(json.getInt("version") == 1)
            val accounts = json.getJSONObject("accounts")
            accounts.keys().asSequence().associateWith { Stage.valueOf(accounts.getString(it)) }
                .also { require(it.keys.all(AuthRules::validAccountKey)) }
        }.getOrDefault(emptyMap())
    }
    private fun write(context: Context, accounts: Map<String, Stage>) {
        val file = file(context)
        if (accounts.isEmpty()) { file.delete(); return }
        val json = JSONObject().put("version", 1).put("accounts", JSONObject().apply { accounts.forEach { (k, v) -> put(k, v.name) } })
        val out = file.startWrite()
        try { out.write(json.toString().toByteArray(Charsets.UTF_8)); out.fd.sync(); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    @Synchronized fun mark(context: Context, key: String, stage: Stage) {
        require(AuthRules.validAccountKey(key)); write(context, read(context) + (key to stage))
    }
    @Synchronized fun pending(context: Context): Map<String, Stage> = read(context)
    @Synchronized fun clear(context: Context, key: String) {
        val accounts = read(context)
        if (key in accounts) write(context, accounts - key)
    }

    private val erasing = Mutex()
    /** Erases every DELETED account (and [gone], which Firebase reported as deleted) except the [open] one. */
    suspend fun resume(app: WardrobeApplication, gone: String? = null, open: String? = null): List<String> = erasing.withLock {
        val erased = mutableListOf<String>()
        for ((key, stage) in pending(app)) {
            if (key == open || stage != Stage.DELETED && key != gone) continue
            // A failed erasure keeps its marker and is repeated at the next start; the others still go ahead.
            if (runCatching { erase(app, key) }.isSuccess) { clear(app, key); erased += key }
        }
        erased
    }

    /** The caller has signed the account out and waited for its session to close. */
    suspend fun erase(app: WardrobeApplication, key: String) = withContext(Dispatchers.IO) {
        require(AuthRules.validAccountKey(key))
        val location = app.accountRegistry.known(key) ?: AccountLocation(key, legacy = false)
        // Workers check the signed-in account before they start; cancellation stops a running one.
        CloudSchedule.enabled(app, key, false); BackupSchedule.update(app, key, false); FamilySyncSchedule.cancelAll(app, key)
        withTimeoutOrNull(30_000) {
            combine(CloudSchedule.running(app, key), BackupSchedule.states(app, key), FamilySyncSchedule.running(app, key)) { cloud, backup, family ->
                cloud || backup == ScheduledBackupState.RUNNING || family
            }.first { !it }
        }
        // The chosen backup folder keeps the user's ZIP files; only this app's access to it ends.
        runCatching {
            val folder = BackupHealthStore(AccountContext(app, location)).read().folder
            if (folder.isNotBlank()) app.contentResolver.releasePersistableUriPermission(Uri.parse(folder),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        AccountFiles.erase(AccountDirectories(app.filesDir, app.cacheDir, app.noBackupFilesDir,
            File(app.applicationInfo.dataDir, "shared_prefs"), app::getDatabasePath,
            { app.deleteSharedPreferences(it) }, { app.deleteDatabase(it) }), location)
        app.accountRegistry.forget(key)
    }
}

internal enum class AccountDeletionProblem { PASSWORD, RATE, REAUTH, CHOICE, EMAIL, CONFIG, NETWORK, RETRY }
internal data class AccountDeletionState(val loading: Boolean = false, val loaded: Boolean = false, val running: Boolean = false,
    val owned: List<FamilyOwnedWardrobe> = emptyList(), val memberships: Int = 0, val problem: AccountDeletionProblem? = null,
    val done: Boolean = false)

/**
 * Deletes the signed-in account: every owned shared wardrobe goes to the editor the user chose (or its only editor)
 * or is deleted for everyone when it has none, then the server removes memberships, the private cloud copy and the
 * Firebase user. The local data is erased afterwards by [AuthViewModel.accountDeleted].
 */
internal class AccountDeletionController(private val app: WardrobeApplication, private val session: AccountSession,
    private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(AccountDeletionState())
    val state = mutable.asStateFlow()
    private val lock = Mutex()
    // One request per open dialog: a retry after a lost response repeats the same server steps.
    @Volatile private var request = UUID.randomUUID().toString()
    private fun guard() {
        if (!session.isOpen || app.authBackend.signedInIdentity()?.key != session.identity.key) throw CancellationException("Account changed")
    }
    private fun run(loading: Boolean, block: suspend (FamilyBackend) -> Unit) {
        scope.launch(Dispatchers.IO) { lock.withLock {
            try {
                guard(); mutable.value = mutable.value.copy(loading = loading, running = !loading, problem = null)
                val work = coroutineContext
                block(FamilyBackend(session.identity, { work.ensureActive(); guard() }))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(problem = problem(e)) }
            finally { if (session.isOpen) mutable.value = mutable.value.copy(loading = false, running = false) }
        } }
    }
    private fun problem(e: Exception): AccountDeletionProblem {
        val failure = e as? FamilyTransferException ?: return AccountDeletionProblem.RETRY
        return when {
            failure.reason == FamilyOwnership.REAUTH_REQUIRED -> AccountDeletionProblem.REAUTH
            failure.reason in setOf(FamilyOwnership.OWNER_CHOICE_REQUIRED, FamilyOwnership.OWNER_NOT_EDITOR) -> AccountDeletionProblem.CHOICE
            failure.problem == FamilyProblem.EMAIL -> AccountDeletionProblem.EMAIL
            failure.problem == FamilyProblem.CONFIG -> AccountDeletionProblem.CONFIG
            failure.problem == FamilyProblem.CONFLICT -> AccountDeletionProblem.CHOICE
            failure.problem == FamilyProblem.RETRY -> AccountDeletionProblem.NETWORK
            else -> AccountDeletionProblem.RETRY
        }
    }
    private suspend fun load(backend: FamilyBackend) {
        val entries = FamilyAccessWire.directory(backend.accessCall("directory", emptyMap()))
        val owned = entries.filter { it.role == "owner" }.map { entry ->
            val members = FamilyAccessWire.members(backend.accessCall("members", mapOf("remoteId" to entry.remoteId)))
            FamilyOwnedWardrobe(entry.remoteId, entry.wardrobeId, entry.name, members.members.filter { it.role == "editor" })
        }
        guard(); mutable.value = mutable.value.copy(owned = owned, memberships = entries.count { it.role != "owner" }, loaded = true)
    }
    /** The plan shown before deletion: the server repeats the same decision and refuses if membership changed meanwhile. */
    fun load() = run(loading = true) { backend -> backend.authenticate(); load(backend) }
    fun reset() { if (!mutable.value.running && !mutable.value.done) { mutable.value = AccountDeletionState(); request = UUID.randomUUID().toString() } }

    fun delete(password: String, choices: Map<String, String>) {
        val state = mutable.value
        val owners = FamilyOwnership.owners(state.owned, choices)
        if (!state.loaded || state.running || state.done || password.isEmpty()) return
        if (owners == null) { mutable.value = state.copy(problem = AccountDeletionProblem.CHOICE); return }
        run(loading = false) { backend ->
            val key = session.identity.key
            withContext(NonCancellable) { AccountErasure.mark(app, key, AccountErasure.Stage.REQUESTED) }
            var definite = true // a definite refusal clears the marker; a lost response keeps it
            try {
                when (app.authBackend.reauthenticate(session.identity, password)) {
                    AuthReauth.OK -> Unit
                    // An earlier attempt whose response was lost already deleted the account.
                    AuthReauth.GONE -> { finished(key); return@run }
                    AuthReauth.WRONG_PASSWORD -> throw DeletionRefused(AccountDeletionProblem.PASSWORD)
                    AuthReauth.RATE_LIMITED -> throw DeletionRefused(AccountDeletionProblem.RATE)
                    AuthReauth.NETWORK -> throw DeletionRefused(AccountDeletionProblem.NETWORK)
                    AuthReauth.FAILED -> throw DeletionRefused(AccountDeletionProblem.RETRY)
                }
                // A fresh ID token carries the new sign-in time the server requires.
                backend.authenticate()
                definite = false
                FamilyOwnership.deletionResult(backend.accessCall("delete_account", FamilyOwnership.deletionRequest(request, owners)))
                finished(key)
            } catch (e: DeletionRefused) {
                withContext(NonCancellable) { AccountErasure.clear(app, key) }
                mutable.value = mutable.value.copy(problem = e.problem)
            } catch (e: FamilyTransferException) {
                val problem = problem(e)
                if (definite || problem != AccountDeletionProblem.NETWORK) withContext(NonCancellable) { AccountErasure.clear(app, key) }
                if (problem == AccountDeletionProblem.CHOICE) runCatching { load(backend) }
                throw e
            } catch (e: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                if (e.errorCode != "ERROR_USER_NOT_FOUND") { withContext(NonCancellable) { AccountErasure.clear(app, key) }; throw e }
                finished(key) // authenticate() found the user already deleted by the lost earlier attempt
            }
        }
    }
    private suspend fun finished(key: String) {
        withContext(NonCancellable) { AccountErasure.mark(app, key, AccountErasure.Stage.DELETED) }
        mutable.value = mutable.value.copy(done = true, problem = null)
    }
    private class DeletionRefused(val problem: AccountDeletionProblem) : Exception(problem.name)
}
