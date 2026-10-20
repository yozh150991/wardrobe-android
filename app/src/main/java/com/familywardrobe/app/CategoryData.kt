package com.familywardrobe.app

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.sync.withLock

@Entity(tableName = "custom_categories", foreignKeys = [ForeignKey(entity = Wardrobe::class,
    parentColumns = ["id"], childColumns = ["wardrobeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["wardrobeId", "nameKey"], unique = true)])
data class CustomCategory(@PrimaryKey val id: String = newId(), val wardrobeId: String,
    val name: String, val nameKey: String, val baseCategory: String)

object CategorySchema {
    fun installTriggers(db: SupportSQLiteDatabase) {
        // Protect every writer, including batch import, purchase, restore and direct DAO writes.
        db.execSQL("CREATE TRIGGER IF NOT EXISTS category_garment_insert BEFORE INSERT ON garments WHEN NEW.customCategoryId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM custom_categories WHERE id=NEW.customCategoryId AND wardrobeId=NEW.wardrobeId AND baseCategory=NEW.category) BEGIN SELECT RAISE(ABORT, 'Invalid garment category'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS category_garment_update BEFORE UPDATE ON garments WHEN NEW.customCategoryId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM custom_categories WHERE id=NEW.customCategoryId AND wardrobeId=NEW.wardrobeId AND baseCategory=NEW.category) BEGIN SELECT RAISE(ABORT, 'Invalid garment category'); END")
        // Same guard as color_definition_update: a used category cannot change its id, wardrobe or base (BUG-10).
        db.execSQL("CREATE TRIGGER IF NOT EXISTS category_definition_update BEFORE UPDATE ON custom_categories WHEN EXISTS (SELECT 1 FROM garments WHERE customCategoryId=OLD.id AND (NEW.id!=OLD.id OR NEW.wardrobeId!=wardrobeId OR NEW.baseCategory!=category)) BEGIN SELECT RAISE(ABORT, 'Category is in use'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS sync_custom_categories_insert AFTER INSERT ON custom_categories WHEN (SELECT applying FROM cloud_sync_meta WHERE id=1)=0 BEGIN UPDATE cloud_sync_meta SET localRevision=localRevision+1, dirty=1 WHERE id=1; END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS sync_custom_categories_update AFTER UPDATE ON custom_categories WHEN (SELECT applying FROM cloud_sync_meta WHERE id=1)=0 BEGIN UPDATE cloud_sync_meta SET localRevision=localRevision+1, dirty=1 WHERE id=1; END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS sync_custom_categories_delete AFTER DELETE ON custom_categories WHEN (SELECT applying FROM cloud_sync_meta WHERE id=1)=0 BEGIN UPDATE cloud_sync_meta SET localRevision=localRevision+1, dirty=1 WHERE id=1; END")
    }
}

suspend fun Repository.addCustomCategory(wardrobe: String, name: String, base: String): CustomCategory = writes.withLock {
    require(CategoryRules.validName(name) && base in Matching.categories)
    require(dao.allWardrobes().any { it.id == wardrobe })
    val clean = CategoryRules.clean(name)
    val key = CategoryRules.key(clean)
    val existing = dao.allCustomCategories().find { it.wardrobeId == wardrobe && it.nameKey == key }
    if (existing != null) {
        require(existing.baseCategory == base)
        existing
    } else CustomCategory(wardrobeId = wardrobe, name = clean, nameKey = key, baseCategory = base)
        .also { dao.insertCustomCategory(it) }
}

fun Snapshot.customCategory(g: Garment): CustomCategory? = customCategories.find {
    it.id == g.customCategoryId && it.wardrobeId == g.wardrobeId && it.baseCategory == g.category
}

fun subcategoryOptions(garments: List<Garment>, wardrobe: String, category: String): List<String> = garments
    .filter { it.wardrobeId == wardrobe && CategoryRules.categoryMatches(category, it.category, it.customCategoryId) }
    .map { CategoryRules.clean(it.subcategory) }.filter { it.isNotEmpty() }
    .distinctBy { CategoryRules.key(it) }.sortedBy { CategoryRules.key(it) }
