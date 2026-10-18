package com.familywardrobe.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/** Real SDK + Room tests. Fake credentials and named apps use only demo-wardrobe emulators. */
class CloudEmulatorTest {
    private val base: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = base.applicationContext as WardrobeApplication
    private fun emulatorHost(): String {
        val host=InstrumentationRegistry.getArguments().getString("firebaseEmulatorHost")
        assumeTrue("Start local Firebase emulators and pass firebaseEmulatorHost",host!=null)
        require(host in listOf("10.0.2.2","127.0.0.1","localhost"))
        return requireNotNull(host)
    }
    private inner class Device(val firebase: FirebaseApp, val identity: AuthIdentity) {
        private val name="cloud-test-${newId()}.db"
        val context=AccountContext(base,AccountLocation(AuthRules.accountKey("isolated-cloud-test",newId()),false))
        var db=open(); private set
        var repo=Repository(db); private set
        val photos=PhotoStore(context)
        val firestore=FirebaseFirestore.getInstance(firebase).apply {
            firestoreSettings=FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            useEmulator(emulatorHost(),8080)
        }
        val backend=CloudBackend(identity.uid,firebase,firestore)
        private fun open()=Room.databaseBuilder(base,WardrobeDatabase::class.java,name).addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build()
        suspend fun start() {repo.initialize("Home");repo.dao.saveCloudMeta(repo.dao.cloudMeta()!!.copy(enabled=true))}
        suspend fun reopen() {db.close();db=open();repo=Repository(db);repo.initialize("Home")}
        fun engine(transport: CloudTransport=backend)=CloudSyncEngine(app,identity.key,context,repo,photos,{identity},{transport})
        suspend fun item(name: String): Garment {
            val photo=newId();val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            try {photos.file(photo,"png").outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}} finally {bitmap.recycle()}
            return Garment(wardrobeId=Repository.DEFAULT_WARDROBE,name=name,category="dress",color="blue",photoId=photo).also {repo.save(it)}
        }
        suspend fun settled() {repeat(6) {if(engine().run())return};fail("Queue did not settle")}
        suspend fun close() {
            db.close();base.deleteDatabase(name);photos.clearMemoryCache();context.filesDir.deleteRecursively();context.cacheDir.deleteRecursively()
            firestore.terminate().await();firebase.delete()
        }
    }
    private suspend fun clients(block: suspend (Device,Device)->Unit) {
        val host=emulatorHost();val email="cloud-${newId()}@example.test";val password="Wardrobe-test-123"
        fun firebase(): FirebaseApp {
            val options=FirebaseOptions.Builder().setProjectId("demo-wardrobe").setApiKey("fake-api-key-for-emulator")
                .setApplicationId("1:123:android:emulator").setStorageBucket("demo-wardrobe.appspot.com").build()
            return FirebaseApp.initializeApp(base,options,"cloud-${newId()}").also {
                FirebaseAuth.getInstance(it).useEmulator(host,9099);FirebaseStorage.getInstance(it).useEmulator(host,9199)
            }
        }
        val fa=firebase();val fb=firebase()
        val first=FirebaseAuth.getInstance(fa).createUserWithEmailAndPassword(email,password).await().user!!
        val second=FirebaseAuth.getInstance(fb).signInWithEmailAndPassword(email,password).await().user!!
        assertEquals(first.uid,second.uid)
        val identity=AuthIdentity("demo-wardrobe",first.uid,email)
        val a=Device(fa,identity);val b=Device(fb,identity)
        try {a.start();b.start();block(a,b)} finally {a.close();b.close()}
    }
    private suspend fun fails(block: suspend ()->Unit) {try {block();fail("Expected transport failure")}catch(_:IOException){}}

    @Test fun offlineQueueSurvivesDatabaseReopenAndTransfersPhotosToAnotherClient()=runBlocking {clients {a,b ->
        val garment=a.item("Offline dress");val before=a.repo.dao.cloudMeta()!!
        val offline=object:CloudTransport by a.backend {override suspend fun read():CloudHead?=throw IOException("offline")}
        fails {a.engine(offline).run()};a.reopen()
        assertEquals(before.localRevision,a.repo.dao.cloudMeta()!!.localRevision);assertTrue(a.repo.dao.cloudMeta()!!.dirty)
        a.settled();b.settled()
        assertEquals(a.repo.snapshot(),b.repo.snapshot());assertEquals(garment,b.repo.dao.garment(garment.id))
        assertTrue(b.photos.currentFile(garment.photoId).readBytes().contentEquals(a.photos.currentFile(garment.photoId).readBytes()))
    }}
    @Test fun lostCommitReplyIsAcknowledgedOnceAfterReopen()=runBlocking {clients {a,b ->
        val garment=a.item("Lost reply");var failedReads=0;var failReply=true
        val lost=object:CloudTransport by a.backend {
            override suspend fun read():CloudHead? {if(failedReads>0){failedReads--;throw IOException("lost read")};return a.backend.read()}
            override suspend fun commit(base:Long,value:CloudHead):CloudHead {
                val head=a.backend.commit(base,value)
                if(failReply){failReply=false;failedReads=1;throw IOException("lost successful reply")};return head
            }
        }
        fails {a.engine(lost).run()};assertTrue(a.repo.dao.cloudMeta()!!.uploadPath.isNotBlank())
        assertEquals(1L,a.backend.read()!!.revision);a.reopen();a.settled();b.settled()
        assertEquals(1L,a.backend.read()!!.revision);assertFalse(a.repo.dao.cloudMeta()!!.dirty)
        assertEquals("",a.repo.dao.cloudMeta()!!.uploadPath);assertEquals(garment,b.repo.dao.garment(garment.id))
    }}
    @Test fun editsDuringUploadRemainQueuedAndReachSecondRevision()=runBlocking {clients {a,b ->
        val garment=a.item("Before upload");var edit=true
        val during=object:CloudTransport by a.backend {override suspend fun upload(file:File,path:String) {
            a.backend.upload(file,path);if(edit){edit=false;a.repo.save(garment.copy(name="After upload began"))}
        }}
        a.engine(during).run();assertEquals(1L,a.backend.read()!!.revision);assertTrue(a.repo.dao.cloudMeta()!!.dirty)
        a.settled();b.settled();assertEquals(2L,a.backend.read()!!.revision)
        assertEquals("After upload began",b.repo.dao.garment(garment.id)!!.name);assertFalse(a.repo.dao.cloudMeta()!!.dirty)
    }}
    @Test fun allConflictChoicesPreserveChosenDataAndCloudChoiceMakesRescue()=runBlocking {
        for(choice in listOf("cloud","local","both")) clients {a,b ->
            val local=b.item("Local version");val cloud=a.item("Cloud version");a.settled();b.engine().run()
            val conflict=b.repo.dao.cloudMeta()!!;assertEquals(1L,conflict.conflictRevision)
            b.engine().resolve(choice,conflict.localRevision,conflict.conflictRevision)
            when(choice) {
                "cloud" -> {
                    assertNull(b.repo.dao.garment(local.id));assertEquals(cloud,b.repo.dao.garment(cloud.id))
                    val rescues=b.context.filesDir.listFiles()!!.filter {it.name.startsWith("cloud-before-replace-")&&it.extension=="zip"}
                    assertEquals(1,rescues.size);assertEquals(1,Backup(b.context,b.repo,b.photos).verify(Uri.fromFile(rescues.single())).garments)
                }
                "local" -> {assertEquals(local,b.repo.dao.garment(local.id));assertNull(b.repo.dao.garment(cloud.id));assertEquals(2L,b.backend.read()!!.revision)}
                "both" -> {
                    assertEquals(local,b.repo.dao.garment(local.id));assertNull(b.repo.dao.garment(cloud.id))
                    val imported=b.repo.dao.allGarments().single {it.name==cloud.name}
                    assertNotEquals(local.wardrobeId,imported.wardrobeId);assertNotEquals(cloud.id,imported.id)
                    assertTrue(b.photos.currentFile(imported.photoId).isFile);assertTrue(b.repo.dao.cloudMeta()!!.dirty)
                    b.settled();a.settled();assertEquals(2,a.repo.dao.allGarments().size)
                }
            }
            BackupStateRules.validate(b.repo.snapshot())
        }
    }
    @Test fun twoIndependentSdkTransactionsCannotPublishFromSameBaseAndRetryIsIdempotent()=runBlocking {clients {a,b ->
        a.item("Transaction base");a.settled();val head=a.backend.read()!!
        suspend fun candidate(device:Device):CloudHead {
            val file=File(device.context.cacheDir,"candidate-${newId()}.zip");Backup(device.context,a.repo,a.photos).export(Uri.fromFile(file))
            val v=Backup(device.context,a.repo,a.photos).verify(Uri.fromFile(file));val path="users/${a.identity.uid}/snapshots/${newId()}.zip"
            try {device.backend.upload(file,path)}finally{file.delete()}
            return CloudHead(2,path,v.fingerprint,v.wardrobes,v.garments,v.outfits)
        }
        val ca=candidate(a);val cb=candidate(b)
        val results=coroutineScope {listOf(async {runCatching {a.backend.commit(head.revision,ca)}},async {runCatching {b.backend.commit(head.revision,cb)}}).awaitAll()}
        assertEquals(1,results.count {it.isSuccess});assertEquals(1,results.count {it.isFailure})
        val winner=results.single {it.isSuccess}.getOrThrow();assertEquals(2L,winner.revision)
        assertEquals(winner,a.backend.read());assertEquals(winner,b.backend.commit(1,winner));assertEquals(2L,b.backend.read()!!.revision)
    }}
}
