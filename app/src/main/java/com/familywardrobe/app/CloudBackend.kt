package com.familywardrobe.app

import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.*
import com.google.firebase.storage.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.File

class CloudConflictException : IllegalStateException("Cloud version changed")
class CloudDraftException : IllegalStateException("Finish the local draft first")
class CloudChangedException : IllegalStateException("Local data changed")
class CloudConfigurationException(cause: Exception) : IllegalStateException("Cloud storage is not configured", cause)

data class CloudHead(val revision: Long, val path: String, val hash: String, val wardrobes: Int, val garments: Int, val outfits: Int)

internal interface CloudTransport {
    suspend fun read(): CloudHead?
    suspend fun upload(file: File, path: String)
    suspend fun download(remote: CloudHead, file: File)
    suspend fun commit(base: Long, value: CloudHead): CloudHead
    suspend fun removeUncommitted(path: String)
}

internal class CloudBackend(private val uid: String, private val app: FirebaseApp = FirebaseApp.getInstance(),
    private val firestore: FirebaseFirestore = FirestoreClient.instance) : CloudTransport {
    private val storage=try { FirebaseStorage.getInstance(app).also(StorageLimits::configure) } catch(e: Exception) { throw CloudConfigurationException(e) }
    private val head=firestore.document("users/$uid/state/head")
    private fun parse(s: DocumentSnapshot): CloudHead? {
        if(!s.exists())return null
        val revision=requireNotNull(s.getLong("revision"));val path=requireNotNull(s.getString("archive"));val hash=requireNotNull(s.getString("fingerprint"))
        require(revision>0 && CloudSyncRules.validPath(uid,path) && hash.matches(Regex("[a-f0-9]{64}")) && (s.getLong("format") ?: 0L) in 8L..10L)
        fun count(key: String)=requireNotNull(s.getLong(key)).also { require(it in 0L..100_000L) }.toInt()
        return CloudHead(revision,path,hash,count("wardrobes"),count("garments"),count("outfits"))
    }
    override suspend fun read(): CloudHead?=parse(head.get(Source.SERVER).await())
    fun listen(changed: () -> Unit, failed: () -> Unit): ListenerRegistration=head.addSnapshotListener { s,e ->
        if(e!=null) failed() else if(s!=null && !s.metadata.isFromCache) changed()
    }
    override suspend fun upload(file: File,path: String) {
        require(CloudSyncRules.validPath(uid,path) && file.length() in 1..MAX_ARCHIVE)
        val task=storage.reference.child(path).putFile(Uri.fromFile(file),StorageMetadata.Builder().setContentType("application/zip").build())
        try { task.await() } catch(e: CancellationException){task.cancel();throw e}
    }
    override suspend fun download(remote: CloudHead,file: File) {
        val ref=storage.reference.child(remote.path)
        val metadata=ref.metadata.await();require(metadata.sizeBytes in 1..MAX_ARCHIVE)
        val task=ref.getFile(file)
        try {task.await()}catch(e: CancellationException){task.cancel();throw e}
        require(file.length()==metadata.sizeBytes)
    }
    override suspend fun commit(base: Long,value: CloudHead): CloudHead {
        return firestore.runTransaction<CloudHead> { transaction ->
            val existing=parse(transaction.get(head))
            if(existing!=null && existing.path==value.path && existing.hash==value.hash)return@runTransaction existing
            if((existing?.revision ?: 0L)!=base)throw CloudConflictException()
            transaction.set(head,mapOf("revision" to base+1,"archive" to value.path,"fingerprint" to value.hash,"format" to 10,
                "wardrobes" to value.wardrobes,"garments" to value.garments,"outfits" to value.outfits,"updatedAt" to FieldValue.serverTimestamp()))
            value.copy(revision=base+1)
        }.await()
    }
    override suspend fun removeUncommitted(path: String) { if(read()?.path!=path) storage.reference.child(path).delete().await() }
    companion object { const val MAX_ARCHIVE=512L*1024*1024 }
}

/** Set before the first Firestore operation, once per process. Room is the durable offline store. */
private object FirestoreClient {
    val instance: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(FirebaseApp.getInstance()).apply {
            firestoreSettings=FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        }
    }
}
