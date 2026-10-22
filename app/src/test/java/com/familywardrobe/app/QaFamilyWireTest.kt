package com.familywardrobe.app

import com.familywardrobe.app.QaCheck.Technique.BVA
import com.familywardrobe.app.QaCheck.Technique.EG
import com.familywardrobe.app.QaCheck.Technique.EP
import com.familywardrobe.app.QaCheck.Technique.ST
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Family wire parsing and read/publish coordinators on the phone: boundary values of every server field,
 * the client/server limit mismatch QA-01, progress fractions and the read-lease state machine.
 */
class QaFamilyWireTest {
    private val remote = "00000000-0000-4000-8000-000000000001"
    private val wardrobe = "00000000-0000-4000-8000-000000000002"
    private val archive = "00000000-0000-4000-8000-000000000003"
    private val emoji = "😀"

    private fun invite(vararg changes: Pair<String, Any?>) = mapOf<String, Any?>("inviteId" to wardrobe, "remoteId" to remote,
        "name" to "Family", "ownerEmail" to "owner@example.com", "recipientEmail" to "member@example.com", "role" to "editor",
        "expiresAt" to 1_800_000_000_000L, "status" to "pending") + changes
    private fun entry(vararg changes: Pair<String, Any?>) = mapOf<String, Any?>("remoteId" to remote, "wardrobeId" to wardrobe,
        "ownerUid" to "owner", "name" to "Family", "ownerEmail" to "owner@example.com", "role" to "viewer", "ready" to true) + changes
    private fun member(vararg changes: Pair<String, Any?>) = mapOf<String, Any?>("uid" to "member", "email" to "member@example.com",
        "role" to "viewer", "version" to 1L) + changes
    private fun accepted(block: () -> Unit) = runCatching(block).isSuccess

    @Test fun wireFieldBoundaries() {
        val qa = QaCheck("QaFamilyWireTest.wire")
        // BVA: names 80/81, e-mails 254/255, UIDs 128/129, versions 1/0 and 2^53-1 / 2^53.
        qa.isTrue(BVA, "invitation name 80", accepted { FamilyAccessWire.invitations(mapOf("invitations" to listOf(invite("name" to "a".repeat(80))))) })
        qa.isFalse(BVA, "invitation name 81", accepted { FamilyAccessWire.invitations(mapOf("invitations" to listOf(invite("name" to "a".repeat(81))))) })
        qa.isTrue(BVA, "directory name blank", accepted { FamilyAccessWire.directory(mapOf("wardrobes" to listOf(entry("name" to "")))) })
        qa.isTrue(BVA, "e-mail 254", accepted { FamilyAccessWire.invitations(mapOf("invitations" to listOf(invite("recipientEmail" to "a".repeat(242) + "@example.com")))) })
        qa.isFalse(BVA, "e-mail 255", accepted { FamilyAccessWire.invitations(mapOf("invitations" to listOf(invite("recipientEmail" to "a".repeat(243) + "@example.com")))) })
        qa.isTrue(BVA, "uid 128", accepted { FamilyAccessWire.members(mapOf("members" to listOf(member("uid" to "u".repeat(128))), "invitations" to emptyList<Any>())) })
        qa.isFalse(BVA, "uid 129", accepted { FamilyAccessWire.members(mapOf("members" to listOf(member("uid" to "u".repeat(129))), "invitations" to emptyList<Any>())) })
        qa.isTrue(BVA, "version 1", accepted { FamilyAccessWire.members(mapOf("members" to listOf(member("version" to 1)), "invitations" to emptyList<Any>())) })
        qa.isFalse(BVA, "version 0", accepted { FamilyAccessWire.members(mapOf("members" to listOf(member("version" to 0)), "invitations" to emptyList<Any>())) })
        qa.isTrue(BVA, "version 2^53-1", accepted { FamilyAccessWire.members(mapOf("members" to listOf(member("version" to 9_007_199_254_740_991L)), "invitations" to emptyList<Any>())) })
        qa.isFalse(BVA, "version 2^53", accepted { FamilyAccessWire.members(mapOf("members" to listOf(member("version" to 9_007_199_254_740_992L)), "invitations" to emptyList<Any>())) })
        qa.isTrue(BVA, "50 invitations", accepted { FamilyAccessWire.invitations(mapOf("invitations" to List(50) { i -> invite("inviteId" to "00000000-0000-4000-8000-%012d".format(i)) })) })
        qa.isFalse(BVA, "51 invitations", accepted { FamilyAccessWire.invitations(mapOf("invitations" to List(51) { i -> invite("inviteId" to "00000000-0000-4000-8000-%012d".format(i)) })) })
        // EP / EG: control characters, blank names, missing fields.
        qa.isFalse(EG, "newline in name", accepted { FamilyAccessWire.invitations(mapOf("invitations" to listOf(invite("name" to "Fam\nily")))) })
        qa.isFalse(EP, "blank invitation name", accepted { FamilyAccessWire.invitations(mapOf("invitations" to listOf(invite("name" to "  ")))) })
        qa.isFalse(EP, "missing list", accepted { FamilyAccessWire.invitations(emptyMap<String, Any>()) })
        qa.isFalse(EG, "owner role in a membership", accepted { FamilyAccessWire.members(mapOf("members" to listOf(member("role" to "owner")), "invitations" to emptyList<Any>())) })
        // QA-01: the server (membership.invite) limits the shared-wardrobe name to 80 *code points*; the phone parses
        // at most 80 UTF-16 units. An owner (any modified client) can send an invitation named with 80 emoji, which the
        // server stores and returns; the recipient's whole invitation list then fails to parse, and after acceptance
        // their and the owner's family directory (displayName) fails too.
        qa.knownBug(BVA, "QA-01", "emoji wardrobe name breaks invitation/directory parsing") {
            val name = emoji.repeat(80)
            check(name.codePointCount(0, name.length) == 80) { "fixture: 80 code points" }
            FamilyAccessWire.invitations(mapOf("invitations" to listOf(invite("name" to name), invite("inviteId" to remote))))
            FamilyAccessWire.directory(mapOf("wardrobes" to listOf(entry("name" to name))))
        }
        qa.done()
        assertTrue(qa.reproducedBugs().contains("QA-01"))
    }

    @Test fun uploadProgressBoundaries() {
        val qa = QaCheck("QaFamilyWireTest.progress")
        qa.equal(EP, "not uploading", null, familyUploadProgress(false, 1, 10))
        qa.equal(BVA, "unknown total 0", null, familyUploadProgress(true, 0, 0))
        qa.equal(BVA, "negative total", null, familyUploadProgress(true, 0, -1))
        qa.equal(BVA, "start", 0f, familyUploadProgress(true, 0, 10)!!())
        qa.equal(BVA, "end", 1f, familyUploadProgress(true, 10, 10)!!())
        qa.equal(BVA, "overshoot clamps", 1f, familyUploadProgress(true, 11, 10)!!())
        qa.equal(BVA, "negative bytes clamp", 0f, familyUploadProgress(true, -5, 10)!!())
        val huge = familyUploadProgress(true, Long.MAX_VALUE - 1, Long.MAX_VALUE)!!()
        qa.isTrue(EG, "Long extremes stay finite", huge.isFinite() && huge in 0f..1f)
        qa.done()
    }

    @Test fun publicationRequestBoundaries() {
        val qa = QaCheck("QaFamilyWireTest.publication")
        val identity = FamilyArchiveIdentity(remote, wardrobe, "owner")
        val hash = "ab".repeat(32)
        qa.isTrue(BVA, "base revision 1", accepted { FamilyPublication(identity, archive, 1, hash, 1) })
        qa.isFalse(BVA, "base revision 0 is the migration journal", accepted { FamilyPublication(identity, archive, 0, hash, 1) })
        qa.isTrue(BVA, "base revision 2^53-2", accepted { FamilyPublication(identity, archive, 9_007_199_254_740_990L, hash, 1) })
        qa.isFalse(BVA, "base revision 2^53-1", accepted { FamilyPublication(identity, archive, 9_007_199_254_740_991L, hash, 1) })
        qa.isTrue(BVA, "size 128 MiB", accepted { FamilyPublication(identity, archive, 1, hash, FamilyWire.MAX_ARCHIVE) })
        qa.isFalse(BVA, "size 128 MiB + 1", accepted { FamilyPublication(identity, archive, 1, hash, FamilyWire.MAX_ARCHIVE + 1) })
        qa.isFalse(BVA, "size 0", accepted { FamilyPublication(identity, archive, 1, hash, 0) })
        qa.isFalse(EP, "upper-case hash", accepted { FamilyPublication(identity, archive, 1, hash.uppercase(), 1) })
        val publication = FamilyPublication(identity, archive, 4, hash, 10)
        val ticket = mapOf("archiveId" to archive, "sha256" to hash, "authorUid" to "editor", "baseRevision" to 4L, "size" to 10L,
            "expiresAt" to 1L, "status" to "pending", "stagingPath" to "familyWardrobes/$remote/staging/editor/$archive.zip")
        qa.isFalse(ST, "pending ticket", publication.ticket(ticket, "editor").committed)
        qa.isTrue(ST, "committed ticket", publication.ticket(ticket + ("status" to "committed"), "editor").committed)
        qa.isFalse(ST, "expired-unknown status", accepted { publication.ticket(ticket + ("status" to "expired"), "editor") })
        qa.isFalse(EG, "ticket for another author", accepted { publication.ticket(ticket, "intruder") })
        qa.isFalse(EG, "ticket path of another author", accepted { publication.ticket(ticket + ("stagingPath" to "familyWardrobes/$remote/staging/x/$archive.zip"), "editor") })
        qa.isFalse(EG, "fractional size", accepted { publication.ticket(ticket + ("size" to 10.5), "editor") })
        qa.done()
    }

    /** Read lease: the second head read must finish within 15 minutes of its start. */
    @Test fun readLeaseExpiresAtFifteenMinutes() = runBlocking {
        val qa = QaCheck("QaFamilyWireTest.readEngine")
        for ((delay, expected) in listOf(15 * 60_000L - 1 to "published", 15 * 60_000L to "EXPIRED")) {
            var now = 0L; var reads = 0; var published = 0; var discarded = 0
            val head = FamilyRemote(FamilyArchiveIdentity(remote, wardrobe, "owner"), 1,
                FamilyHead(1, 0, archive, FamilyRules.archivePath(remote, archive), "ab".repeat(32), 10, "owner"), "viewer")
            val backend = object : FamilyReadTransport {
                override suspend fun authenticate() {}
                override suspend fun readHead(remoteId: String): FamilyRemote { reads++; if (reads == 2) now += delay; return head }
                override suspend fun download(remote: FamilyRemote, target: File, progress: (Long, Long) -> Unit) = Unit
            }
            val outcome = try {
                FamilyReadEngine("member", backend, {}, { now }, { "staged" }, { _, _, _ -> published++ }, { discarded++ }).run(remote)
                "published"
            } catch (e: FamilyTransferException) { e.problem.name }
            qa.equal(BVA, "second read after $delay ms", expected, outcome)
            qa.equal(ST, "candidate kept or discarded after $delay ms", if (expected == "published") 0 else 1, discarded)
        }
        qa.done()
        assertEquals(4, qa.total())
    }
}
