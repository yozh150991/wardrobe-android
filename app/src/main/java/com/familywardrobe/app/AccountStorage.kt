package com.familywardrobe.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.AtomicFile
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Only the first explicit claim binds the old global wardrobe. A damaged registry fails closed. */
internal class AccountRegistry(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "account-storage-v1.json"))
    private data class Record(val legacyOwner: String?, val separate: Set<String>)
    private fun read(): Record {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return Record(null, emptySet())
        val json = JSONObject(file.readFully().toString(Charsets.UTF_8))
        require(json.getInt("version") == 1 && json.has("legacyOwner") && json.has("separate"))
        val owner = if (json.isNull("legacyOwner")) null else json.getString("legacyOwner")
        require(owner == null || AuthRules.validAccountKey(owner))
        val array = json.getJSONArray("separate")
        val separate = (0 until array.length()).map { array.getString(it) }.toSet()
        require(separate.all(AuthRules::validAccountKey) && (owner == null || owner !in separate))
        return Record(owner, separate)
    }
    private fun write(record: Record) {
        val json = JSONObject().put("version", 1).put("legacyOwner", record.legacyOwner ?: JSONObject.NULL)
            .put("separate", org.json.JSONArray(record.separate.toList().sorted()))
        val stream = file.startWrite()
        try { stream.write(json.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    @Synchronized fun needsLegacyChoice(key: String): Boolean {
        require(AuthRules.validAccountKey(key))
        val record = read()
        return record.legacyOwner == null && key !in record.separate &&
            (context.getDatabasePath("wardrobe.db").exists() || File(context.filesDir, "photos").listFiles()?.isNotEmpty() == true)
    }
    @Synchronized fun location(key: String, claimLegacy: Boolean = false): AccountLocation {
        require(AuthRules.validAccountKey(key))
        var record = read()
        if (claimLegacy) {
            require(record.legacyOwner == null || record.legacyOwner == key)
            require(key !in record.separate)
            record = record.copy(legacyOwner = key)
            write(record) // Ownership is durable before any private database is opened.
        } else if (record.legacyOwner != key && key !in record.separate) {
            record = record.copy(separate = record.separate + key)
            write(record)
        }
        return AccountLocation(key, record.legacyOwner == key)
    }
}

internal data class AccountLocation(val key: String, val legacy: Boolean) {
    init { require(AuthRules.validAccountKey(key)) }
    val databaseName get() = if (legacy) "wardrobe.db" else "wardrobe-$key.db"
}

internal class AccountContext(base: Context, val location: AccountLocation) : ContextWrapper(base) {
    override fun getFilesDir(): File = if (location.legacy) super.getFilesDir()
        else File(super.getFilesDir(), "accounts/${location.key}").apply { check(mkdirs() || isDirectory) }
    override fun getCacheDir(): File = File(super.getCacheDir(), "accounts/${location.key}")
        .apply { check(mkdirs() || isDirectory) }
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences(if (location.legacy) name else "account-${location.key}-$name", mode)
}

internal class AccountSession(val identity: AuthIdentity, application: WardrobeApplication, location: AccountLocation) : ViewModelStoreOwner {
    val id = UUID.randomUUID().toString() // Never reuse navigation state after signing out.
    val context = AccountContext(application, location)
    val database = Room.databaseBuilder(application, WardrobeDatabase::class.java, location.databaseName)
        .enableMultiInstanceInvalidation()
        .addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build()
    val repository = Repository(database, AccountWriteLocks.forDatabase(location.databaseName))
    val photos = PhotoStore(context)
    val analyzer = PhotoAnalyzer(context, photos)
    override val viewModelStore = ViewModelStore()
    private val app = application
    internal var privateJob: kotlinx.coroutines.Job? = null
    @Volatile private var closed = false
    internal val isOpen get() = !closed
    fun close() {
        if (closed) return
        closed = true
        CloudSchedule.enabled(context, identity.key, false)
        viewModelStore.clear()
        app.accountClosures[identity.key] = app.backgroundScope.launch(Dispatchers.IO) {
            privateJob?.join()
            runCatching { analyzer.close() }
            photos.clearMemoryCache()
            runCatching { database.close() }
            // Only transient per-account data. Final photos, DB and appearance are retained.
            context.cacheDir.deleteRecursively()
            File(app.cacheDir, "camera/${identity.key}").deleteRecursively()
        }
    }
}
