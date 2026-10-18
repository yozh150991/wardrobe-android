package com.familywardrobe.app

import androidx.room.*

@Entity(tableName = "day_plans", foreignKeys = [
    ForeignKey(entity = Wardrobe::class, parentColumns = ["id"], childColumns = ["wardrobeId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Outfit::class, parentColumns = ["id"], childColumns = ["outfitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("wardrobeId"), Index("outfitId"), Index(value = ["wardrobeId", "date", "outfitId"], unique = true)])
data class DayPlan(@PrimaryKey val id: String = newId(), val wardrobeId: String, val outfitId: String,
    val date: String, val notes: String = "", val worn: Boolean = false, val wornItemIds: String = "")

@Entity(tableName = "outfit_feedback", foreignKeys = [ForeignKey(entity = Wardrobe::class,
    parentColumns = ["id"], childColumns = ["wardrobeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("wardrobeId"), Index(value = ["wardrobeId", "signature", "weather", "occasion", "reason"], unique = true)])
data class OutfitFeedback(@PrimaryKey val id: String = newId(), val wardrobeId: String,
    val signature: String, val weather: String, val occasion: String, val reason: String)

@Entity(tableName = "capsules", foreignKeys = [ForeignKey(entity = Wardrobe::class,
    parentColumns = ["id"], childColumns = ["wardrobeId"], onDelete = ForeignKey.CASCADE)], indices = [Index("wardrobeId")])
data class Capsule(@PrimaryKey val id: String = newId(), val wardrobeId: String, val name: String,
    val kind: String = "capsule", val startDate: String = "", val endDate: String = "")

@Entity(tableName = "capsule_outfits", primaryKeys = ["capsuleId", "outfitId"], foreignKeys = [
    ForeignKey(entity = Capsule::class, parentColumns = ["id"], childColumns = ["capsuleId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Outfit::class, parentColumns = ["id"], childColumns = ["outfitId"], onDelete = ForeignKey.CASCADE)], indices = [Index("outfitId")])
data class CapsuleOutfit(val capsuleId: String, val outfitId: String)

@Entity(tableName = "capsule_items", primaryKeys = ["capsuleId", "garmentId"], foreignKeys = [
    ForeignKey(entity = Capsule::class, parentColumns = ["id"], childColumns = ["capsuleId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Garment::class, parentColumns = ["id"], childColumns = ["garmentId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("garmentId")])
data class CapsuleItem(val capsuleId: String, val garmentId: String, val manual: Boolean = false, val packed: Boolean = false)
