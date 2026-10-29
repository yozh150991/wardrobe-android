package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

/** FAM-04 (0.14.25): ownership transfer and account deletion wire, and owner-agnostic draft identity. */
class FamilyOwnershipTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-%012d".format(n)
    private fun editor(n: Int, version: Long = 1) = FamilyMember("uid-$n", "e$n@example.com", "editor", version)
    private fun owned(n: Int, vararg editors: FamilyMember) = FamilyOwnedWardrobe(id(n), id(100 + n), "W$n", editors.toList())
    private fun rejects(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }

    @Test fun transferRequestCarriesTheMembershipVersion() {
        val request = FamilyOwnership.transferRequest(id(1), editor(2, version = 7), id(3))
        assertEquals(mapOf("remoteId" to id(1), "uid" to "uid-2", "expectedVersion" to 7L, "requestId" to id(3)), request)
        assertTrue(request["expectedVersion"] is Long)
    }
    @Test fun onlyAnEditorCanBecomeTheOwner() {
        rejects { FamilyOwnership.transferRequest(id(1), editor(2).copy(role = "viewer"), id(3)) }
        rejects { FamilyOwnership.transferRequest("not-an-id", editor(2), id(3)) }
        rejects { FamilyOwnership.transferRequest(id(1), editor(2), "") }
    }
    @Test fun transferResultMustNameTheChosenOwnerAndWardrobe() {
        val ok = mapOf("remoteId" to id(1), "ownerUid" to "uid-2", "revision" to 8)
        assertEquals(8L, FamilyOwnership.transferResult(ok, id(1), "uid-2"))
        // An empty shared wardrobe keeps revision 0.
        assertEquals(0L, FamilyOwnership.transferResult(ok + ("revision" to 0), id(1), "uid-2"))
        rejects { FamilyOwnership.transferResult(ok, id(1), "uid-3") }
        rejects { FamilyOwnership.transferResult(ok, id(2), "uid-2") }
        rejects { FamilyOwnership.transferResult(ok + ("revision" to 8.5), id(1), "uid-2") }
        rejects { FamilyOwnership.transferResult(ok - "revision", id(1), "uid-2") }
    }
    @Test fun singleEditorIsChosenAutomaticallyAndNoEditorMeansDeletion() {
        val owners = FamilyOwnership.owners(listOf(owned(1, editor(1)), owned(2)), emptyMap())
        assertEquals(mapOf(id(1) to "uid-1"), owners)
    }
    @Test fun severalEditorsNeedTheUsersChoice() {
        val wardrobes = listOf(owned(1, editor(1), editor(2)), owned(2, editor(3)))
        assertNull(FamilyOwnership.owners(wardrobes, emptyMap()))
        // A choice that is not an editor of that wardrobe does not count.
        assertNull(FamilyOwnership.owners(wardrobes, mapOf(id(1) to "uid-3")))
        assertEquals(mapOf(id(1) to "uid-2", id(2) to "uid-3"), FamilyOwnership.owners(wardrobes, mapOf(id(1) to "uid-2")))
        // A stale choice for a wardrobe without editors is not sent: the server would refuse an unknown wardrobe.
        assertEquals(emptyMap<String, String>(), FamilyOwnership.owners(listOf(owned(3)), mapOf(id(3) to "uid-1")))
    }
    @Test fun ownedWardrobeHoldsOnlyDistinctEditors() {
        rejects { owned(1, editor(1).copy(role = "viewer")) }
        rejects { owned(1, editor(1), editor(1)) }
    }
    @Test fun deletionRequestAndResult() {
        assertEquals(mapOf("requestId" to id(9), "owners" to mapOf(id(1) to "uid-1")), FamilyOwnership.deletionRequest(id(9), mapOf(id(1) to "uid-1")))
        assertEquals(mapOf("requestId" to id(9), "owners" to emptyMap<String, String>()), FamilyOwnership.deletionRequest(id(9), emptyMap()))
        rejects { FamilyOwnership.deletionRequest(id(9), mapOf("x" to "uid-1")) }
        rejects { FamilyOwnership.deletionRequest("x", emptyMap()) }
        val result = FamilyOwnership.deletionResult(mapOf("transferred" to listOf(id(1)), "deleted" to listOf(id(2)), "left" to emptyList<String>()))
        assertEquals(FamilyDeletionResult(listOf(id(1)), listOf(id(2)), emptyList()), result)
        rejects { FamilyOwnership.deletionResult(mapOf("transferred" to listOf(id(1)), "deleted" to listOf(id(1)), "left" to emptyList<String>())) }
        rejects { FamilyOwnership.deletionResult(mapOf("transferred" to listOf("x"), "deleted" to emptyList<String>(), "left" to emptyList<String>())) }
        rejects { FamilyOwnership.deletionResult(mapOf("transferred" to emptyList<String>(), "deleted" to emptyList<String>())) }
    }
    @Test fun anOwnershipTransferKeepsTheSameWardrobe() {
        val identity = FamilyArchiveIdentity(id(1), id(2), "owner")
        assertTrue(identity.sameWardrobe(identity.copy(ownerUid = "new-owner")))
        assertFalse(identity.sameWardrobe(identity.copy(remoteId = id(3))))
        assertFalse(identity.sameWardrobe(identity.copy(wardrobeId = id(3))))
    }
}
