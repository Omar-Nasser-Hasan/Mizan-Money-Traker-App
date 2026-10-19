// Copyright (c) 2026 Mizan contributors
package app.mizan.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.mizan.data.settings.SettingFlag
import app.mizan.data.settings.SettingText
import app.mizan.data.settings.SettingsStore
import app.mizan.ui.v2.PeriodKind
import app.mizan.ui.v2.PeriodState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SettingsStoreTest {
    @Test fun every_toggle_and_text_round_trips_through_preferences() =
        runBlocking {
            val data =
                object : DataStore<Preferences> {
                    override val data = MutableStateFlow(emptyPreferences())
                    private val mutex = Mutex()

                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                        mutex.withLock {
                            transform(data.value).also { data.value = it }
                        }
                }
            run {
                val settings = SettingsStore(data)
                SettingFlag.entries.forEach { flag ->
                    settings.set(flag, !flag.default)
                    assertEquals(!flag.default, SettingsStore(data).snapshots.first()[flag])
                    settings.set(flag, flag.default)
                }
                settings.set(SettingText.APPEARANCE, "LIGHT")
                settings.set(SettingText.WEEK_START, "1")
                settings.set(SettingText.QUIET_START, "23:00")
                settings.set(SettingText.QUIET_END, "06:00")
                settings.spread("USD", "1.25")
                val saved = SettingsStore(data).snapshots.first()
                assertEquals("LIGHT", saved[SettingText.APPEARANCE])
                assertEquals(1, saved.weekStart)
                assertEquals("23:00", saved[SettingText.QUIET_START])
                assertEquals("06:00", saved[SettingText.QUIET_END])
                assertEquals("1.25", saved.spreads["USD"])
                assertTrue(saved[SettingFlag.BUDGET])
                assertFalse(saved[SettingFlag.DAILY])
                assertTrue(runCatching { settings.set(SettingText.WEEK_START, "8") }.isFailure)
                assertTrue(runCatching { settings.set(SettingText.APPEARANCE, "unknown") }.isFailure)
                assertTrue(runCatching { settings.set(SettingText.QUIET_START, "25:00") }.isFailure)
                assertTrue(runCatching { settings.spread("USD", "-1") }.isFailure)
            }
        }

    @Test fun week_start_changes_the_actual_week_window() {
        val anchor = LocalDate.parse("2026-10-19")
        assertEquals(LocalDate.parse("2026-10-17"), PeriodState(PeriodKind.WEEK).startOfWeek(anchor))
        assertEquals(anchor, PeriodState(PeriodKind.WEEK, weekStart = 1).startOfWeek(anchor))
        assertEquals(LocalDate.parse("2026-10-18"), PeriodState(PeriodKind.WEEK, weekStart = 7).startOfWeek(anchor))
    }
}
