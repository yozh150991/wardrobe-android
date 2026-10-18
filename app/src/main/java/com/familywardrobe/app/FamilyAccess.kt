package com.familywardrobe.app

/** These are server directory hints, not a permission or an offline lease. */
internal data class FamilyDirectoryEntry(val remoteId: String, val wardrobeId: String, val ownerUid: String,
    val name: String, val ownerEmail: String, val role: String, val ready: Boolean)
internal data class FamilyInvitation(val inviteId: String, val remoteId: String, val name: String,
    val ownerEmail: String, val recipientEmail: String, val role: String, val expiresAt: Long)
internal data class FamilyMember(val uid: String, val email: String, val role: String, val version: Long)
internal data class FamilyMembers(val members: List<FamilyMember>, val invitations: List<FamilyInvitation>)

internal object FamilyAccessWire {
    private fun text(row: Map<*, *>, key: String, max: Int, blank: Boolean = false): String {
        val value = row[key] as? String ?: error("Missing family field")
        require(value.length <= max && (blank || value.isNotBlank()) && value.none { it.code < 32 })
        return value
    }
    private fun id(row: Map<*, *>, key: String) = text(row, key, 36).also { require(FamilyRules.validId(it)) }
    private fun role(row: Map<*, *>, owner: Boolean = false) = text(row, "role", 10).also {
        require(it == "viewer" || it == "editor" || owner && it == "owner")
    }
    private fun integer(row: Map<*, *>, key: String): Long {
        val raw = row[key] as? Number ?: error("Missing family number")
        val value = raw.toLong()
        require(value in 1..9_007_199_254_740_991L && raw.toDouble() == value.toDouble())
        return value
    }
    private fun rows(value: Map<*, *>, key: String, max: Int = 50): List<Map<*, *>> {
        val rows = value[key] as? List<*> ?: error("Missing family list")
        require(rows.size <= max)
        return rows.map { it as? Map<*, *> ?: error("Invalid family row") }
    }
    fun directory(value: Map<*, *>): List<FamilyDirectoryEntry> = rows(value, "wardrobes", 100).map { row ->
        FamilyDirectoryEntry(id(row,"remoteId"), id(row,"wardrobeId"), text(row,"ownerUid",128),
            text(row,"name",80,true), text(row,"ownerEmail",254,true), role(row,true), row["ready"] as? Boolean ?: error("Missing ready"))
    }.also { require(it.map { r -> r.remoteId }.distinct().size == it.size) }
    fun invitations(value: Map<*, *>): List<FamilyInvitation> = rows(value,"invitations").map { row ->
        require(row["status"] == "pending")
        FamilyInvitation(id(row,"inviteId"), id(row,"remoteId"), text(row,"name",80), text(row,"ownerEmail",254),
            text(row,"recipientEmail",254), role(row), integer(row,"expiresAt"))
    }.also { require(it.map { r -> r.inviteId }.distinct().size == it.size) }
    fun members(value: Map<*, *>): FamilyMembers = FamilyMembers(rows(value,"members").map { row ->
        FamilyMember(text(row,"uid",128), text(row,"email",254), role(row), integer(row,"version"))
    }.also { require(it.map { r -> r.uid }.distinct().size == it.size) }, invitations(value))
}
