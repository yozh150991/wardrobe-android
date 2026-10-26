package com.familywardrobe.app

import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal object FamilySyncLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val epochs = ConcurrentHashMap<String, AtomicLong>()
    fun account(key: String) = locks.computeIfAbsent(key) { Mutex() }
    fun epoch(key: String) = epochs.computeIfAbsent(key) { AtomicLong() }.get()
    fun signOut(key: String) { epochs.computeIfAbsent(key) { AtomicLong() }.incrementAndGet() }
}
internal object FamilySyncJournal {
    fun encode(p: FamilySyncPlan, account: String): String = JSONObject().put("version", 1).put("account", account)
        .put("id", p.id).put("remote", p.remote).put("background", p.background).put("cursor", p.cursor)
        .put("acknowledged", p.acknowledged).put("status", p.status.name).put("problem", p.problem)
        .put("operations", JSONArray().apply { p.operations.forEach { o -> put(JSONObject().put("kind", o.kind.name)
            .put("id", o.id).put("fingerprint", o.fingerprint).put("submitted", o.submitted)) } }).toString()
    fun decode(text: String, account: String, remote: String): FamilySyncPlan {
        val j = JSONObject(text)
        require(j.getInt("version") == 1 && j.getString("account") == account && j.getString("remote") == remote)
        val rows = j.getJSONArray("operations"); require(rows.length() in 1..130)
        return FamilySyncPlan(j.getString("id"), remote, (0 until rows.length()).map { index -> rows.getJSONObject(index).let {
            FamilySyncOperation(FamilySyncKind.valueOf(it.getString("kind")), it.getString("id"), it.getString("fingerprint"), it.getBoolean("submitted"))
        } }, j.getBoolean("background"), j.getInt("cursor"), j.getBoolean("acknowledged"),
            FamilySyncStatus.valueOf(j.getString("status")), j.getString("problem"))
    }
}
internal class FamilySyncStore(context: AccountContext, private val account: AuthIdentity) {
    init { require(context.location.key == account.key) }
    private val root = File(context.noBackupFilesDir, "family-sync/${account.key}")
    private val lock = locks.computeIfAbsent(root.canonicalPath) { Any() }
    val changes = signals.computeIfAbsent(root.canonicalPath) { MutableStateFlow(0L) }
    companion object {
        private val locks = ConcurrentHashMap<String, Any>()
        private val signals = ConcurrentHashMap<String, MutableStateFlow<Long>>()
    }
    private fun file(remote: String): AtomicFile {
        require(FamilyRules.validId(remote)); return AtomicFile(File(root, "$remote.json"))
    }
    fun get(remote: String): FamilySyncPlan? = synchronized(lock) {
        val f = file(remote)
        if (!f.baseFile.exists() && !File(f.baseFile.path + ".bak").exists()) null else f.openRead().use { input ->
            val bytes = java.io.ByteArrayOutputStream().also { PhotoStore.copyLimited(input, it, 128L * 1024) }.toByteArray()
            FamilySyncJournal.decode(bytes.toString(Charsets.UTF_8), account.key, remote)
        }
    }
    fun put(next: FamilySyncPlan, expected: FamilySyncPlan?) = synchronized(lock) {
        require(get(next.remote) == expected) { "Sync plan changed" }
        val bytes = FamilySyncJournal.encode(next, account.key).toByteArray(Charsets.UTF_8); require(bytes.size <= 128 * 1024)
        check(root.mkdirs() || root.isDirectory)
        val f = file(next.remote)
        if (!f.baseFile.exists() && root.listFiles().orEmpty().count { it.extension == "json" } >= 100) throw FamilyTransferException(FamilyProblem.LIMIT)
        val out = f.startWrite()
        try { out.write(bytes); out.fd.sync(); f.finishWrite(out) } catch (e: Exception) { f.failWrite(out); throw e }
        changes.value += 1
    }
    fun stop(remote: String) = synchronized(lock) {
        get(remote)?.takeIf { it.active }?.let { put(it.copy(status = FamilySyncStatus.CANCELLED, background = false, problem = "STOPPED"), it) }
    }
    fun failure(remote: String, id: String, problem: String) = synchronized(lock) {
        get(remote)?.takeIf { it.id == id && it.active }?.let {
            put(it.copy(status = if (problem == "RETRY") FamilySyncStatus.RETRY else FamilySyncStatus.PAUSED, problem = problem), it)
        }
    }
    fun pauseAll() = synchronized(lock) {
        root.listFiles().orEmpty().map { it.name.removeSuffix(".bak").removeSuffix(".json") }
            .filter(FamilyRules::validId).distinct().forEach { remote ->
                get(remote)?.takeIf { it.active }?.let { put(it.copy(background = false, status = FamilySyncStatus.PAUSED, problem = "SIGNED_OUT"), it) }
            }
    }
}
