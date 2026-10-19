// Copyright (c) 2026 Mizan contributors
package app.mizan.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.mizan.data.backup.BackupCrypto
import app.mizan.data.backup.BackupPayload
import app.mizan.data.backup.LocalCrashLog
import app.mizan.data.backup.PreferenceBackup
import app.mizan.data.settings.QuietHours
import app.mizan.data.settings.SettingFlag
import app.mizan.data.settings.SettingsSnapshot
import app.mizan.worker.SettingsEffects
import app.mizan.worker.SettingsWorkPlan
import app.mizan.worker.ZakatReminderSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

class P13SettingsTest {
    @Test fun quiet_hours_cover_midnight_and_exact_boundaries() {
        val start = LocalTime.of(22, 0)
        val end = LocalTime.of(7, 0)
        assertTrue(QuietHours.muted(true, start, end, start))
        assertTrue(QuietHours.muted(true, start, end, LocalTime.MIDNIGHT))
        assertFalse(QuietHours.muted(true, start, end, end))
        assertFalse(QuietHours.muted(false, start, end, start))
        assertFalse(QuietHours.muted(true, start, start, start))
        assertTrue(QuietHours.muted(true, end, start, LocalTime.NOON))
        assertFalse(QuietHours.muted(true, end, start, LocalTime.MIDNIGHT))
    }

    @Test fun notification_flags_control_the_actual_work_plan() {
        val disabled = SettingFlag.entries.associateWith { false }
        val plan = SettingsWorkPlan.from(SettingsSnapshot(disabled))
        assertFalse(plan.planning)
        listOf(SettingFlag.PLANNED, SettingFlag.DEBT, SettingFlag.CARD, SettingFlag.BUDGET).forEach {
            assertTrue(SettingsWorkPlan.from(SettingsSnapshot(disabled + (it to true))).planning)
        }
        val sunday = ZonedDateTime.of(2026, 10, 18, 8, 0, 0, 0, ZoneId.of("Africa/Cairo"))
        assertEquals(3_600_000L, SettingsEffects.weeklyDelayMillis(sunday))
        assertEquals(7 * 24 * 3_600_000L, SettingsEffects.weeklyDelayMillis(sunday.plusHours(1)))
    }

    @Test fun preferences_round_trip_inside_the_real_encrypted_archive_without_device_keys() {
        val original =
            preferencesOf(
                booleanPreferencesKey("hide_amounts") to true,
                stringPreferencesKey("appearance") to "TRUE_BLACK",
                longPreferencesKey("stamp") to 1234L,
                stringPreferencesKey("backup_passphrase_keystore_wrapped") to "device-only",
            )
        val payload = BackupPayload(byteArrayOf(1, 2), "{}", preferences = PreferenceBackup.encode(original))
        val secret = "round trip passphrase".toCharArray()
        val encrypted = BackupCrypto.encrypt(payload, secret)
        val decoded = PreferenceBackup.decode(requireNotNull(BackupCrypto.decrypt(encrypted, secret).preferences))
        assertEquals(true, decoded[booleanPreferencesKey("hide_amounts")])
        assertEquals("TRUE_BLACK", decoded[stringPreferencesKey("appearance")])
        assertEquals(1234L, decoded[longPreferencesKey("stamp")])
        assertNull(decoded[stringPreferencesKey("backup_passphrase_keystore_wrapped")])
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("TRUE_BLACK"))
    }

    @Test fun local_logs_exclude_user_messages_and_export_the_real_file() {
        val directory = File(System.getProperty("java.io.tmpdir"), "mizan-log-test-${java.util.UUID.randomUUID()}")
        try {
            LocalCrashLog.write(directory, IllegalArgumentException("secret EGP 123.45"))
            val file = LocalCrashLog.export(directory, File(directory, "exports/log.txt"), "test")
            assertTrue(file.readText().contains("IllegalArgumentException"))
            assertFalse(file.readText().contains("secret EGP"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun hijri_anniversary_is_next_occurrence_and_clamps_day_30() {
        val now = ZonedDateTime.of(2026, 10, 19, 12, 0, 0, 0, ZoneId.of("Africa/Cairo"))
        val next = ZakatReminderSchedule.next(9, 1, now)
        assertTrue(next > now)
        assertEquals(9, HijrahDate.from(next).get(ChronoField.MONTH_OF_YEAR))
        assertEquals(1, HijrahDate.from(next).get(ChronoField.DAY_OF_MONTH))
        assertEquals(9, next.hour)
        val thirty = ZakatReminderSchedule.next(2, 30, now)
        val hijri = HijrahDate.from(thirty)
        assertEquals(hijri.lengthOfMonth(), hijri.get(ChronoField.DAY_OF_MONTH))
    }
}
