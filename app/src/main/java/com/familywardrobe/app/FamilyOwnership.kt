package com.familywardrobe.app

/** An owned shared wardrobe and its active editors, the only members who can become its owner. */
internal data class FamilyOwnedWardrobe(val remoteId: String, val wardrobeId: String, val name: String, val editors: List<FamilyMember>) {
    init {
        require(FamilyRules.validId(remoteId) && FamilyRules.validId(wardrobeId))
        require(editors.all { it.role == "editor" } && editors.map { it.uid }.distinct().size == editors.size)
    }
}
internal data class FamilyDeletionResult(val transferred: List<String>, val deleted: List<String>, val left: List<String>)

/**
 * FAM-04 (0.14.25): the wire of family_transfer_owner and family_delete_account. Pure, so the JVM tests cover it.
 * The transfer republishes the head with the new owner in its envelope; the former owner stays as an editor.
 */
internal object FamilyOwnership {
    const val REAUTH_REQUIRED = "REAUTH_REQUIRED"
    const val OWNER_CHOICE_REQUIRED = "OWNER_CHOICE_REQUIRED"
    const val OWNER_NOT_EDITOR = "OWNER_NOT_EDITOR"
    private const val MAX = 100

    /** [FamilyMember.version] makes a role change by someone else in the meantime refuse the transfer. */
    fun transferRequest(remote: String, member: FamilyMember, request: String): Map<String, Any> {
        require(FamilyRules.validId(remote) && FamilyRules.validId(request) && member.role == "editor")
        return mapOf("remoteId" to remote, "uid" to member.uid, "expectedVersion" to member.version, "requestId" to request)
    }
    fun transferResult(value: Map<*, *>, remote: String, uid: String): Long {
        require(value["remoteId"] == remote && value["ownerUid"] == uid)
        return number(value["revision"])
    }

    /**
     * The new owner for every owned wardrobe with editors: its only editor, or the editor the user chose. A wardrobe
     * without editors is absent, so the server deletes it for everyone. Null while a needed choice is missing.
     * The single editor is sent explicitly too: if that editor lost the role meanwhile, the server refuses
     * (OWNER_NOT_EDITOR) instead of choosing someone the user did not see.
     */
    fun owners(owned: List<FamilyOwnedWardrobe>, choices: Map<String, String>): Map<String, String>? {
        val result = linkedMapOf<String, String>()
        for (wardrobe in owned) {
            val uid = when (wardrobe.editors.size) {
                0 -> continue
                1 -> wardrobe.editors.single().uid
                else -> choices[wardrobe.remoteId]?.takeIf { choice -> wardrobe.editors.any { it.uid == choice } } ?: return null
            }
            result[wardrobe.remoteId] = uid
        }
        return result
    }
    fun deletionRequest(request: String, owners: Map<String, String>): Map<String, Any> {
        require(FamilyRules.validId(request) && owners.size <= MAX)
        require(owners.keys.all(FamilyRules::validId) && owners.values.all(FamilyRules::validUid))
        return mapOf("requestId" to request, "owners" to owners)
    }
    fun deletionResult(value: Map<*, *>): FamilyDeletionResult {
        fun ids(key: String) = (value[key] as? List<*> ?: error("Missing $key")).map { it as? String ?: error("Invalid wardrobe ID") }
            .also { require(it.size <= MAX && it.all(FamilyRules::validId) && it.distinct().size == it.size) }
        val result = FamilyDeletionResult(ids("transferred"), ids("deleted"), ids("left"))
        val all = result.transferred + result.deleted + result.left
        require(all.distinct().size == all.size)
        return result
    }
    private fun number(value: Any?): Long {
        val raw = value as? Number ?: error("Missing revision")
        val result = raw.toLong()
        require(raw.toDouble() == result.toDouble() && result in 0..9_007_199_254_740_991L)
        return result
    }
}
