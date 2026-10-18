package com.familywardrobe.app

import androidx.room.*

/** Account-private queue; copied images survive process death. Source URIs are never exported. */
@Entity(tableName = "import_drafts", foreignKeys = [ForeignKey(entity = Wardrobe::class,
    parentColumns = ["id"], childColumns = ["wardrobeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("wardrobeId")])
data class ImportDraft(@PrimaryKey val id: String = newId(), val wardrobeId: String,
    val batchId: String, val position: Int, val inputUri: String = "", val photoId: String = "",
    val originalPhotoId: String = "", val name: String = "", val category: String = "",
    val color: String = "other", val season: String = "all", val style: String = "casual",
    val status: String = "queued", val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val photoFingerprint: String = "")

data class BulkChange(val before: List<Garment>, val after: List<Garment>)
