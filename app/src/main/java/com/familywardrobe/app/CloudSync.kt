package com.familywardrobe.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.work.*
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex

private object CloudLocks {
    private val locks=ConcurrentHashMap<String,Mutex>()
    fun account(key: String)=locks.getOrPut(key) {Mutex()}
}

/** One queued account snapshot; SQLite revisions retain edits across offline/restarts. */
internal class CloudSyncEngine(private val app: WardrobeApplication,private val key: String,private val context: Context,
    private val repo: Repository,private val photos: PhotoStore,
    private val signedIn: () -> AuthIdentity? = { app.authBackend.signedInIdentity() },
    private val backendFor: (String) -> CloudTransport = { CloudBackend(it) }) {
    private val identity get()=requireNotNull(signedIn()).also { require(it.key==key) }
    private fun guard() { require(signedIn()?.key==key) }
    private suspend fun meta()=requireNotNull(repo.dao.cloudMeta())
    private suspend fun update(transform: (CloudSyncMeta)->CloudSyncMeta)=repo.writes.withLock {repo.dao.saveCloudMeta(transform(meta()))}
    private suspend fun conflict(remote: CloudHead) {update {it.copy(conflictRevision=remote.revision,conflictPath=remote.path,conflictHash=remote.hash,
        conflictWardrobes=remote.wardrobes,conflictGarments=remote.garments,conflictOutfits=remote.outfits,error="CONFLICT")}}
    suspend fun run(): Boolean = withContext(Dispatchers.IO) { CloudLocks.account(key).withLock { runLocked() } }
    private suspend fun runLocked(): Boolean {
        guard();val before=meta();if(!before.enabled)return true
        val backend=backendFor(identity.uid);val remote=backend.read();guard()
        if(remote!=null && CloudSyncRules.acknowledged(before.uploadPath,remote.path,before.uploadHash,remote.hash)) {
            update {it.copy(baseRevision=remote.revision,dirty=!CloudSyncRules.canClearDirty(it.uploadLocalRevision,it.localRevision),uploadPath="",uploadHash="",error="",lastSuccess=System.currentTimeMillis())}
            return false
        }
        if(before.uploadPath.isNotBlank() && (remote?.revision ?: 0L)==before.baseRevision && recoverPending(backend,before))return false
        val s=repo.snapshot();val hasData=s.garments.isNotEmpty()||s.wishes.isNotEmpty()||s.outfits.isNotEmpty()||s.capsules.isNotEmpty()||s.wardrobes.any {it.id!=Repository.DEFAULT_WARDROBE||!it.usesDefaultName}
        val action=CloudSyncRules.decide(before.baseRevision,remote?.revision ?: 0L,before.dirty,hasData)
        when(action) {
            CloudSyncRules.Action.NONE -> update {it.copy(error="",conflictRevision=0,lastSuccess=System.currentTimeMillis())}
            CloudSyncRules.Action.CONFLICT -> conflict(requireNotNull(remote))
            CloudSyncRules.Action.PULL -> {pull(backend,requireNotNull(remote),before.localRevision,false);return false}
            CloudSyncRules.Action.PUSH -> {push(backend,before);return false}
        }
        return !meta().dirty || meta().conflictRevision!=0L
    }
    /** A process may die after uploading but before publishing/receiving the head. Resume that exact snapshot. */
    private suspend fun recoverPending(backend: CloudTransport,before: CloudSyncMeta): Boolean {
        val file=File(context.cacheDir,"cloud-pending-${newId()}.zip")
        try {
            backend.download(CloudHead(before.baseRevision+1,before.uploadPath,before.uploadHash,0,0,0),file);guard()
            val v=Backup(context,repo,photos).verify(Uri.fromFile(file));require(v.fingerprint==before.uploadHash)
            val accepted=backend.commit(before.baseRevision,CloudHead(before.baseRevision+1,before.uploadPath,before.uploadHash,v.wardrobes,v.garments,v.outfits))
            guard()
            update {it.copy(baseRevision=accepted.revision,dirty=!CloudSyncRules.canClearDirty(before.uploadLocalRevision,it.localRevision),uploadPath="",uploadHash="",error="",lastSuccess=System.currentTimeMillis())}
            return true
        } catch(e: StorageException) {
            if(e.errorCode!=StorageException.ERROR_OBJECT_NOT_FOUND)throw e
            update {if(it.uploadPath==before.uploadPath)it.copy(uploadPath="",uploadHash="") else it}
            return false
        } catch(e: Exception) {
            if(e is CancellationException)throw e
            val current=runCatching {backend.read()}.getOrNull()
            if(current!=null && current.revision!=before.baseRevision) {
                if(current.path==before.uploadPath && current.hash==before.uploadHash)return true // next pass acknowledges it
                conflict(current);return true
            }
            throw e
        } finally {file.delete()}
    }
    private suspend fun checkedDownload(backend: CloudTransport,remote: CloudHead): File {
        val file=File(context.cacheDir,"cloud-${newId()}.zip")
        try {
            backend.download(remote,file);guard()
            require(Backup(context,repo,photos).verify(Uri.fromFile(file)).fingerprint==remote.hash)
            if(backend.read()!=remote)throw CloudChangedException()
            return file
        }catch(e: Exception){file.delete();throw e}
    }
    private suspend fun pull(backend: CloudTransport,remote: CloudHead,local: Long,rescue: Boolean) {
        val file=checkedDownload(backend,remote)
        try {
            if(rescue) {
                val staged=File(context.filesDir,"cloud-rescue-${newId()}.zip")
                try {
                    Backup(context,repo,photos).export(Uri.fromFile(staged),::guard)
                    check(staged.renameTo(rescueFile(context)))
                } finally {staged.delete()}
            }
            CloudArchive(context,repo,photos).apply(file,remote,local,::guard)
        } finally {file.delete()}
    }
    private suspend fun push(backend: CloudTransport,before: CloudSyncMeta) {
        val previous=backend.read()
        if ((previous?.revision ?: 0L)!=before.baseRevision) {
            if(previous!=null) {conflict(previous);return}
            throw CloudChangedException()
        }
        val file=File(context.cacheDir,"cloud-upload-${newId()}.zip");var path=""
        try {
            // The archive is a coherent snapshot of `staged`; later local edits keep `dirty` for the next pass.
            val staged=Backup(context,repo,photos).export(Uri.fromFile(file),::guard)
            require(file.length()<=CloudBackend.MAX_ARCHIVE)
            val verification=Backup(context,repo,photos).verify(Uri.fromFile(file))
            repo.writes.withLock {
                val current=meta();if(staged<before.localRevision || staged>current.localRevision || !current.enabled)throw CloudChangedException()
                path="users/${identity.uid}/snapshots/${newId()}.zip"
                repo.dao.saveCloudMeta(current.copy(uploadPath=path,uploadHash=verification.fingerprint,uploadLocalRevision=staged,error=""))
            }
            backend.upload(file,path);guard();if(!meta().enabled)return
            val accepted=try {
                backend.commit(before.baseRevision,CloudHead(before.baseRevision+1,path,verification.fingerprint,verification.wardrobes,verification.garments,verification.outfits))
            }catch(e: Exception) {
                // A lost acknowledgement after a server commit must not become an overwrite or a false conflict.
                val current=runCatching {backend.read()}.getOrNull()
                if(current!=null && current.path==path && current.hash==verification.fingerprint)current
                else if(current!=null && current.revision!=before.baseRevision){
                    conflict(current)
                    runCatching {backend.removeUncommitted(path)}
                    return
                }
                else throw e
            }
            guard()
            update {it.copy(baseRevision=accepted.revision,dirty=!CloudSyncRules.canClearDirty(staged,it.localRevision),uploadPath="",uploadHash="",conflictRevision=0,conflictPath="",conflictHash="",error="",lastSuccess=System.currentTimeMillis())}
            // The new head is committed before deleting the previous snapshot. Active heads are protected by rules.
            previous?.takeIf {it.path!=accepted.path}?.let {runCatching {backend.removeUncommitted(it.path)}}
        } finally {file.delete()}
    }
    suspend fun resolve(choice: String,expectedLocal: Long,expectedRemote: Long) = withContext(Dispatchers.IO) { CloudLocks.account(key).withLock {
        guard();val before=meta()
        if(!before.enabled || before.localRevision!=expectedLocal || before.conflictRevision!=expectedRemote)throw CloudChangedException()
        val backend=backendFor(identity.uid);val remote=requireNotNull(backend.read())
        if(remote.revision!=expectedRemote){conflict(remote);throw CloudChangedException()}
        when(choice) {
            "local" -> {
                update { if(it.localRevision!=expectedLocal)throw CloudChangedException();it.copy(baseRevision=remote.revision,dirty=true,conflictRevision=0,conflictPath="",conflictHash="",error="") }
                push(backend,meta())
            }
            "cloud" -> pull(backend,remote,expectedLocal,true)
            "both" -> {
                val file=checkedDownload(backend,remote)
                try {Backup(context,repo,photos).restore(Uri.fromFile(file),expectedFingerprint=remote.hash,expectedLocalRevision=expectedLocal,cloudBaseRevision=remote.revision,cloudGuard=::guard)}finally{file.delete()}
            }
            else -> error("Unknown conflict choice")
        }
    } }
    companion object { fun rescueFile(context: Context)=File(context.filesDir,"cloud-before-replace-${newId()}.zip") }
}

internal object CloudSchedule {
    private fun name(key: String)="wardrobe-cloud-$key"
    fun enqueue(context: Context,key: String) {
        val request=OneTimeWorkRequestBuilder<CloudSyncWorker>().setInputData(workDataOf("account" to key))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(name(key),ExistingWorkPolicy.KEEP,request)
    }
    fun enabled(context: Context,key: String,on: Boolean) {
        val manager=WorkManager.getInstance(context.applicationContext)
        if(!on){manager.cancelUniqueWork(name(key));manager.cancelUniqueWork(name(key)+"-poll");return}
        val periodic=PeriodicWorkRequestBuilder<CloudSyncWorker>(15,TimeUnit.MINUTES).setInputData(workDataOf("account" to key))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        manager.enqueueUniquePeriodicWork(name(key)+"-poll",ExistingPeriodicWorkPolicy.KEEP,periodic);enqueue(context,key)
    }
    fun running(context: Context,key: String): Flow<Boolean> {
        val manager=WorkManager.getInstance(context.applicationContext)
        return combine(manager.getWorkInfosForUniqueWorkFlow(name(key)),manager.getWorkInfosForUniqueWorkFlow(name(key)+"-poll")) { a,b ->
            (a+b).any {it.state==WorkInfo.State.RUNNING}
        }
    }
}

class CloudSyncWorker(context: Context,parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        val app=applicationContext as WardrobeApplication;val key=inputData.getString("account") ?: return Result.failure()
        if(app.authBackend.signedInIdentity()?.key!=key)return Result.success()
        val location=runCatching {app.accountRegistry.location(key)}.getOrNull() ?: return Result.failure()
        val context=AccountContext(app,location)
        val db=Room.databaseBuilder(app,WardrobeDatabase::class.java,location.databaseName).enableMultiInstanceInvalidation().addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build()
        try {
            AccountWriteLocks.forDatabase(location.databaseName).withLock {ScenarioSchema.installTriggers(db.openHelper.writableDatabase)}
            val repo=Repository(db,AccountWriteLocks.forDatabase(location.databaseName))
            if(repo.dao.cloudMeta()?.enabled!=true)return Result.success()
            val engine=CloudSyncEngine(app,key,context,repo,PhotoStore(context))
            repeat(3){if(engine.run())return Result.success()}
            return Result.retry()
        } catch(e: CancellationException){throw e}
        catch(e: Exception) {
            if(app.authBackend.signedInIdentity()?.key!=key)return Result.success()
            val category=when {
                e is CloudDraftException -> "DRAFT"
                e is CloudChangedException -> "CHANGED"
                e is CloudConfigurationException -> "CONFIG"
                e is FirebaseFirestoreException && e.code in setOf(FirebaseFirestoreException.Code.PERMISSION_DENIED,FirebaseFirestoreException.Code.FAILED_PRECONDITION) -> "CONFIG"
                e is StorageException && e.errorCode in setOf(StorageException.ERROR_NOT_AUTHORIZED,StorageException.ERROR_BUCKET_NOT_FOUND,StorageException.ERROR_PROJECT_NOT_FOUND) -> "CONFIG"
                e is IllegalArgumentException -> "INVALID"
                else -> "RETRY"
            }
            val dao=db.dao();AccountWriteLocks.forDatabase(location.databaseName).withLock {dao.cloudMeta()?.let {dao.saveCloudMeta(it.copy(error=category))}}
            return if(category in setOf("DRAFT","CONFIG","INVALID"))Result.failure() else Result.retry()
        } finally {db.close()}
    }
}

internal class CloudSyncController(private val app: WardrobeApplication,private val session: AccountSession,private val scope: CoroutineScope) {
    /** Each upload is a full account archive: wait for a quiet minute instead of uploading after every tap. */
    private companion object { const val DIRTY_DEBOUNCE_MS=60_000L }
    private val stored=session.repository.dao.cloudMetaFlow().filterNotNull()
    val state=stored.stateIn(scope,SharingStarted.Eagerly,CloudSyncMeta())
    val running=CloudSchedule.running(session.context,session.identity.key).stateIn(scope,SharingStarted.WhileSubscribed(5000),false)
    private var listener: com.google.firebase.firestore.ListenerRegistration?=null
    init {
        scope.launch {
            stored.map {it.enabled}.distinctUntilChanged().collect {enabled ->
                listener?.remove();listener=null;CloudSchedule.enabled(session.context,session.identity.key,enabled)
                if(enabled && app.authBackend.signedInIdentity()?.key==session.identity.key)runCatching {
                    listener=CloudBackend(session.identity.uid).listen({CloudSchedule.enqueue(session.context,session.identity.key)},{})
                }
            }
        }
        scope.launch {stored.map {Triple(it.enabled,it.localRevision,it.dirty)}.distinctUntilChanged().collectLatest { (enabled,_,dirty) ->if(enabled&&dirty){delay(DIRTY_DEBOUNCE_MS);CloudSchedule.enqueue(session.context,session.identity.key)}}}
        scope.coroutineContext[Job]?.invokeOnCompletion {listener?.remove()}
    }
    suspend fun enable(on: Boolean) = session.repository.writes.withLock {
        val dao=session.repository.dao;val s=dao.cloudMeta() ?: CloudSyncMeta();dao.saveCloudMeta(s.copy(enabled=on,error=""))
    }
    fun retry()=CloudSchedule.enqueue(session.context,session.identity.key)
    suspend fun resolve(choice: String,local: Long,remote: Long)=CloudSyncEngine(app,session.identity.key,session.context,session.repository,session.photos).resolve(choice,local,remote)
    fun rescueFiles(): List<File> = session.context.filesDir.listFiles().orEmpty().filter {
        it.isFile && (it.name=="cloud-before-replace.zip" || it.name.matches(Regex("cloud-before-replace-[a-f0-9-]{36}\\.zip")))
    }.sortedByDescending {it.lastModified()}
}
