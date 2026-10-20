package com.familywardrobe.app

/** General snapshot publication, separate from the base-0 migration journal. */
internal data class FamilyPublication(val identity: FamilyArchiveIdentity, val archiveId: String,
    val baseRevision: Long, val hash: String, val size: Long) {
    init {
        require(FamilyRules.validId(archiveId) && baseRevision in 1..9_007_199_254_740_990L)
        require(FamilyRules.validHash(hash) && size in 1..FamilyWire.MAX_ARCHIVE)
    }
    fun request(): Map<String, Any> = mapOf("remoteId" to identity.remoteId, "archiveId" to archiveId,
        "baseRevision" to baseRevision, "sha256" to hash, "size" to size)
    fun ticket(value: Map<*, *>, actor: String): FamilyTicket {
        val path = "familyWardrobes/${identity.remoteId}/staging/$actor/$archiveId.zip"
        require(value["archiveId"] == archiveId && value["sha256"] == hash && value["authorUid"] == actor && value["stagingPath"] == path)
        require(number(value["baseRevision"]) == baseRevision && number(value["size"]) == size && number(value["expiresAt"]) > 0)
        require(value["status"] in setOf("pending", "committed"))
        return FamilyTicket(path, value["status"] == "committed")
    }
    fun receipt(value: Map<*, *>, actor: String) {
        // A historical receipt is valid after a later writer advances the head.
        // The synthetic role is only for wire parsing; it never grants a read lease.
        val result = FamilyWire.remote(value + ("role" to "editor"))
        val head = requireNotNull(result.head)
        require(result.identity == identity && result.revision == baseRevision + 1 && head.base == baseRevision &&
            head.archiveId == archiveId && head.hash == hash && head.size == size && head.author == actor)
    }
    private fun number(value: Any?): Long {
        val n = value as? Number ?: error("Invalid number"); val v = n.toLong()
        require(n.toDouble().isFinite() && n.toDouble() == v.toDouble() && v in 0..9_007_199_254_740_991L)
        return v
    }
}

internal interface FamilyPublicationTransport {
    suspend fun beginPublication(publication: FamilyPublication): FamilyTicket
    suspend fun commitPublication(publication: FamilyPublication)
    suspend fun upload(file: java.io.File, path: String, progress: (Long, Long) -> Unit)
}

/** Retry probes a receipt before touching local bytes or uploading again. */
internal suspend fun publishFamilyAddition(publication: FamilyPublication, backend: FamilyPublicationTransport,
    checkedFile: () -> java.io.File, checkpoint: () -> Unit) {
    val ticket = backend.beginPublication(publication); checkpoint()
    if (ticket.committed) backend.commitPublication(publication)
    else {
        try { backend.commitPublication(publication) }
        catch (e: FamilyTransferException) {
            if (e.problem != FamilyProblem.MISSING) throw e
            checkpoint(); backend.upload(checkedFile(), ticket.path) { _, _ -> checkpoint() }
            checkpoint(); backend.commitPublication(publication)
        }
    }
    checkpoint()
}
