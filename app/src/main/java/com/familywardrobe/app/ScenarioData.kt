package com.familywardrobe.app

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

@Entity(tableName = "purchase_undo", foreignKeys = [ForeignKey(entity = Wish::class, parentColumns = ["id"], childColumns = ["wishId"], onDelete = ForeignKey.CASCADE)])
data class PurchaseUndoRow(@PrimaryKey val wishId: String, val garmentJson: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "wish_events", foreignKeys = [ForeignKey(entity = Wish::class, parentColumns = ["id"], childColumns = ["wishId"], onDelete = ForeignKey.CASCADE)], indices = [Index("wishId")])
data class WishEvent(@PrimaryKey val id: String = newId(), val wishId: String, val kind: String,
    val name: String, val category: String, val priceMinor: Long? = null, val currency: String = "PLN",
    val link: String = "", val inspiration: String = "", val photoId: String = "", val createdAt: Long = System.currentTimeMillis())

fun Wish.event(kind: String) = WishEvent(wishId = id, kind = kind, name = name, category = category,
    priceMinor = priceMinor, currency = currency, link = link, inspiration = inspiration, photoId = photoId)

internal suspend fun Repository.recordWishEvent(event: WishEvent) {
    dao.saveWishEvent(event.copy(createdAt = maxOf(event.createdAt, (dao.latestWishEventTime(event.wishId) ?: 0L) + 1L)))
}

suspend fun Repository.returnToNeed(id: String): Boolean = writes.withLock {
    db.withTransaction {
        val old = dao.wish(id) ?: return@withTransaction false
        if (old.kind != "product" || old.needName.isBlank() || old.ownedId.isNotBlank()) return@withTransaction false
        val next = old.copy(kind = "need", name = old.needName, category = old.needCategory,
            priceMinor = null, color = "", photoId = old.needPhotoId, link = old.needLink, inspiration = old.needInspiration)
        validateWish(next)
        recordWishEvent(old.event("candidate_cancelled")); dao.saveWish(next)
        true
    }
}

@Entity(tableName = "cloud_sync_meta")
data class CloudSyncMeta(
    @PrimaryKey @ColumnInfo(defaultValue = "1") val id: Int = 1,
    @ColumnInfo(defaultValue = "0") val localRevision: Long = 0,
    @ColumnInfo(defaultValue = "0") val baseRevision: Long = 0,
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
    @ColumnInfo(defaultValue = "0") val enabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val applying: Boolean = false,
    @ColumnInfo(defaultValue = "0") val conflictRevision: Long = 0,
    @ColumnInfo(defaultValue = "''") val conflictPath: String = "",
    @ColumnInfo(defaultValue = "''") val conflictHash: String = "",
    @ColumnInfo(defaultValue = "0") val conflictWardrobes: Int = 0,
    @ColumnInfo(defaultValue = "0") val conflictGarments: Int = 0,
    @ColumnInfo(defaultValue = "0") val conflictOutfits: Int = 0,
    @ColumnInfo(defaultValue = "''") val error: String = "",
    @ColumnInfo(defaultValue = "0") val lastSuccess: Long = 0,
    @ColumnInfo(defaultValue = "''") val uploadPath: String = "",
    @ColumnInfo(defaultValue = "''") val uploadHash: String = "",
    @ColumnInfo(defaultValue = "0") val uploadLocalRevision: Long = 0
)

/** Dirty queue state is changed by SQLite in the same transaction as the user's edit. */
object ScenarioSchema {
    val portableTables = listOf("wardrobes", "garments", "outfits", "outfit_parts", "wears", "wishes", "outfit_ideas", "day_plans", "outfit_feedback", "capsules", "capsule_items", "capsule_outfits", "purchase_undo", "wish_events")
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wishes ADD COLUMN needPhotoId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE wishes ADD COLUMN needLink TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE wishes ADD COLUMN needInspiration TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS wish_events (id TEXT NOT NULL, wishId TEXT NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, priceMinor INTEGER, currency TEXT NOT NULL, link TEXT NOT NULL, inspiration TEXT NOT NULL, photoId TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(wishId) REFERENCES wishes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_wish_events_wishId ON wish_events(wishId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS purchase_undo (wishId TEXT NOT NULL, garmentJson TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(wishId), FOREIGN KEY(wishId) REFERENCES wishes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS cloud_sync_meta (id INTEGER NOT NULL DEFAULT 1, localRevision INTEGER NOT NULL DEFAULT 0, baseRevision INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 0, applying INTEGER NOT NULL DEFAULT 0, conflictRevision INTEGER NOT NULL DEFAULT 0, conflictPath TEXT NOT NULL DEFAULT '', conflictHash TEXT NOT NULL DEFAULT '', conflictWardrobes INTEGER NOT NULL DEFAULT 0, conflictGarments INTEGER NOT NULL DEFAULT 0, conflictOutfits INTEGER NOT NULL DEFAULT 0, error TEXT NOT NULL DEFAULT '', lastSuccess INTEGER NOT NULL DEFAULT 0, uploadPath TEXT NOT NULL DEFAULT '', uploadHash TEXT NOT NULL DEFAULT '', uploadLocalRevision INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(id))")
        installTriggers(db)
        db.execSQL("UPDATE cloud_sync_meta SET dirty=1 WHERE id=1")
    }
    fun installTriggers(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO cloud_sync_meta (id) VALUES (1)")
        portableTables.forEach { table -> listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
            db.execSQL("CREATE TRIGGER IF NOT EXISTS sync_${table}_${operation.lowercase()} AFTER $operation ON $table WHEN (SELECT applying FROM cloud_sync_meta WHERE id=1)=0 BEGIN UPDATE cloud_sync_meta SET localRevision=localRevision+1, dirty=1 WHERE id=1; END")
        } }
    }
}

object GarmentReceipt {
    fun write(g: Garment): String = JSONObject().apply {
        put("id",g.id); put("wardrobeId",g.wardrobeId); put("name",g.name); put("category",g.category); put("color",g.color)
        put("size",g.size); put("location",g.location); put("description",g.description); put("photoId",g.photoId)
        put("season",g.season); put("style",g.style); put("availability",g.availability); put("favorite",g.favorite)
        put("rarelyWorn",g.rarelyWorn); put("archived",g.archived); put("createdAt",g.createdAt)
        put("priceMinor",g.priceMinor ?: JSONObject.NULL); put("currency",g.currency); put("subcategory",g.subcategory)
        put("suggestionExcluded",g.suggestionExcluded); put("photoFingerprint",g.photoFingerprint)
        put("customCategoryId",g.customCategoryId ?: JSONObject.NULL)
        put("customColorId",g.customColorId ?: JSONObject.NULL)
    }.toString()
    fun read(text: String): Garment {
        require(text.length <= 20_000)
        val j=JSONObject(text)
        val g=Garment(j.getString("id"),j.getString("wardrobeId"),j.getString("name"),j.getString("category"),j.getString("color"),
            j.getString("size"),j.getString("location"),j.getString("description"),j.getString("photoId"),j.getString("season"),
            j.getString("style"),j.getString("availability"),j.getBoolean("favorite"),j.getBoolean("rarelyWorn"),j.getBoolean("archived"),
            j.getLong("createdAt"),if(j.isNull("priceMinor")) null else j.getLong("priceMinor"),j.getString("currency"),j.getString("subcategory"),
            j.getBoolean("suggestionExcluded"),j.getString("photoFingerprint"),
            if (j.isNull("customCategoryId")) null else j.getString("customCategoryId"),
            if (j.isNull("customColorId")) null else j.getString("customColorId"))
        require(PhotoStore.ID.matches(g.id) && PhotoStore.ID.matches(g.wardrobeId) && PhotoStore.ID.matches(g.photoId))
        require(g.name.trim().length in 1..120 && g.category in Matching.categories && g.color in Matching.colors && g.season in Matching.seasons && g.style in Matching.styles && g.availability in Matching.availability)
        require(g.size.length<=40 && g.location.length<=200 && g.description.length<=1000 && g.subcategory.length<=60 && WishRules.validMinor(g.priceMinor) && g.currency in wishCurrencies)
        require(g.customCategoryId == null || PhotoStore.ID.matches(g.customCategoryId))
        require(g.customColorId == null || PhotoStore.ID.matches(g.customColorId))
        require(g.createdAt >= 0 && (g.photoFingerprint.isEmpty() || g.photoFingerprint.matches(Regex("[a-f0-9]{64}"))))
        return g
    }
}

fun purchaseUndoReason(w: Wish, row: PurchaseUndoRow?, s: Snapshot): Int? {
    if (row == null) return R.string.purchase_undo_legacy
    val old=runCatching { GarmentReceipt.read(row.garmentJson) }.getOrNull() ?: return R.string.purchase_undo_unavailable
    if(w.ownedId != old.id || s.garments.find { it.id==old.id } != old) return R.string.purchase_undo_edited
    if(s.wears.any { it.garmentId==old.id }) return R.string.purchase_undo_worn
    if(s.capsuleItems.any { it.garmentId==old.id }) return R.string.purchase_undo_capsule
    s.parts.filter { it.garmentId==old.id }.forEach { part ->
        val categories = s.parts.filter { it.outfitId==part.outfitId && it.garmentId!=old.id }.mapNotNull { other -> s.garments.find { it.id==other.garmentId }?.category } +
            s.ideas.filter { it.outfitId==part.outfitId }.map { it.category } + w.category
        val signature = (s.parts.filter { it.outfitId==part.outfitId && it.garmentId!=old.id }.map { it.garmentId } +
            s.ideas.filter { it.outfitId==part.outfitId }.map { it.partId } + "wish:${w.id}").sorted().joinToString("|")
        if (!WardrobeRules.validCategories(categories) || s.outfits.any { it.id!=part.outfitId && it.wardrobeId==w.wardrobeId && it.signature==signature }) return R.string.purchase_undo_outfit
    }
    return null
}
fun unavailableReason(p: LookPiece): Int = when {
    p.kind=="missing" -> R.string.unavailable_deleted
    p.archived -> R.string.unavailable_archived
    p.availability=="laundry" -> R.string.unavailable_laundry
    p.availability=="repair" -> R.string.unavailable_repair
    p.availability=="lent" -> R.string.unavailable_lent
    else -> R.string.unavailable_generic
}
