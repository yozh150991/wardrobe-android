package com.familywardrobe.app

import com.google.firebase.FirebaseApp
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

internal data class FamilyTransferState(val records: List<FamilyMigrationRecord> = emptyList(), val loading: Boolean = true,
    val running: Boolean = false, val step: FamilyStep = FamilyStep.CHECKING, val bytes: Long = 0, val total: Long = 0,
    val problem: FamilyProblem? = null, val emailSent: Boolean = false)
internal class FamilyTransferController(private val app: WardrobeApplication, private val session: AccountSession, scope: CoroutineScope) {
    private val store = FamilyMigrationStore(session.context,session.identity)
    private val mutable = MutableStateFlow(FamilyTransferState())
    val state = mutable.asStateFlow()
    private val lock = locks.computeIfAbsent(session.identity.key) { Mutex() }
    private fun guard() {
        if (!session.isOpen || app.authBackend.signedInIdentity()?.key != session.identity.key) throw CancellationException("Account changed")
    }
    init { scope.launch(Dispatchers.IO) { lock.withLock {
        try { guard(); mutable.value = mutable.value.copy(records = store.records(),loading = false) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutable.value = mutable.value.copy(loading = false,problem = FamilyProblem.ARCHIVE) }
    } } }
    private fun backend(): FamilyBackend {
        guard()
        return try { FamilyBackend(session.identity,::guard,FirebaseApp.getInstance()) }
        catch (e: CancellationException) { throw e }
        catch (e: IllegalStateException) { throw FamilyTransferException(FamilyProblem.CONFIG,e) }
    }
    suspend fun transfer(id: String, restart: Boolean = false) = operation {
        val engine = FamilyTransferEngine(FamilyArchive(session.context,session.repository,session.photos,store,::guard),backend(),::guard) { step,bytes,total ->
            if (session.isOpen) mutable.value = mutable.value.copy(step = step,bytes = bytes,total = total)
        }
        if (restart) engine.restart(id) else engine.run(id)
    }
    suspend fun sendVerification() = operation { backend().sendVerification(); mutable.value = mutable.value.copy(emailSent = true) }
    private suspend fun operation(block: suspend () -> Unit) = withContext(Dispatchers.IO) { lock.withLock {
        guard(); mutable.value = mutable.value.copy(running = true,problem = null,emailSent = false,step = FamilyStep.CHECKING,bytes = 0,total = 0)
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            guard()
            val problem = when (e) {
                is FamilyTransferException -> e.problem
                is IllegalArgumentException -> FamilyProblem.ARCHIVE
                is StorageException -> when(e.errorCode) {
                    StorageException.ERROR_NOT_AUTHORIZED,StorageException.ERROR_NOT_AUTHENTICATED -> FamilyProblem.ACCESS
                    StorageException.ERROR_BUCKET_NOT_FOUND,StorageException.ERROR_PROJECT_NOT_FOUND -> FamilyProblem.CONFIG
                    StorageException.ERROR_QUOTA_EXCEEDED -> FamilyProblem.LIMIT
                    else -> FamilyProblem.RETRY
                }
                else -> FamilyProblem.RETRY
            }
            mutable.value = mutable.value.copy(problem = problem)
        } finally {
            if (session.isOpen) try { mutable.value = mutable.value.copy(records = store.records(),running = false,loading = false) }
            catch (_: Exception) { mutable.value = mutable.value.copy(running = false,loading = false,problem = FamilyProblem.ARCHIVE) }
        }
    } }
    companion object { private val locks = ConcurrentHashMap<String,Mutex>() }
}
