package com.familywardrobe.app

import android.app.Application
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Entity(tableName = "wardrobes")
data class Wardrobe(@PrimaryKey val id: String = newId(), val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val usesDefaultName: Boolean = false,
    @ColumnInfo(defaultValue = "0") val noRepeatDays: Int = 0)

data class WardrobeDeletion(val nextWardrobeId: String, val photosRemoved: Boolean)

data class AvailabilityChange(val garmentId: String, val beforeAvailability: String, val beforeArchived: Boolean,
    val afterAvailability: String, val afterArchived: Boolean)

data class SavedItemNotice(val id: String, val wardrobe: String, val name: String, val category: String)

@Entity(tableName = "garments", foreignKeys = [ForeignKey(entity = Wardrobe::class,
    parentColumns = ["id"], childColumns = ["wardrobeId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = CustomCategory::class, parentColumns = ["id"], childColumns = ["customCategoryId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = CustomColor::class, parentColumns = ["id"], childColumns = ["customColorId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("wardrobeId"), Index("customCategoryId"), Index("customColorId")])
data class Garment(
    @PrimaryKey val id: String = newId(), val wardrobeId: String, val name: String,
    val category: String, val color: String, val size: String = "", val location: String = "",
    val description: String = "", val photoId: String, val season: String = "all",
    val style: String = "casual", val availability: String = "available",
    val favorite: Boolean = false, val rarelyWorn: Boolean = false, val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(), val priceMinor: Long? = null,
    @ColumnInfo(defaultValue = "'PLN'") val currency: String = "PLN",
    @ColumnInfo(defaultValue = "''") val subcategory: String = "",
    @ColumnInfo(defaultValue = "0") val suggestionExcluded: Boolean = false,
    @ColumnInfo(defaultValue = "''") val photoFingerprint: String = "",
    @ColumnInfo(defaultValue = "NULL") val customCategoryId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val customColorId: String? = null
)

@Entity(tableName = "outfits", foreignKeys = [ForeignKey(entity = Wardrobe::class,
    parentColumns = ["id"], childColumns = ["wardrobeId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("wardrobeId"), Index(value = ["wardrobeId", "signature"], unique = true)])
data class Outfit(@PrimaryKey val id: String = newId(), val wardrobeId: String,
    val name: String, val signature: String, val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'everyday'") val occasion: String = "everyday",
    @ColumnInfo(defaultValue = "'any_weather'") val weather: String = "any_weather",
    @ColumnInfo(defaultValue = "'owned'") val source: String = "owned",
    @ColumnInfo(defaultValue = "''") val layout: String = "",
    @ColumnInfo(defaultValue = "'paper'") val background: String = "paper")

@Entity(tableName = "outfit_parts", primaryKeys = ["outfitId", "garmentId"], foreignKeys = [
    ForeignKey(entity = Outfit::class, parentColumns = ["id"], childColumns = ["outfitId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Garment::class, parentColumns = ["id"], childColumns = ["garmentId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("garmentId")])
data class OutfitPart(val outfitId: String, val garmentId: String)

@Entity(tableName = "wears", primaryKeys = ["garmentId", "date", "source"], foreignKeys = [
    ForeignKey(entity = Garment::class, parentColumns = ["id"], childColumns = ["garmentId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("source")])
data class Wear(val garmentId: String, val date: String, val source: String)

@Entity(tableName = "wishes", foreignKeys = [ForeignKey(entity = Wardrobe::class,
    parentColumns = ["id"], childColumns = ["wardrobeId"], onDelete = ForeignKey.CASCADE)], indices = [Index("wardrobeId")])
data class Wish(@PrimaryKey val id: String = newId(), val wardrobeId: String, val name: String, val category: String,
    @ColumnInfo(defaultValue = "'need'") val kind: String = "need",
    val priceMinor: Long? = null,
    @ColumnInfo(defaultValue = "'PLN'") val currency: String = "PLN",
    @ColumnInfo(defaultValue = "''") val link: String = "",
    @ColumnInfo(defaultValue = "''") val color: String = "",
    @ColumnInfo(defaultValue = "''") val inspiration: String = "",
    @ColumnInfo(defaultValue = "''") val photoId: String = "",
    @ColumnInfo(defaultValue = "''") val needName: String = "",
    @ColumnInfo(defaultValue = "''") val needCategory: String = "",
    @ColumnInfo(defaultValue = "''") val requirements: String = "",
    val budgetMinor: Long? = null,
    @ColumnInfo(defaultValue = "'PLN'") val budgetCurrency: String = "PLN",
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val ownedId: String = "",
    @ColumnInfo(defaultValue = "''") val needPhotoId: String = "",
    @ColumnInfo(defaultValue = "''") val needLink: String = "",
    @ColumnInfo(defaultValue = "''") val needInspiration: String = "")

// Saved planned pieces keep a snapshot if a wishlist idea is later removed.
@Entity(tableName = "outfit_ideas", primaryKeys = ["outfitId", "partId"], foreignKeys = [
    ForeignKey(entity = Outfit::class, parentColumns = ["id"], childColumns = ["outfitId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Wish::class, parentColumns = ["id"], childColumns = ["wishId"], onDelete = ForeignKey.SET_NULL)
], indices = [Index("wishId")])
data class OutfitIdea(val outfitId: String, val partId: String, val wishId: String?,
    val category: String, val name: String, val color: String, val photoId: String)

@Dao
interface WardrobeDao {
    @Query("SELECT * FROM custom_colors ORDER BY nameKey, id") fun customColors(): Flow<List<CustomColor>>
    @Query("SELECT * FROM custom_colors") suspend fun allCustomColors(): List<CustomColor>
    @Insert suspend fun insertCustomColor(value: CustomColor)
    @Query("SELECT * FROM custom_categories ORDER BY nameKey, id") fun customCategories(): Flow<List<CustomCategory>>
    @Query("SELECT * FROM custom_categories") suspend fun allCustomCategories(): List<CustomCategory>
    @Insert suspend fun insertCustomCategory(value: CustomCategory)
    @Query("SELECT * FROM wardrobes ORDER BY createdAt") fun wardrobes(): Flow<List<Wardrobe>>
    @Query("SELECT * FROM garments ORDER BY createdAt DESC") fun garments(): Flow<List<Garment>>
    @Query("SELECT * FROM outfits ORDER BY createdAt DESC") fun outfits(): Flow<List<Outfit>>
    @Query("SELECT * FROM outfit_parts") fun parts(): Flow<List<OutfitPart>>
    @Query("SELECT * FROM wears ORDER BY date DESC") fun wears(): Flow<List<Wear>>
    @Query("SELECT * FROM wishes ORDER BY rowid DESC") fun wishes(): Flow<List<Wish>>
    @Query("SELECT * FROM wardrobes") suspend fun allWardrobes(): List<Wardrobe>
    @Query("SELECT * FROM garments") suspend fun allGarments(): List<Garment>
    @Query("UPDATE garments SET photoId=:replacement WHERE id=:id AND wardrobeId=:wardrobe AND photoId=:source")
    suspend fun replaceProcessedPhoto(id: String, wardrobe: String, source: String, replacement: String): Int
    @Query("SELECT * FROM outfits") suspend fun allOutfits(): List<Outfit>
    @Query("SELECT * FROM outfit_parts") suspend fun allParts(): List<OutfitPart>
    @Query("SELECT * FROM wears") suspend fun allWears(): List<Wear>
    @Query("SELECT * FROM wishes") suspend fun allWishes(): List<Wish>
    @Query("SELECT * FROM wishes WHERE id=:id") suspend fun wish(id: String): Wish?
    @Query("SELECT * FROM outfit_ideas") fun ideas(): Flow<List<OutfitIdea>>
    @Query("SELECT * FROM outfit_ideas") suspend fun allIdeas(): List<OutfitIdea>
    @Query("SELECT * FROM import_drafts ORDER BY createdAt, position") fun imports(): Flow<List<ImportDraft>>
    @Query("SELECT * FROM import_drafts ORDER BY createdAt, position") suspend fun allImports(): List<ImportDraft>
    @Query("SELECT * FROM import_drafts WHERE id=:id") suspend fun importDraft(id: String): ImportDraft?
    @Upsert suspend fun saveImport(value: ImportDraft)
    @Query("DELETE FROM import_drafts WHERE id=:id") suspend fun deleteImport(id: String)
    @Query("UPDATE import_drafts SET status='queued' WHERE status='working'") suspend fun resetInterruptedImports()
    @Query("SELECT * FROM day_plans ORDER BY date, id") fun plans(): Flow<List<DayPlan>>
    @Query("SELECT * FROM day_plans") suspend fun allPlans(): List<DayPlan>
    @Query("SELECT * FROM day_plans WHERE id=:id") suspend fun plan(id: String): DayPlan?
    @Upsert suspend fun savePlan(value: DayPlan)
    @Query("DELETE FROM day_plans WHERE id=:id") suspend fun deletePlan(id: String)
    @Query("SELECT * FROM outfit_feedback") fun feedback(): Flow<List<OutfitFeedback>>
    @Query("SELECT * FROM outfit_feedback") suspend fun allFeedback(): List<OutfitFeedback>
    @Upsert suspend fun saveFeedback(value: OutfitFeedback)
    @Query("DELETE FROM outfit_feedback WHERE id=:id") suspend fun deleteFeedback(id: String)
    @Query("DELETE FROM outfit_feedback WHERE wardrobeId=:wardrobe") suspend fun clearFeedback(wardrobe: String)
    @Query("SELECT * FROM capsules ORDER BY name") fun capsules(): Flow<List<Capsule>>
    @Query("SELECT * FROM capsules") suspend fun allCapsules(): List<Capsule>
    @Query("SELECT * FROM capsules WHERE id=:id") suspend fun capsule(id: String): Capsule?
    @Upsert suspend fun saveCapsule(value: Capsule)
    @Query("DELETE FROM capsules WHERE id=:id") suspend fun deleteCapsule(id: String)
    @Query("DELETE FROM capsules WHERE wardrobeId=:wardrobe") suspend fun deleteWardrobeCapsules(wardrobe: String)
    @Query("SELECT * FROM capsule_items") fun capsuleItems(): Flow<List<CapsuleItem>>
    @Query("SELECT * FROM capsule_items") suspend fun allCapsuleItems(): List<CapsuleItem>
    @Upsert suspend fun saveCapsuleItem(value: CapsuleItem)
    @Query("DELETE FROM capsule_items WHERE capsuleId=:capsuleId AND garmentId=:garmentId") suspend fun deleteCapsuleItem(capsuleId: String, garmentId: String)
    @Query("SELECT * FROM capsule_outfits") fun capsuleOutfits(): Flow<List<CapsuleOutfit>>
    @Query("SELECT * FROM capsule_outfits") suspend fun allCapsuleOutfits(): List<CapsuleOutfit>
    @Upsert suspend fun saveCapsuleOutfit(value: CapsuleOutfit)
    @Query("DELETE FROM capsule_outfits WHERE capsuleId=:capsuleId AND outfitId=:outfitId") suspend fun deleteCapsuleOutfit(capsuleId: String, outfitId: String)
    @Query("SELECT * FROM garments WHERE id=:id") suspend fun garment(id: String): Garment?
    @Query("SELECT * FROM outfits WHERE id=:id") suspend fun outfit(id: String): Outfit?
    @Query("SELECT * FROM outfits WHERE wardrobeId=:wardrobe AND signature=:signature LIMIT 1")
    suspend fun findOutfit(wardrobe: String, signature: String): Outfit?
    @Query("SELECT garmentId FROM outfit_parts WHERE outfitId=:id") suspend fun outfitIds(id: String): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWardrobe(value: Wardrobe)
    @Query("UPDATE wardrobes SET name=:name, usesDefaultName=0 WHERE id=:id") suspend fun renameWardrobe(id: String, name: String): Int
    @Query("DELETE FROM outfits WHERE wardrobeId=:wardrobe") suspend fun deleteWardrobeOutfits(wardrobe: String)
    @Query("DELETE FROM garments WHERE wardrobeId=:wardrobe") suspend fun deleteWardrobeGarments(wardrobe: String)
    @Query("UPDATE wardrobes SET noRepeatDays=:days WHERE id=:id") suspend fun setNoRepeatDays(id: String, days: Int): Int
    @Query("DELETE FROM wardrobes WHERE id=:id") suspend fun deleteWardrobeRow(id: String)
    @Upsert suspend fun saveGarment(value: Garment)
    @Insert suspend fun insertOutfit(value: Outfit)
    @Upsert suspend fun saveOutfitRow(value: Outfit)
    @Query("DELETE FROM outfit_parts WHERE outfitId=:id") suspend fun deleteParts(id: String)
    @Query("DELETE FROM outfit_ideas WHERE outfitId=:id") suspend fun deleteIdeas(id: String)
    @Query("DELETE FROM outfit_parts WHERE outfitId=:outfitId AND garmentId=:garmentId")
    suspend fun deletePart(outfitId: String, garmentId: String)
    @Query("DELETE FROM outfit_ideas WHERE outfitId=:outfitId AND partId=:partId")
    suspend fun deleteIdea(outfitId: String, partId: String)
    @Query("DELETE FROM garments WHERE id=:id") suspend fun deleteGarment(id: String)
    @Insert suspend fun insertParts(values: List<OutfitPart>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWears(values: List<Wear>)
    @Insert suspend fun insertWish(value: Wish)
    @Upsert suspend fun saveWish(value: Wish)
    @Insert suspend fun insertIdeas(values: List<OutfitIdea>)
    @Query("DELETE FROM wishes WHERE id=:id") suspend fun deleteWish(id: String)
    @Query("UPDATE outfit_ideas SET category=:category WHERE wishId=:wishId") suspend fun updateIdeaCategory(wishId: String, category: String)
    @Query("DELETE FROM wears WHERE garmentId=:id AND date=:date") suspend fun clearItemDay(id: String, date: String)
    @Query("DELETE FROM wears WHERE source=:source AND date=:date") suspend fun clearSourceDay(source: String, date: String)
    @Query("SELECT COUNT(*) FROM wears WHERE source=:source AND date=:date") suspend fun sourceCount(source: String, date: String): Int
    @Query("SELECT COUNT(*) FROM wears WHERE garmentId=:id AND date=:date") suspend fun itemCount(id: String, date: String): Int
    @Query("SELECT * FROM purchase_undo") fun purchaseUndos(): Flow<List<PurchaseUndoRow>>
    @Query("SELECT * FROM purchase_undo") suspend fun allPurchaseUndos(): List<PurchaseUndoRow>
    @Query("SELECT * FROM purchase_undo WHERE wishId=:id") suspend fun purchaseUndoRow(id: String): PurchaseUndoRow?
    @Upsert suspend fun savePurchaseUndo(value: PurchaseUndoRow)
    @Query("SELECT * FROM wish_events ORDER BY createdAt, id") fun wishEvents(): Flow<List<WishEvent>>
    @Query("SELECT * FROM wish_events ORDER BY createdAt, id") suspend fun allWishEvents(): List<WishEvent>
    @Insert suspend fun saveWishEvent(value: WishEvent)
    @Query("SELECT MAX(createdAt) FROM wish_events WHERE wishId=:id") suspend fun latestWishEventTime(id: String): Long?
    @Query("DELETE FROM purchase_undo WHERE wishId=:id") suspend fun deletePurchaseUndo(id: String)
    @Query("SELECT * FROM cloud_sync_meta WHERE id=1") fun cloudMetaFlow(): Flow<CloudSyncMeta?>
    @Query("SELECT * FROM cloud_sync_meta WHERE id=1") suspend fun cloudMeta(): CloudSyncMeta?
    @Upsert suspend fun saveCloudMeta(value: CloudSyncMeta)

}

@Database(entities = [Wardrobe::class, Garment::class, Outfit::class, OutfitPart::class, Wear::class, Wish::class, OutfitIdea::class, ImportDraft::class, DayPlan::class, OutfitFeedback::class, Capsule::class, CapsuleItem::class, CapsuleOutfit::class, PurchaseUndoRow::class, WishEvent::class, CloudSyncMeta::class, CustomCategory::class, CustomColor::class],
    version = 10, exportSchema = true)
abstract class WardrobeDatabase : RoomDatabase() {
    abstract fun dao(): WardrobeDao
    companion object {
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS custom_colors (id TEXT NOT NULL, wardrobeId TEXT NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL, baseColor TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(wardrobeId) REFERENCES wardrobes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_custom_colors_wardrobeId_nameKey ON custom_colors(wardrobeId, nameKey)")
                db.execSQL("ALTER TABLE garments ADD COLUMN customColorId TEXT DEFAULT NULL REFERENCES custom_colors(id) ON UPDATE NO ACTION ON DELETE RESTRICT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_garments_customColorId ON garments(customColorId)")
                ColorSchema.installTriggers(db)
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS custom_categories (id TEXT NOT NULL, wardrobeId TEXT NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL, baseCategory TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(wardrobeId) REFERENCES wardrobes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_custom_categories_wardrobeId_nameKey ON custom_categories(wardrobeId, nameKey)")
                db.execSQL("ALTER TABLE garments ADD COLUMN customCategoryId TEXT DEFAULT NULL REFERENCES custom_categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_garments_customCategoryId ON garments(customCategoryId)")
                CategorySchema.installTriggers(db)
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) { ScenarioSchema.create(db) }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wardrobes ADD COLUMN noRepeatDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE garments ADD COLUMN subcategory TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE garments ADD COLUMN suggestionExcluded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE outfits ADD COLUMN layout TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE garments ADD COLUMN photoFingerprint TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE import_drafts ADD COLUMN photoFingerprint TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE outfits ADD COLUMN background TEXT NOT NULL DEFAULT 'paper'")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE garments ADD COLUMN priceMinor INTEGER")
                db.execSQL("ALTER TABLE garments ADD COLUMN currency TEXT NOT NULL DEFAULT 'PLN'")
                db.execSQL("UPDATE garments SET priceMinor=(SELECT priceMinor FROM wishes WHERE ownedId=garments.id ORDER BY createdAt DESC, id LIMIT 1), currency=(SELECT currency FROM wishes WHERE ownedId=garments.id ORDER BY createdAt DESC, id LIMIT 1) WHERE EXISTS (SELECT 1 FROM wishes WHERE ownedId=garments.id)")
                db.execSQL("CREATE TABLE IF NOT EXISTS day_plans (id TEXT NOT NULL, wardrobeId TEXT NOT NULL, outfitId TEXT NOT NULL, date TEXT NOT NULL, notes TEXT NOT NULL, worn INTEGER NOT NULL, wornItemIds TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(wardrobeId) REFERENCES wardrobes(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(outfitId) REFERENCES outfits(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_day_plans_wardrobeId ON day_plans(wardrobeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_day_plans_outfitId ON day_plans(outfitId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_day_plans_wardrobeId_date_outfitId ON day_plans(wardrobeId, date, outfitId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS outfit_feedback (id TEXT NOT NULL, wardrobeId TEXT NOT NULL, signature TEXT NOT NULL, weather TEXT NOT NULL, occasion TEXT NOT NULL, reason TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(wardrobeId) REFERENCES wardrobes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_outfit_feedback_wardrobeId ON outfit_feedback(wardrobeId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_outfit_feedback_wardrobeId_signature_weather_occasion_reason ON outfit_feedback(wardrobeId, signature, weather, occasion, reason)")
                db.execSQL("CREATE TABLE IF NOT EXISTS capsules (id TEXT NOT NULL, wardrobeId TEXT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL, startDate TEXT NOT NULL, endDate TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(wardrobeId) REFERENCES wardrobes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_capsules_wardrobeId ON capsules(wardrobeId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS capsule_outfits (capsuleId TEXT NOT NULL, outfitId TEXT NOT NULL, PRIMARY KEY(capsuleId, outfitId), FOREIGN KEY(capsuleId) REFERENCES capsules(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(outfitId) REFERENCES outfits(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_capsule_outfits_outfitId ON capsule_outfits(outfitId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS capsule_items (capsuleId TEXT NOT NULL, garmentId TEXT NOT NULL, manual INTEGER NOT NULL, packed INTEGER NOT NULL, PRIMARY KEY(capsuleId, garmentId), FOREIGN KEY(capsuleId) REFERENCES capsules(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(garmentId) REFERENCES garments(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_capsule_items_garmentId ON capsule_items(garmentId)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS import_drafts (id TEXT NOT NULL, wardrobeId TEXT NOT NULL, batchId TEXT NOT NULL, position INTEGER NOT NULL, inputUri TEXT NOT NULL, photoId TEXT NOT NULL, originalPhotoId TEXT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, color TEXT NOT NULL, season TEXT NOT NULL, style TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(wardrobeId) REFERENCES wardrobes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_import_drafts_wardrobeId ON import_drafts(wardrobeId)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wishes ADD COLUMN ownedId TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wardrobes ADD COLUMN usesDefaultName INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE wardrobes SET usesDefaultName=1 WHERE id='00000000-0000-0000-0000-000000000001'")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wishes ADD COLUMN kind TEXT NOT NULL DEFAULT 'need'")
                db.execSQL("ALTER TABLE wishes ADD COLUMN priceMinor INTEGER")
                db.execSQL("ALTER TABLE wishes ADD COLUMN currency TEXT NOT NULL DEFAULT 'PLN'")
                db.execSQL("ALTER TABLE wishes ADD COLUMN link TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wishes ADD COLUMN color TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wishes ADD COLUMN inspiration TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wishes ADD COLUMN photoId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wishes ADD COLUMN needName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wishes ADD COLUMN needCategory TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wishes ADD COLUMN requirements TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE wishes ADD COLUMN budgetMinor INTEGER")
                db.execSQL("ALTER TABLE wishes ADD COLUMN budgetCurrency TEXT NOT NULL DEFAULT 'PLN'")
                db.execSQL("ALTER TABLE wishes ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE wishes SET needName=name, needCategory=category")
                db.execSQL("ALTER TABLE outfits ADD COLUMN occasion TEXT NOT NULL DEFAULT 'everyday'")
                db.execSQL("ALTER TABLE outfits ADD COLUMN weather TEXT NOT NULL DEFAULT 'any_weather'")
                db.execSQL("ALTER TABLE outfits ADD COLUMN source TEXT NOT NULL DEFAULT 'owned'")
                db.execSQL("CREATE TABLE IF NOT EXISTS outfit_ideas (outfitId TEXT NOT NULL, partId TEXT NOT NULL, wishId TEXT, category TEXT NOT NULL, name TEXT NOT NULL, color TEXT NOT NULL, photoId TEXT NOT NULL, PRIMARY KEY(outfitId, partId), FOREIGN KEY(outfitId) REFERENCES outfits(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(wishId) REFERENCES wishes(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_outfit_ideas_wishId ON outfit_ideas(wishId)")
            }
        }
        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
    }
}

data class Snapshot(val wardrobes: List<Wardrobe> = emptyList(), val garments: List<Garment> = emptyList(),
    val outfits: List<Outfit> = emptyList(), val parts: List<OutfitPart> = emptyList(),
    val wears: List<Wear> = emptyList(), val wishes: List<Wish> = emptyList(), val ideas: List<OutfitIdea> = emptyList(),
    val plans: List<DayPlan> = emptyList(), val feedback: List<OutfitFeedback> = emptyList(),
    val capsules: List<Capsule> = emptyList(), val capsuleItems: List<CapsuleItem> = emptyList(),
    val capsuleOutfits: List<CapsuleOutfit> = emptyList(), val purchaseUndos: List<PurchaseUndoRow> = emptyList(), val wishEvents: List<WishEvent> = emptyList(),
    val customCategories: List<CustomCategory> = emptyList(), val customColors: List<CustomColor> = emptyList()) {
    fun photoIds(): Set<String> = (garments.map { it.photoId } + wishes.flatMap { listOf(it.photoId, it.needPhotoId) } + ideas.map { it.photoId } + wishEvents.map { it.photoId }).filter { it.isNotBlank() }.toSet()
}

class Repository(val db: WardrobeDatabase, val writes: Mutex = Mutex()) {
    val dao = db.dao()
    internal var processingPhotos: () -> Set<String> = { emptySet() }
    internal var editorDraftPhotos: () -> Set<String> = { emptySet() }
    // Also shared by backup/export: keep a coherent DB + file snapshot during mutations.
    suspend fun initialize(name: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { writes.withLock {
        ScenarioSchema.installTriggers(db.openHelper.writableDatabase)
        CategorySchema.installTriggers(db.openHelper.writableDatabase)
        ColorSchema.installTriggers(db.openHelper.writableDatabase)
        if (dao.allWardrobes().isEmpty()) dao.insertWardrobe(Wardrobe(id = DEFAULT_WARDROBE, name = name, usesDefaultName = true))
    } }
    suspend fun save(garment: Garment, photos: PhotoStore? = null): Boolean = writes.withLock {
        require(TextLimits.hasVisibleText(garment.name) && garment.photoId.isNotBlank() && garment.category in Matching.categories && garment.color in Matching.colors)
        require(garment.priceMinor == null || WishRules.validMinor(garment.priceMinor))
        require(garment.currency in WishRules.CURRENCIES && garment.subcategory.length <= 60 && (garment.photoFingerprint.isEmpty() || Regex("[a-f0-9]{64}").matches(garment.photoFingerprint)))
        val previous = dao.garment(garment.id)?.photoId
        dao.saveGarment(garment)
        // Commit first. A failed DB write must leave the previous photo intact.
        if (photos != null && previous != null && previous != garment.photoId &&
            previous !in referencedPhotos()) photos.remove(setOf(previous)) else true
    }
    suspend fun referencedPhotos(): Set<String> = (dao.allGarments().map { it.photoId } + dao.allWishes().flatMap { listOf(it.photoId, it.needPhotoId) } +
        dao.allIdeas().map { it.photoId } + dao.allWishEvents().map { it.photoId } + dao.allImports().flatMap { listOf(it.photoId, it.originalPhotoId) }).filter { it.isNotBlank() }.toSet() + processingPhotos() + editorDraftPhotos()
    suspend fun discardPhotos(candidates: Set<String>, photos: PhotoStore): Boolean = writes.withLock {
        photos.remove(candidates - referencedPhotos())
    }
    suspend fun saveWish(value: Wish, converting: Boolean, photos: PhotoStore): Boolean = writes.withLock {
        validateWish(value)
        val previous = dao.wish(value.id)
        require(value.ownedId.isBlank() && previous?.ownedId.orEmpty().isBlank())
        val next = if (converting) {
            requireNotNull(previous)
            require(previous.kind == "need" || (previous.kind == "product" && previous.needName.isNotBlank()))
            value.copy(kind = "product", needName = previous.needName.ifBlank { previous.name },
                needCategory = previous.needCategory.ifBlank { previous.category }, requirements = previous.requirements,
                budgetMinor = previous.budgetMinor, budgetCurrency = previous.budgetCurrency, createdAt = previous.createdAt,
                needPhotoId = if (previous.kind == "need") previous.photoId else previous.needPhotoId,
                needLink = if (previous.kind == "need") previous.link else previous.needLink,
                needInspiration = if (previous.kind == "need") previous.inspiration else previous.needInspiration)
        } else if (previous?.kind == "product") value.copy(needName = previous.needName, needCategory = previous.needCategory,
            requirements = previous.requirements, budgetMinor = previous.budgetMinor, budgetCurrency = previous.budgetCurrency,
            createdAt = previous.createdAt, needPhotoId = previous.needPhotoId, needLink = previous.needLink, needInspiration = previous.needInspiration) else value
        require(previous == null || previous.wardrobeId == next.wardrobeId)
        require(previous == null || previous.kind == next.kind || converting)
        validateWish(next)
        val categoryChanged = previous != null && previous.category != next.category
        if (categoryChanged) {
            // A planned piece keeps its outfits valid: a new category must not create two tops or a dress with trousers.
            val ideas = dao.allIdeas(); val parts = dao.allParts()
            ideas.filter { it.wishId == next.id }.forEach { idea ->
                val categories = parts.filter { it.outfitId == idea.outfitId }.map { requireNotNull(dao.garment(it.garmentId)).category } +
                    ideas.filter { it.outfitId == idea.outfitId && it.partId != idea.partId }.map { it.category } + next.category
                if (!WardrobeRules.validCategories(categories)) throw WishCategoryInUseException()
            }
        }
        db.withTransaction {
            dao.saveWish(next)
            if (categoryChanged) dao.updateIdeaCategory(next.id, next.category)
            if (converting) recordWishEvent(next.event("candidate"))
        }
        val oldPhoto = previous?.photoId.orEmpty()
        if (oldPhoto.isNotBlank() && oldPhoto != next.photoId && oldPhoto !in referencedPhotos()) photos.remove(setOf(oldPhoto)) else true
    }
    suspend fun removeWish(id: String, photos: PhotoStore): Wish? = writes.withLock {
        val old = dao.wish(id) ?: return@withLock null
        require(old.ownedId.isBlank())
        val candidates = (listOf(old.photoId, old.needPhotoId) + dao.allWishEvents().filter { it.wishId == id }.map { it.photoId }).filter { it.isNotBlank() }.toSet()
        dao.deleteWish(id)
        // A saved outfit can still use the reference photograph.
        photos.remove(candidates - referencedPhotos())
        old
    }
    suspend fun update(id: String, transform: (Garment) -> Garment) = writes.withLock {
        dao.garment(id)?.let { dao.saveGarment(transform(it)) }
    }
    suspend fun changeAvailability(id: String, availability: String? = null, archived: Boolean? = null): AvailabilityChange? = writes.withLock {
        val before = dao.garment(id) ?: return@withLock null
        val after = before.copy(availability = availability ?: before.availability, archived = archived ?: before.archived)
        require(after.availability in Matching.availability)
        if (before.availability == after.availability && before.archived == after.archived) return@withLock null
        dao.saveGarment(after)
        AvailabilityChange(id, before.availability, before.archived, after.availability, after.archived)
    }
    suspend fun undoAvailability(change: AvailabilityChange): Boolean = writes.withLock {
        val current = dao.garment(change.garmentId) ?: return@withLock false
        if (current.availability != change.afterAvailability || current.archived != change.afterArchived) return@withLock false
        dao.saveGarment(current.copy(availability = change.beforeAvailability, archived = change.beforeArchived))
        true
    }
    suspend fun addWardrobe(name: String) = writes.withLock {
        require(name.trim().length in 1..80); dao.insertWardrobe(Wardrobe(name = name.trim()))
    }
    suspend fun renameWardrobe(id: String, name: String) = writes.withLock {
        require(name.trim().length in 1..80)
        require(dao.renameWardrobe(id, name.trim()) == 1)
    }
    suspend fun deleteWardrobe(id: String, photos: PhotoStore): WardrobeDeletion = writes.withLock {
        // The entire DB operation rolls back on failure. Never delete photographs before commit.
        val (next, unreferencedPhotos) = db.withTransaction {
            val wardrobes = dao.allWardrobes()
            require(wardrobes.size > 1 && wardrobes.any { it.id == id })
            val outfitIds = dao.allOutfits().filter { it.wardrobeId == id }.map { it.id }.toSet()
            val wishIds = dao.allWishes().filter { it.wardrobeId == id }.map { it.id }.toSet()
            val candidates = (dao.allGarments().filter { it.wardrobeId == id }.map { it.photoId } +
                dao.allWishes().filter { it.wardrobeId == id }.flatMap { listOf(it.photoId, it.needPhotoId) } +
                dao.allWishEvents().filter { it.wishId in wishIds }.map { it.photoId } +
                dao.allIdeas().filter { it.outfitId in outfitIds }.map { it.photoId } +
                dao.allImports().filter { it.wardrobeId == id }.flatMap { listOf(it.photoId, it.originalPhotoId) }).filter { it.isNotBlank() }.toSet()
            // Outfits cascade to parts; garments cascade to wears; wardrobes cascade to wishes.
            dao.deleteWardrobeCapsules(id)
            dao.deleteWardrobeOutfits(id)
            dao.deleteWardrobeGarments(id)
            dao.deleteWardrobeRow(id)
            val stillUsed = referencedPhotos()
            wardrobes.filter { it.id != id }.minBy { it.createdAt }.id to (candidates - stillUsed)
        }
        WardrobeDeletion(next, photos.remove(unreferencedPhotos))
    }
    suspend fun saveOutfit(wardrobe: String, ids: List<String>, name: String): String = writes.withLock {
        db.withTransaction {
            val garments = ids.distinct().map { requireNotNull(dao.garment(it)) }
            require(ids.size == ids.distinct().size && name.trim().length in 1..80)
            require(Matching.complete(garments) && garments.all { it.wardrobeId == wardrobe && WardrobeRules.ready(it.rulePiece()) })
            require(garments.all { a -> garments.all { b -> Matching.compatibleSeasons(a.season, b.season) } })
            val signature = ids.sorted().joinToString("|")
            val existing = dao.findOutfit(wardrobe, signature)
            if (existing != null) existing.id else {
                val outfit = Outfit(wardrobeId = wardrobe, name = name.trim(), signature = signature)
                dao.insertOutfit(outfit); dao.insertParts(ids.map { OutfitPart(outfit.id, it) }); outfit.id
            }
        }
    }
    suspend fun saveLook(wardrobe: String, pieces: List<LookPiece>, name: String, occasion: String, weather: String, source: String, outfitId: String = "", photos: PhotoStore? = null, layout: String? = null, background: String? = null): String = writes.withLock {
        val oldPhotos = dao.allIdeas().filter { it.outfitId == outfitId }.map { it.photoId }.filter { it.isNotBlank() }.toSet()
        val saved = db.withTransaction {
            require(name.trim().length in 1..80 && pieces.isNotEmpty() && pieces.size <= WardrobeRules.MAX_PARTS)
            require(pieces.all { it.category in Matching.categories && it.name.isNotBlank() })
            require(WardrobeRules.validCategories(pieces.map { it.category }) && pieces.map { it.key }.distinct().size == pieces.size)
            require(occasion in LookRules.OCCASIONS && weather in LookRules.WEATHERS && source in LookRules.SOURCES)
            val owned = pieces.filter { it.ownedId != null }.map { piece -> requireNotNull(dao.garment(requireNotNull(piece.ownedId))) }
            require(owned.all { it.wardrobeId == wardrobe && WardrobeRules.ready(it.rulePiece()) })
            require(owned.all { a -> owned.all { b -> Matching.compatibleSeasons(a.season, b.season) } })
            require(source != "owned" || owned.size == pieces.size)
            require(source != "inspiration" || owned.isEmpty())
            val planned = pieces.filter { it.ownedId == null }.map { piece ->
                val wish = piece.wishId?.let { requireNotNull(dao.wish(it)) }
                require(wish == null || wish.wardrobeId == wardrobe)
                require(wish == null || wish.ownedId.isBlank())
                require(source != "mixed" || wish != null)
                require(source != "inspiration" || (wish == null && InspirationCatalog.validKey(piece.key, piece.category)))
                require(wish == null || piece.key == "wish:${wish.id}")
                piece.copy(name = wish?.name ?: piece.name, category = wish?.category ?: piece.category,
                    color = wish?.color ?: piece.color, photoId = wish?.photoId ?: piece.photoId)
            }
            require(WardrobeRules.validCategories(owned.map { it.category } + planned.map { it.category }))
            val signature = pieces.map { if (it.ownedId != null) it.ownedId else it.key }.sorted().joinToString("|")
            val duplicate = dao.findOutfit(wardrobe, signature)
            if (outfitId.isBlank() && duplicate != null) duplicate.id else run {
                val previous = if (outfitId.isBlank()) null else requireNotNull(dao.outfit(outfitId))
                require(previous == null || previous.wardrobeId == wardrobe)
                require(duplicate == null || duplicate.id == previous?.id)
                val canvasLayout = OutfitCanvasState.reconcile(layout ?: previous?.layout.orEmpty(), pieces.map { it.key })
                val canvasBackground = background ?: previous?.background ?: "paper"
                require(canvasBackground in OutfitCanvasState.backgrounds)
                val outfit = (previous ?: Outfit(wardrobeId = wardrobe, name = name.trim(), signature = signature)).copy(
                    name = name.trim(), signature = signature, occasion = occasion, weather = weather, source = source, layout = canvasLayout, background = canvasBackground)
                dao.saveOutfitRow(outfit)
                dao.deleteParts(outfit.id); dao.deleteIdeas(outfit.id)
                dao.insertParts(owned.map { OutfitPart(outfit.id, it.id) })
                dao.insertIdeas(planned.map { OutfitIdea(outfit.id, it.key, it.wishId, it.category, it.name, it.color, it.photoId) })
                reconcileOutfitCapsules(outfit.id)
                outfit.id
            }
        }
        photos?.remove(oldPhotos - referencedPhotos())
        saved
    }
    suspend fun toggleItemWear(id: String, date: String = LocalDate.now().toString()) = writes.withLock {
        db.withTransaction {
            if (dao.itemCount(id, date) > 0) dao.clearItemDay(id, date)
            else dao.insertWears(listOf(Wear(id, date, "item:$id")))
            reconcilePlanWear(date)
        }
    }
    suspend fun toggleOutfitWear(id: String, date: String = LocalDate.now().toString()) = writes.withLock {
        db.withTransaction {
            val ids = dao.outfitIds(id)
            val source = "outfit:$id"
            if (ids.isNotEmpty() && dao.sourceCount(source, date) == ids.size) dao.clearSourceDay(source, date)
            else {
                require(dao.allIdeas().none { it.outfitId == id })
                val garments = ids.map { requireNotNull(dao.garment(it)) }
                require(garments.isNotEmpty() && garments.all { WardrobeRules.ready(it.rulePiece()) })
                dao.insertWears(ids.map { Wear(it, date, source) })
            }
        }
    }
    suspend fun snapshot(): Snapshot = db.withTransaction {
        Snapshot(dao.allWardrobes(), dao.allGarments(), dao.allOutfits(), dao.allParts(), dao.allWears(), dao.allWishes(), dao.allIdeas(), dao.allPlans(), dao.allFeedback(), dao.allCapsules(), dao.allCapsuleItems(), dao.allCapsuleOutfits(), dao.allPurchaseUndos(), dao.allWishEvents(), dao.allCustomCategories(), dao.allCustomColors())
    }
    companion object { const val DEFAULT_WARDROBE = "00000000-0000-0000-0000-000000000001" }
}

class WardrobeApplication : Application() {
    internal val sharedImports by lazy { SharedImportInbox(this) }
    val network by lazy { NetworkState(this) }
    internal val authBackend by lazy { FirebaseAuthBackend(this) }
    internal val accountRegistry by lazy { AccountRegistry(this) }
    internal val accountClosures = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    internal val backgroundScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
}
