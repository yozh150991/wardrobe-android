package com.familywardrobe.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal enum class FamilyAccessNotice { INVITED, ACCEPTED, DECLINED, UPDATED, REVOKED, LEFT, TRANSFERRED }
internal data class FamilyAccessState(val running: Boolean = false, val loaded: Boolean = false,
    val wardrobes: List<FamilyDirectoryEntry> = emptyList(), val invitations: List<FamilyInvitation> = emptyList(),
    val managedRemote: String = "", val members: List<FamilyMember> = emptyList(),
    val pending: List<FamilyInvitation> = emptyList(), val problem: FamilyProblem? = null,
    val notice: FamilyAccessNotice? = null, val completedRequest: String = "")

internal class FamilyAccessController(private val app: WardrobeApplication, private val session: AccountSession,
    private val reader: FamilyReaderController, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(FamilyAccessState())
    val state = mutable.asStateFlow()
    private val lock = Mutex()
    private fun guard() {
        if (!session.isOpen || app.authBackend.signedInIdentity()?.key != session.identity.key)
            throw CancellationException("Account changed")
    }
    private fun run(block: suspend (FamilyBackend) -> Unit) {
        // Queue behind a running request: an accept/revoke tapped during refresh must not vanish silently.
        scope.launch(Dispatchers.IO) { lock.withLock {
            try {
                guard(); mutable.value = mutable.value.copy(running = true, problem = null, notice = null)
                val work = coroutineContext
                val backend = FamilyBackend(session.identity, { work.ensureActive(); guard() })
                backend.authenticate(); block(backend); work.ensureActive(); guard()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                guard()
                val problem = (e as? FamilyTransferException)?.problem ?: FamilyProblem.RETRY
                if (problem == FamilyProblem.ACCESS) {
                    reader.invalidate(); mutable.value = FamilyAccessState(running = true)
                }
                mutable.value = mutable.value.copy(problem = problem)
            } finally {
                if (session.isOpen) mutable.value = mutable.value.copy(running = false)
            }
        } }
    }
    private suspend fun reload(backend: FamilyBackend) {
        // Refresh invalidates any prior cached access, including a role changed elsewhere.
        reader.invalidate()
        val wardrobes = FamilyAccessWire.directory(backend.accessCall("directory", emptyMap()))
        val invitations = FamilyAccessWire.invitations(backend.accessCall("invitations", emptyMap()))
        guard(); mutable.value = mutable.value.copy(wardrobes = wardrobes, invitations = invitations, loaded = true)
        val remote = mutable.value.managedRemote
        if (wardrobes.any { it.remoteId == remote && it.role == "owner" }) loadMembers(backend, remote)
        else mutable.value = mutable.value.copy(managedRemote = "", members = emptyList(), pending = emptyList())
    }
    private suspend fun loadMembers(backend: FamilyBackend, remote: String) {
        val result = FamilyAccessWire.members(backend.accessCall("members", mapOf("remoteId" to remote)))
        guard(); mutable.value = mutable.value.copy(managedRemote = remote, members = result.members, pending = result.invitations)
    }
    fun refresh() = run { reload(it) }
    fun manage(remote: String) = run { backend ->
        mutable.value = mutable.value.copy(managedRemote = "", members = emptyList(), pending = emptyList())
        loadMembers(backend, remote)
    }
    fun invite(remote: String, id: String, email: String, role: String, name: String) = run { backend ->
        val result = backend.accessCall("invite", mapOf("remoteId" to remote, "inviteId" to id,
            "email" to email.trim(), "role" to role, "name" to name.trim()))
        if (result["status"] != "pending") throw FamilyTransferException(FamilyProblem.CONFLICT)
        mutable.value = mutable.value.copy(notice = FamilyAccessNotice.INVITED, completedRequest = id)
        reload(backend)
    }
    fun respond(invite: FamilyInvitation, accept: Boolean) = run { backend ->
        backend.accessCall("respond_invite", mapOf("inviteId" to invite.inviteId, "action" to if (accept) "accept" else "decline"))
        mutable.value = mutable.value.copy(notice = if (accept) FamilyAccessNotice.ACCEPTED else FamilyAccessNotice.DECLINED)
        reload(backend)
    }
    fun cancel(invite: FamilyInvitation) = run { backend ->
        backend.accessCall("cancel_invite", mapOf("remoteId" to invite.remoteId, "inviteId" to invite.inviteId))
        mutable.value = mutable.value.copy(notice = FamilyAccessNotice.REVOKED); reload(backend)
    }
    fun change(remote: String, member: FamilyMember, role: String, request: String) = run { backend ->
        backend.accessCall("change_member", mapOf("remoteId" to remote, "uid" to member.uid, "role" to role,
            "expectedVersion" to member.version, "requestId" to request))
        mutable.value = mutable.value.copy(notice = if (role == "revoked") FamilyAccessNotice.REVOKED else FamilyAccessNotice.UPDATED)
        reload(backend)
    }
    /**
     * FAM-04: [member] (an active editor) becomes the owner; this account stays as an editor. A lost response is
     * harmless: the server replays the same [request], and any other request finds that this account no longer owns it.
     */
    fun transfer(remote: String, member: FamilyMember, request: String) = run { backend ->
        // The owner's read lease and export permission end with the role.
        reader.invalidate()
        FamilyOwnership.transferResult(backend.accessCall("transfer_owner", FamilyOwnership.transferRequest(remote, member, request)), remote, member.uid)
        mutable.value = mutable.value.copy(notice = FamilyAccessNotice.TRANSFERRED); reload(backend)
    }
    fun leave(remote: String, request: String = UUID.randomUUID().toString()) = run { backend ->
        // Hide cached photos before a potentially ambiguous network response.
        reader.invalidate()
        backend.accessCall("leave", mapOf("remoteId" to remote, "requestId" to request))
        mutable.value = mutable.value.copy(notice = FamilyAccessNotice.LEFT); reload(backend)
    }
    fun invalidateSession() { mutable.value = FamilyAccessState() }
}
