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
    val kind: String = "capsule", val startDate: String = "", val endDate: String = "",
    /** TRIP-01 (0.14.44, Room 11, backup 11): where the trip goes — its forecast — and what it is for. */
    @ColumnInfo(defaultValue = "") val city: String = "", val latitude: Double? = null, val longitude: Double? = null,
    /** One of LookRules.OCCASIONS, or "" for «щодня». */
    @ColumnInfo(defaultValue = "") val occasion: String = "") {
    /** Trip details beyond the format-10 fields: only these make a backup format 11. */
    val hasTripDetails: Boolean get() = city.isNotEmpty() || latitude != null || longitude != null || occasion.isNotEmpty()
    /** TRIP-01: a city of up to [MAX_CITY] characters, both coordinates or none (within range), a known occasion or none. */
    val validTripDetails: Boolean get() = city.length <= MAX_CITY && (latitude == null) == (longitude == null) &&
        (latitude == null || latitude in -90.0..90.0) && (longitude == null || longitude in -180.0..180.0) &&
        city.isEmpty() == (latitude == null) && (occasion.isEmpty() || occasion in LookRules.OCCASIONS)
    val tripCity: WeatherCity? get() = if (city.isNotEmpty() && latitude != null && longitude != null) WeatherCity(city, latitude, longitude) else null
    companion object { const val MAX_CITY = 250 }
}

@Entity(tableName = "capsule_outfits", primaryKeys = ["capsuleId", "outfitId"], foreignKeys = [
    ForeignKey(entity = Capsule::class, parentColumns = ["id"], childColumns = ["capsuleId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Outfit::class, parentColumns = ["id"], childColumns = ["outfitId"], onDelete = ForeignKey.CASCADE)], indices = [Index("outfitId")])
data class CapsuleOutfit(val capsuleId: String, val outfitId: String)

@Entity(tableName = "capsule_items", primaryKeys = ["capsuleId", "garmentId"], foreignKeys = [
    ForeignKey(entity = Capsule::class, parentColumns = ["id"], childColumns = ["capsuleId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Garment::class, parentColumns = ["id"], childColumns = ["garmentId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("garmentId")])
data class CapsuleItem(val capsuleId: String, val garmentId: String, val manual: Boolean = false, val packed: Boolean = false)
