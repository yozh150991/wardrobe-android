package com.familywardrobe.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppearancePersistenceTest {
    @Test fun savedSelectionSurvivesClosingAndReopeningTheStore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "appearance-${UUID.randomUUID()}.preferences_pb")
        val firstJob = SupervisorJob()
        val expected = AppearanceSettings(WardrobePalette.BURGUNDY_LINEN, AppearanceMode.DARK)
        try {
            val first = PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file })
            val store = AppearanceStore(first)
            assertEquals(AppearanceSettings(), store.load())
            store.save(expected)
        } finally { firstJob.cancelAndJoin() }
        val secondJob = SupervisorJob()
        try {
            val reopened = PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file })
            assertEquals(expected, AppearanceStore(reopened).load())
        } finally { secondJob.cancelAndJoin(); file.delete() }
    }

    @Test fun unknownStoredValuesUseDefaultsWithoutTouchingWardrobeData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "appearance-${UUID.randomUUID()}.preferences_pb")
        val job = SupervisorJob()
        try {
            val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
            data.edit { it[stringPreferencesKey("palette")] = "future"; it[stringPreferencesKey("mode")] = "unknown" }
            assertEquals(AppearanceSettings(), AppearanceStore(data).load())
        } finally { job.cancelAndJoin(); file.delete() }
    }
}
