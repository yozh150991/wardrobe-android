package com.familywardrobe.app

import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

internal interface FamilyReadTransport {
    suspend fun authenticate()
    suspend fun readHead(remoteId: String): FamilyRemote
    suspend fun download(remote: FamilyRemote, target: File, progress: (Long, Long) -> Unit)
}

/** Publish only after a second authenticated head read; a role is never read from the ZIP. */
internal class FamilyReadEngine<T : Any>(
    private val actorUid: String,
    private val backend: FamilyReadTransport,
    private val guard: () -> Unit,
    private val elapsed: () -> Long,
    private val stage: suspend (FamilyRemote) -> T,
    private val publish: (T, FamilyRemote, Long) -> Unit,
    private val discard: (T) -> Unit
) {
    private suspend fun checkpoint() { coroutineContext.ensureActive(); guard() }
    private fun checked(remoteId: String, remote: FamilyRemote): FamilyRemote {
        require(remote.identity.remoteId == remoteId)
        val role = FamilyRules.role(actorUid, remote.identity.ownerUid, remote.role, true)
        if (role == null || role.name.lowercase() != remote.role) throw FamilyTransferException(FamilyProblem.ACCESS)
        val head = remote.head ?: throw FamilyTransferException(FamilyProblem.MISSING)
        require(head.revision == remote.revision && head.revision > 0 && head.base == head.revision - 1 &&
            head.path == FamilyRules.archivePath(remoteId, head.archiveId) && FamilyRules.validHash(head.hash) &&
            head.size in 1..FamilyWire.MAX_ARCHIVE && FamilyRules.validUid(head.author))
        return remote
    }
    suspend fun run(remoteId: String) {
        require(FamilyRules.validId(remoteId)); checkpoint()
        backend.authenticate(); checkpoint()
        val before = checked(remoteId, backend.readHead(remoteId)); checkpoint()
        var candidate: T? = null
        var published = false
        try {
            val staged = stage(before); candidate = staged; checkpoint()
            // Start the lease before the request: network latency must not extend access.
            val verifiedAt = elapsed()
            val after = checked(remoteId, backend.readHead(remoteId)); checkpoint()
            if (before.identity != after.identity || before.head != after.head || before.revision != after.revision)
                throw FamilyTransferException(FamilyProblem.CONFLICT)
            val role = FamilyRules.role(actorUid, after.identity.ownerUid, after.role, true)
            if (!FamilyRules.cachedRead(role, "read", "read", verifiedAt, elapsed()))
                throw FamilyTransferException(FamilyProblem.EXPIRED)
            publish(staged, after, verifiedAt); published = true
        } finally {
            if (!published) candidate?.let(discard)
        }
    }
}
