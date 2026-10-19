// Copyright (c) 2026 Mizan contributors
package app.mizan.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mizan.data.settings.SettingFlag
import app.mizan.data.settings.SettingText
import app.mizan.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SettingsPersistenceInstrumentedTest {
    @Test fun every_toggle_survives_closing_and_reopening_the_real_datastore() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
            val firstJob = SupervisorJob()
            val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob)) { file }
            val settings = SettingsStore(data)
            SettingFlag.entries.forEach { settings.set(it, !it.default) }
            settings.set(SettingText.APPEARANCE, "TRUE_BLACK")
            settings.set(SettingText.WEEK_START, "1")
            settings.set(SettingText.DAILY_TIME, "19:30")
            settings.set(SettingText.QUIET_START, "23:00")
            settings.set(SettingText.QUIET_END, "06:30")
            firstJob.cancelAndJoin()
            val reopenedJob = SupervisorJob()
            try {
                val reopenedData = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + reopenedJob)) { file }
                val reopened = SettingsStore(reopenedData)
                val saved = reopened.snapshots.first()
                SettingFlag.entries.forEach { assertEquals(!it.default, saved[it]) }
                assertEquals("TRUE_BLACK", saved[SettingText.APPEARANCE])
                assertEquals(1, saved.weekStart)
                assertEquals("19:30", saved[SettingText.DAILY_TIME])
                assertEquals("23:00", saved[SettingText.QUIET_START])
                assertEquals("06:30", saved[SettingText.QUIET_END])
            } finally {
                reopenedJob.cancelAndJoin()
                file.delete()
            }
        }
}
