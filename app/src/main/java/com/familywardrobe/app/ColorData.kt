package com.familywardrobe.app

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.sync.withLock

@Entity(tableName = "custom_colors", foreignKeys = [ForeignKey(entity = Wardrobe::class,
    parentColumns = ["id"], childColumns = ["wardrobeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["wardrobeId", "nameKey"], unique = true)])
data class CustomColor(@PrimaryKey val id: String = newId(), val wardrobeId: String,
    val name: String, val nameKey: String, val baseColor: String)

object ColorSchema {
    fun installTriggers(db: SupportSQLiteDatabase) {
        // Protect every writer, including batch import, purchase, restore and direct DAO writes.
        db.execSQL("CREATE TRIGGER IF NOT EXISTS color_garment_insert BEFORE INSERT ON garments WHEN NEW.customColorId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM custom_colors WHERE id=NEW.customColorId AND wardrobeId=NEW.wardrobeId AND baseColor=NEW.color) BEGIN SELECT RAISE(ABORT, 'Invalid garment color'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS color_garment_update BEFORE UPDATE ON garments WHEN NEW.customColorId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM custom_colors WHERE id=NEW.customColorId AND wardrobeId=NEW.wardrobeId AND baseColor=NEW.color) BEGIN SELECT RAISE(ABORT, 'Invalid garment color'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS color_definition_update BEFORE UPDATE ON custom_colors WHEN EXISTS (SELECT 1 FROM garments WHERE customColorId=OLD.id AND (NEW.id!=OLD.id OR NEW.wardrobeId!=wardrobeId OR NEW.baseColor!=color)) BEGIN SELECT RAISE(ABORT, 'Color is in use'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS sync_custom_colors_insert AFTER INSERT ON custom_colors WHEN (SELECT applying FROM cloud_sync_meta WHERE id=1)=0 BEGIN UPDATE cloud_sync_meta SET localRevision=localRevision+1, dirty=1 WHERE id=1; END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS sync_custom_colors_update AFTER UPDATE ON custom_colors WHEN (SELECT applying FROM cloud_sync_meta WHERE id=1)=0 BEGIN UPDATE cloud_sync_meta SET localRevision=localRevision+1, dirty=1 WHERE id=1; END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS sync_custom_colors_delete AFTER DELETE ON custom_colors WHEN (SELECT applying FROM cloud_sync_meta WHERE id=1)=0 BEGIN UPDATE cloud_sync_meta SET localRevision=localRevision+1, dirty=1 WHERE id=1; END")
    }
}

suspend fun Repository.addCustomColor(wardrobe: String, name: String, base: String): CustomColor = writes.withLock {
    require(CategoryRules.validName(name) && base in Matching.colors)
    require(dao.allWardrobes().any { it.id == wardrobe })
    val clean = CategoryRules.clean(name)
    val key = CategoryRules.key(clean)
    val existing = dao.allCustomColors().find { it.wardrobeId == wardrobe && it.nameKey == key }
    if (existing != null) {
        require(existing.baseColor == base)
        existing
    } else CustomColor(wardrobeId = wardrobe, name = clean, nameKey = key, baseColor = base)
        .also { dao.insertCustomColor(it) }
}

fun Snapshot.customColor(g: Garment): CustomColor? = customColors.find {
    it.id == g.customColorId && it.wardrobeId == g.wardrobeId && it.baseColor == g.color
}

