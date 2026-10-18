package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

class FamilyAccessWireTest {
    private val remote = "00000000-0000-4000-8000-000000000001"
    private val wardrobe = "00000000-0000-4000-8000-000000000002"
    private fun entry() = mapOf("remoteId" to remote, "wardrobeId" to wardrobe, "ownerUid" to "owner",
        "name" to "Family", "ownerEmail" to "owner@example.com", "role" to "viewer", "ready" to true)
    private fun invite() = mapOf("inviteId" to wardrobe, "remoteId" to remote, "name" to "Family",
        "ownerEmail" to "owner@example.com", "recipientEmail" to "member@example.com", "role" to "editor",
        "expiresAt" to 1_800_000_000_000L, "status" to "pending")
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Invalid family response accepted") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
    }
    @Test fun directoryReadsTypedHints() {
        val result = FamilyAccessWire.directory(mapOf("wardrobes" to listOf(entry())))
        assertEquals(remote, result.single().remoteId); assertTrue(result.single().ready)
        assertEquals("viewer", result.single().role)
    }
    @Test fun duplicateAndOversizedDirectoriesAreRejected() {
        rejects { FamilyAccessWire.directory(mapOf("wardrobes" to listOf(entry(),entry()))) }
        rejects { FamilyAccessWire.directory(mapOf("wardrobes" to List(101) { entry() })) }
    }
    @Test fun badPathAndRoleAreRejected() {
        for (change in listOf(mapOf("remoteId" to "../other"),mapOf("role" to "admin"),mapOf("ready" to "true")))
            rejects { FamilyAccessWire.directory(mapOf("wardrobes" to listOf(entry() + change))) }
    }
    @Test fun pendingInvitationIsTyped() {
        val result = FamilyAccessWire.invitations(mapOf("invitations" to listOf(invite()))).single()
        assertEquals("editor", result.role); assertEquals(1_800_000_000_000L, result.expiresAt)
    }
    @Test fun TerminalInvitationsAreNotActionable() {
        for (status in listOf("accepted", "revoked", "declined"))
            rejects { FamilyAccessWire.invitations(mapOf("invitations" to listOf(invite() + ("status" to status)))) }
    }
    @Test fun fractionalAndNonFiniteVersionsAreRejected() {
        for (version in listOf<Any>(true, 1.5, Double.NaN, Double.POSITIVE_INFINITY, 0, -1, Long.MAX_VALUE)) {
            val member = mapOf("uid" to "member", "email" to "member@example.com", "role" to "viewer", "version" to version)
            rejects { FamilyAccessWire.members(mapOf("members" to listOf(member), "invitations" to emptyList<Any>())) }
        }
    }
    @Test fun currentMemberVersionPreservedForCompareAndSet() {
        val member = mapOf("uid" to "member", "email" to "member@example.com", "role" to "viewer", "version" to 42L)
        val result = FamilyAccessWire.members(mapOf("members" to listOf(member), "invitations" to emptyList<Any>()))
        assertEquals(42L,result.members.single().version)
    }
}
