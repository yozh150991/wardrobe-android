package com.familywardrobe.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/** Accesses only existing account-private outboxes. Never opens private Room or builds a new ZIP. */
internal class FamilySyncOperations(context: AccountContext, identity: AuthIdentity, private val remote: String) {
    private val edits = FamilyEditStore(context, identity)
    private val adds = FamilyAddStore(context, identity)
    private val photos = FamilyAddStore(context, identity, "family-photo-replacements")
    private val outfits = FamilyOutfitStore(context, identity)
    fun pending(kind: FamilySyncKind): List<FamilySyncOperation> = when (kind) {
        FamilySyncKind.EDIT -> edits.list(remote).map(FamilySyncPlanner::edit)
        FamilySyncKind.ADD, FamilySyncKind.PHOTO -> mediaStore(kind).list(remote).map { FamilySyncPlanner.media(kind, it) }
        FamilySyncKind.OUTFIT -> outfits.list(remote).map(FamilySyncPlanner::outfit)
    }
    fun snapshot() = FamilySyncPlanner.ordered(FamilySyncKind.entries.associateWith(::pending))
    fun label(o: FamilySyncOperation): String = when (o.kind) {
        FamilySyncKind.EDIT -> edits.list(remote).find { it.editId == o.id }?.values?.get("name").orEmpty()
        FamilySyncKind.OUTFIT -> outfits.list(remote).find { it.publication.archiveId == o.id }?.change?.name.orEmpty()
        else -> mediaStore(o.kind).list(remote).find { it.publication.archiveId == o.id }?.name.orEmpty()
    }
    private fun mediaStore(kind: FamilySyncKind) = if (kind == FamilySyncKind.ADD) adds else photos
    private fun checked(o: FamilySyncOperation) {
        if (!o.matches(pending(o.kind).firstOrNull())) throw FamilySyncChanged()
    }
    suspend fun authorize(o: FamilySyncOperation, backend: FamilyBackend) {
        checked(o); backend.authenticate(); val head = backend.readHead(remote)
        if (head.role !in setOf("owner", "editor")) throw FamilyTransferException(FamilyProblem.ACCESS)
        val current = pending(o.kind).first(); checked(o)
        val publication = when (o.kind) {
            FamilySyncKind.EDIT -> null
            FamilySyncKind.OUTFIT -> requireNotNull(outfits.draft(remote)).publication
            else -> requireNotNull(mediaStore(o.kind).get(remote)).publication
        }
        if (publication != null && publication.identity != head.identity) throw FamilyTransferException(FamilyProblem.ACCESS)
        val base = publication?.baseRevision ?: requireNotNull(edits.get(remote)).baseRevision
        // A submitted request must probe its historical receipt even after the head advances.
        if (!current.submitted && base != head.revision &&
            !(o.kind == FamilySyncKind.EDIT && edits.get(remote)?.changes?.isEmpty() == true)) throw FamilyTransferException(FamilyProblem.CONFLICT)
    }
    fun submitted(o: FamilySyncOperation) {
        checked(o)
        when (o.kind) {
            FamilySyncKind.EDIT -> requireNotNull(edits.get(remote)).let { if (it.changes.isNotEmpty() || it.submitted) edits.put(it.copy(submitted = true), it) }
            FamilySyncKind.OUTFIT -> requireNotNull(outfits.draft(remote)).let { outfits.put(it.copy(submitted = true), it) }
            else -> mediaStore(o.kind).let { store -> requireNotNull(store.get(remote)).let { store.put(it.copy(submitted = true), it) } }
        }
    }
    suspend fun publish(o: FamilySyncOperation, backend: FamilyBackend, checkpoint: () -> Unit) {
        checked(o)
        when (o.kind) {
            FamilySyncKind.EDIT -> {
                val d = requireNotNull(edits.get(remote))
                if (!d.submitted && d.changes.isEmpty()) return
                require(d.submitted)
                val result = backend.accessCall("edit_item", mapOf("remoteId" to remote, "editId" to d.editId,
                    "garmentId" to d.garmentId, "baseRevision" to d.baseRevision, "changes" to d.changes))
                require(result["status"] == "committed" && result["editId"] == d.editId && result["remoteId"] == remote)
                val revision = result["revision"] as? Number ?: error("Invalid edit receipt")
                require(revision.toDouble() == (d.baseRevision + 1).toDouble())
            }
            FamilySyncKind.OUTFIT -> {
                val d = requireNotNull(outfits.draft(remote)); require(d.submitted)
                publishFamilyAddition(d.publication, backend, { outfits.checked(d, checkpoint) }, checkpoint)
            }
            else -> {
                val store = mediaStore(o.kind); val d = requireNotNull(store.get(remote)); require(d.submitted)
                publishFamilyAddition(d.publication, backend, { store.checked(d, checkpoint) }, checkpoint)
            }
        }
        checkpoint()
    }
    fun remove(o: FamilySyncOperation) {
        checked(o)
        when (o.kind) {
            FamilySyncKind.EDIT -> edits.remove(requireNotNull(edits.get(remote)))
            FamilySyncKind.OUTFIT -> outfits.remove(requireNotNull(outfits.draft(remote)))
            else -> mediaStore(o.kind).let { it.remove(requireNotNull(it.get(remote))) }
        }
    }
}
internal class FamilySyncEngine(private val app: WardrobeApplication, private val context: AccountContext,
    private val identity: AuthIdentity, private val remote: String, private val planId: String,
    private val background: Boolean, private val sessionOpen: () -> Boolean = { true }) {
    suspend fun run(): Boolean = FamilySyncLocks.account(identity.key).withLock {
        val store = FamilySyncStore(context, identity)
        val initial = store.get(remote) ?: return@withLock true
        if (initial.id != planId || !initial.active || (background && !initial.background)) return@withLock true
        val work = coroutineContext; val epoch = FamilySyncLocks.epoch(identity.key)
        fun guard() {
            work.ensureActive()
            if (!sessionOpen() || app.authBackend.signedInIdentity()?.key != identity.key || FamilySyncLocks.epoch(identity.key) != epoch)
                throw CancellationException("Account changed")
            val p = store.get(remote)
            if (p?.id != planId || !p.active || (background && !p.background)) throw CancellationException("Plan stopped")
        }
        try {
            guard()
            store.put(initial.copy(status = FamilySyncStatus.RUNNING, problem = ""), initial)
            val backend = FamilyBackend(identity, ::guard)
            val operations = FamilySyncOperations(context, identity, remote)
            FamilySyncRunner(object : FamilySyncPort {
                override fun plan() = requireNotNull(store.get(remote))
                override fun save(next: FamilySyncPlan, expected: FamilySyncPlan) { guard(); store.put(next, expected) }
                override fun pending(kind: FamilySyncKind) = operations.pending(kind)
                override suspend fun authorize(operation: FamilySyncOperation) = operations.authorize(operation, backend)
                override fun submitted(operation: FamilySyncOperation) { guard(); operations.submitted(operation) }
                override suspend fun publish(operation: FamilySyncOperation) = operations.publish(operation, backend, ::guard)
                override fun remove(operation: FamilySyncOperation) { guard(); operations.remove(operation) }
                override fun checkpoint() = guard()
            }).run(if (background) 10 else 130)
        } catch (e: CancellationException) {
            if (!background) store.failure(remote, planId, "STOPPED")
            throw e
        } catch (e: Exception) {
            val problem = when (e) {
                is FamilySyncChanged -> "CHANGED"
                is FamilyTransferException -> e.problem.name
                is IllegalArgumentException -> "ARCHIVE"
                else -> "RETRY"
            }
            store.failure(remote, planId, problem)
            false
        }
    }
}
