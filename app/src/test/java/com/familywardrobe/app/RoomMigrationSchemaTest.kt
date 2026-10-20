package com.familywardrobe.app

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the production Room migrations from Data.kt on real SQLite and compares the result with the
 * exported schema JSON, the way MigrationTestHelper validates tables (columns/affinity/not-null/
 * primary key/declared defaults, foreign keys and created indices). Triggers are not part of Room's
 * schema validation and are covered by the host checks.
 *
 * Schemas 5, 6 and 9 were never exported and the old sources are not in Git, so they cannot be
 * regenerated faithfully. Migrations 4→5→6→7 and 8→9→10 are therefore validated across the gap
 * against the next exported schema (7 and 10), plus the full 1→10 chain (audit 0.14.6 №13).
 */
class RoomMigrationSchemaTest {
    private val schemas = listOf(File("schemas"), File("app/schemas"))
        .map { File(it, "com.familywardrobe.app.WardrobeDatabase") }.first { it.isDirectory }
    private val exported = schemas.listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()

    private data class Column(val affinity: String, val notNull: Boolean, val primaryKey: Int, val default: String?)
    private data class Table(val columns: Map<String, Column>, val foreignKeys: List<List<String>>, val indices: List<List<String>>)

    private fun connection(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:")

    private fun create(version: Int): Connection {
        val json = File(schemas, "$version.json").readText()
        val db = connection()
        val statements = mutableListOf<String>()
        db.prepareStatement("""
            SELECT json_extract(e.value,'$.tableName'), json_extract(e.value,'$.createSql'), e.value
            FROM json_each(?, '$.database.entities') e""".trimIndent()).use { query ->
            query.setString(1, json)
            query.executeQuery().use { rows ->
                while (rows.next()) {
                    val table = rows.getString(1)
                    statements += rows.getString(2).replace("\${TABLE_NAME}", table)
                    db.prepareStatement("SELECT json_extract(i.value,'$.createSql') FROM json_each(?, '$.indices') i").use { idx ->
                        idx.setString(1, rows.getString(3))
                        idx.executeQuery().use { while (it.next()) statements += it.getString(1).replace("\${TABLE_NAME}", table) }
                    }
                }
            }
        }
        assertTrue("schema $version has no entities", statements.isNotEmpty())
        db.createStatement().use { s -> statements.forEach { s.execute(it) } }
        return db
    }

    /** Only execSQL is used by the production migrations; anything else fails loudly. */
    private fun support(db: Connection): SupportSQLiteDatabase = Proxy.newProxyInstance(
        SupportSQLiteDatabase::class.java.classLoader, arrayOf(SupportSQLiteDatabase::class.java)
    ) { proxy, method, args ->
        when (method.name) {
            "execSQL" -> {
                val sql = args!![0] as String
                if (args.size == 1) db.createStatement().use { it.execute(sql) }
                else db.prepareStatement(sql).use { st ->
                    (args[1] as Array<*>).forEachIndexed { i, v -> st.setObject(i + 1, v) }; st.execute()
                }
                null
            }
            "toString" -> "SupportSQLiteDatabase(jdbc)"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.get(0)
            else -> throw UnsupportedOperationException("Migration used ${method.name}; extend RoomMigrationSchemaTest")
        }
    } as SupportSQLiteDatabase

    private fun migrate(db: Connection, from: Int, to: Int) {
        val support = support(db)
        for (version in from until to) {
            val migration = WardrobeDatabase.ALL_MIGRATIONS.single { it.startVersion == version }
            assertEquals(version + 1, migration.endVersion)
            migration.migrate(support)
        }
    }

    private fun affinity(type: String?): String {
        val t = type.orEmpty().uppercase()
        return when {
            "INT" in t -> "INTEGER"
            "CHAR" in t || "CLOB" in t || "TEXT" in t -> "TEXT"
            "BLOB" in t || t.isEmpty() -> "BLOB"
            "REAL" in t || "FLOA" in t || "DOUB" in t -> "REAL"
            else -> "NUMERIC"
        }
    }

    private fun rows(db: Connection, sql: String): List<List<String?>> = db.createStatement().use { s ->
        s.executeQuery(sql).use { r ->
            val n = r.metaData.columnCount; val out = mutableListOf<List<String?>>()
            while (r.next()) out += (1..n).map { r.getString(it) }
            out
        }
    }

    private fun describe(db: Connection): Map<String, Table> = rows(db,
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table','android_metadata')")
        .map { it[0]!! }.associateWith { table ->
            val columns = rows(db, "PRAGMA table_info(`$table`)").associate {
                it[1]!! to Column(affinity(it[2]), it[3] == "1", it[5]!!.toInt(), it[4])
            }
            val foreignKeys = rows(db, "PRAGMA foreign_key_list(`$table`)").map { listOf(it[2], it[3], it[4], it[5], it[6]).map { v -> v.orEmpty() } }
                .sortedBy { it.joinToString("|") }
            val indices = rows(db, "PRAGMA index_list(`$table`)").filter { it[3] == "c" }.map { index ->
                listOf(index[1]!!, index[2]!!) + rows(db, "PRAGMA index_info(`${index[1]}`)").map { it[2].orEmpty() }
            }.sortedBy { it[0] }
            Table(columns, foreignKeys, indices)
        }

    /** Room's rule: a default declared by the entity must match; defaults added only by a migration are allowed. */
    private fun assertSchema(label: String, expected: Map<String, Table>, actual: Map<String, Table>) {
        assertEquals("$label: tables", expected.keys, actual.keys)
        for ((name, table) in expected) {
            val migrated = actual.getValue(name)
            assertEquals("$label: columns of $name", table.columns.keys, migrated.columns.keys)
            for ((column, info) in table.columns) {
                val got = migrated.columns.getValue(column)
                assertEquals("$label: $name.$column", info.copy(default = null), got.copy(default = null))
                if (info.default != null) assertEquals("$label: default of $name.$column", info.default, got.default)
            }
            assertEquals("$label: foreign keys of $name", table.foreignKeys, migrated.foreignKeys)
            assertEquals("$label: indices of $name", table.indices, migrated.indices)
        }
    }

    private fun check(from: Int, to: Int) {
        create(from).use { db ->
            migrate(db, from, to)
            create(to).use { expected -> assertSchema("$from→$to", describe(expected), describe(db)) }
        }
    }

    @Test fun migrationsCoverEveryVersionUpToTheCurrentSchema() {
        val current = exported.last()
        assertEquals((1 until current).toList(), WardrobeDatabase.ALL_MIGRATIONS.map { it.startVersion }.sorted())
        assertTrue("schemas 1 and $current must be exported", exported.first() == 1 && current >= 10)
    }

    @Test fun eachMigrationStepMatchesTheNextExportedSchema() {
        exported.zipWithNext().forEach { (from, to) -> check(from, to) }
    }

    @Test fun fullChainFromFirstSchemaMatchesCurrentSchema() = check(1, exported.last())

    @Test fun validatorRejectsAnUnmigratedDatabase() {
        val failed = runCatching {
            create(8).use { old -> create(10).use { current -> assertSchema("8 unmigrated", describe(current), describe(old)) } }
        }.isFailure
        assertTrue("an unmigrated v8 database must not pass as v10", failed)
    }

    /** Inserts a row filling every NOT NULL column without a default; [values] override specific columns. */
    private fun insert(db: Connection, table: String, values: Map<String, String>) {
        val info = rows(db, "PRAGMA table_info(`$table`)")
        val columns = info.filter { it[3] == "1" && it[4] == null || it[1] in values }.map { it[1]!! }
        val literals = columns.map { c -> values[c]?.let { "'$it'" } ?: if (affinity(info.single { it[1] == c }[2]) == "INTEGER") "0" else "''" }
        db.createStatement().use { it.execute("INSERT INTO `$table` (${columns.joinToString()}) VALUES (${literals.joinToString()})") }
    }

    @Test fun migrationKeepsExistingRowsAcrossTheUnexportedVersions() {
        create(8).use { db ->
            db.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
            insert(db, "wardrobes", mapOf("id" to "w", "name" to "Home"))
            insert(db, "garments", mapOf("id" to "g", "wardrobeId" to "w", "name" to "Coat"))
            migrate(db, 8, exported.last())
            assertEquals(listOf(listOf("g", "w", "Coat", null, null)),
                rows(db, "SELECT id, wardrobeId, name, customCategoryId, customColorId FROM garments"))
            assertTrue(rows(db, "PRAGMA foreign_key_check").isEmpty())
        }
    }
}
