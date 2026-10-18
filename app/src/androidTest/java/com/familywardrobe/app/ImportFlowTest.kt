package com.familywardrobe.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ImportFlowTest {
    private fun scenario(test: suspend (Repository, PhotoStore) -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WardrobeDatabase::class.java).build()
        try { val repo = Repository(db); repo.initialize("Home"); test(repo, PhotoStore(context)) } finally { db.close() }
    }
    @Test fun queuePersistsSourceAndRecoversInterruptedWorkWithoutSavingGarments() = scenario { repo, _ ->
        val rows = repo.enqueueImports(Repository.DEFAULT_WARDROBE, listOf("content://test/a", "content://test/b"))
        repo.dao.saveImport(rows.first().copy(status = "working", photoId = newId()))
        repo.dao.resetInterruptedImports()
        assertEquals(listOf("queued", "queued"), repo.dao.allImports().map { it.status })
        assertTrue(repo.dao.allGarments().isEmpty())
        assertEquals(1, rows.map { it.batchId }.distinct().size)
    }
    @Test fun reviewingCommitsOneItemAndReleasesOnlyItsQueueEntry() = scenario { repo, photos ->
        val rows = repo.enqueueImports(Repository.DEFAULT_WARDROBE, listOf("content://test/a", "content://test/b"))
        val source = newId(); val final = newId()
        val row = rows.first().copy(photoId = final, originalPhotoId = source, name = "Top", category = "top", status = "review")
        repo.dao.saveImport(row)
        val g = Garment(id = row.id, wardrobeId = row.wardrobeId, name = row.name, category = row.category, color = "navy", photoId = final)
        repo.saveImported(row.id, g, photos)
        assertEquals(g, repo.dao.garment(g.id))
        assertEquals(rows.last().id, repo.dao.allImports().single().id)
        assertTrue(final in repo.referencedPhotos())
        assertFalse(source in repo.referencedPhotos())
        try { repo.saveImported(row.id, g, photos); fail("Repeated review must not duplicate") } catch (_: IllegalArgumentException) { }
        assertEquals(1, repo.dao.allGarments().size)
    }
    @Test fun queueAndBulkActionsCannotCrossWardrobeBoundaries() = scenario { repo, photos ->
        val first = Garment(wardrobeId = Repository.DEFAULT_WARDROBE, name = "Top", category = "top", color = "navy", photoId = newId())
        repo.save(first)
        val other = Wardrobe(name = "Other"); repo.dao.insertWardrobe(other)
        val second = first.copy(id = newId(), wardrobeId = other.id); repo.save(second)
        try { repo.bulkChange(first.wardrobeId, setOf(first.id, second.id), "archive"); fail("Expected isolation") } catch (_: IllegalArgumentException) { }
        assertFalse(repo.dao.garment(first.id)!!.archived)
        val row = repo.enqueueImports(first.wardrobeId, listOf("content://test/a")).single().copy(status = "review", photoId = newId())
        repo.dao.saveImport(row)
        try { repo.saveImported(row.id, second.copy(id = row.id), photos); fail("Expected ownership check") } catch (_: IllegalArgumentException) { }
        assertNotNull(repo.dao.importDraft(row.id))
    }
    @Test fun bulkUndoKeepsIndependentEditsAndRejectsChangedAvailability() = scenario { repo, _ ->
        val g = Garment(wardrobeId = Repository.DEFAULT_WARDROBE, name = "Top", category = "top", color = "navy", photoId = newId())
        repo.save(g)
        val change = repo.bulkChange(g.wardrobeId, setOf(g.id), "laundry")
        repo.update(g.id) { it.copy(name = "Renamed") }
        assertTrue(repo.undoBulk(change))
        assertEquals("Renamed", repo.dao.garment(g.id)!!.name)
        val next = repo.bulkChange(g.wardrobeId, setOf(g.id), "archive")
        repo.changeAvailability(g.id, "repair")
        assertFalse(repo.undoBulk(next))
        assertEquals("repair", repo.dao.garment(g.id)!!.availability)
        assertTrue(repo.dao.garment(g.id)!!.archived)
    }
}
