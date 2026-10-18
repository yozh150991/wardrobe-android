package com.familywardrobe.app

internal data class FamilyArchiveIdentity(val remoteId: String, val wardrobeId: String, val ownerUid: String) {
    init { require(FamilyRules.validId(remoteId) && FamilyRules.validId(wardrobeId) && FamilyRules.validUid(ownerUid)) }
}

internal enum class FamilyMigrationPhase { PREPARED, STAGED, UPLOADED, VERIFIED }
internal data class FamilyMigrationRecord(val wardrobeId: String, val remoteId: String, val ownerUid: String,
    val archiveId: String, val phase: FamilyMigrationPhase = FamilyMigrationPhase.PREPARED,
    val sourceRevision: Long = -1, val fingerprint: String = "", val remoteRevision: Long = 0, val archiveSize: Long = 0) {
    init {
        require(FamilyRules.validId(wardrobeId) && FamilyRules.validId(remoteId) && FamilyRules.validId(archiveId) && FamilyRules.validUid(ownerUid))
        if (phase == FamilyMigrationPhase.PREPARED) require(sourceRevision == -1L && fingerprint.isEmpty() && archiveSize == 0L)
        else require(sourceRevision >= 0 && FamilyRules.validHash(fingerprint))
        require(archiveSize in 0..FamilyWire.MAX_ARCHIVE)
        require(if (phase == FamilyMigrationPhase.VERIFIED) remoteRevision == 1L else remoteRevision == 0L)
    }
    val identity get() = FamilyArchiveIdentity(remoteId, wardrobeId, ownerUid)
    val path get() = FamilyRules.archivePath(remoteId, archiveId)
}

