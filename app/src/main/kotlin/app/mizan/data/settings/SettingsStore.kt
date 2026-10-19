// Copyright (c) 2026 Mizan contributors
package app.mizan.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalTime

enum class SettingFlag(
    val key: String,
    val default: Boolean,
) {
    DAILY("daily_expense_reminder_enabled", false),
    BUDGET("budget_alerts_enabled", true),
    PLANNED("planned_reminders_enabled", true),
    DEBT("debt_reminders_enabled", true),
    CARD("credit_card_reminders_enabled", true),
    WEEKLY("weekly_summary_enabled", false),
    QUIET("quiet_hours_enabled", true),
    BACKUP("daily_backup_enabled", true),
    GOLD_AUTO("gold_auto_enabled", true),
    ONLINE_VOICE("online_voice_enabled", false),
    WIDGET("widget_enabled", true),
    SMS("sms_inbox_enabled", false),
    NOTIFICATION_INBOX("notification_inbox_enabled", false),
    HIDE("hide_amounts", false),
    SECURE("secure_screens", false),
    LOCK("app_lock_enabled", false),
}

enum class SettingText(
    val key: String,
    val default: String,
) {
    APPEARANCE("appearance", "DARK"),
    WEEK_START("week_start", "6"),
    DAILY_TIME("daily_expense_reminder_time", "20:00"),
    QUIET_START("quiet_hours_start", "22:00"),
    QUIET_END("quiet_hours_end", "07:00"),
    LAST_SHARED("last_backup_shared", "0"),
}

data class SettingsSnapshot(
    val flags: Map<SettingFlag, Boolean> = emptyMap(),
    val texts: Map<SettingText, String> = emptyMap(),
    val pins: Map<String, String> = emptyMap(),
    val spreads: Map<String, String> = emptyMap(),
) {
    operator fun get(flag: SettingFlag): Boolean = flags[flag] ?: flag.default

    operator fun get(text: SettingText): String = texts[text] ?: text.default

    val weekStart: Int get() = get(SettingText.WEEK_START).toInt()
}

class SettingsStore(
    private val data: DataStore<Preferences>,
) {
    val snapshots =
        data.data.map { prefs ->
            SettingsSnapshot(
                SettingFlag.entries.associateWith { prefs[booleanPreferencesKey(it.key)] ?: it.default },
                SettingText.entries.associateWith { prefs[stringPreferencesKey(it.key)] ?: it.default },
                prefs
                    .asMap()
                    .mapNotNull { (key, value) ->
                        if (key.name.startsWith("fx_pin_")) key.name.removePrefix("fx_pin_") to value.toString() else null
                    }.toMap(),
                prefs
                    .asMap()
                    .mapNotNull { (key, value) ->
                        if (key.name.startsWith("fx_spread_")) key.name.removePrefix("fx_spread_") to value.toString() else null
                    }.toMap(),
            )
        }

    suspend fun set(
        flag: SettingFlag,
        enabled: Boolean,
    ) = data.edit { it[booleanPreferencesKey(flag.key)] = enabled }

    suspend fun set(
        text: SettingText,
        value: String,
    ) {
        when (text) {
            SettingText.APPEARANCE -> require(value in setOf("DARK", "LIGHT", "FOLLOW_SYSTEM", "TRUE_BLACK"))
            SettingText.WEEK_START -> require(value.toInt() in 1..7)
            SettingText.DAILY_TIME, SettingText.QUIET_START, SettingText.QUIET_END -> LocalTime.parse(value)
            SettingText.LAST_SHARED -> require(value.toLong() >= 0)
        }
        data.edit { it[stringPreferencesKey(text.key)] = value }
    }

    suspend fun spread(
        currency: String,
        value: String,
    ) {
        require(currency.matches(Regex("[A-Z]{3}")) && currency != "EGP")
        val decimal = BigDecimal(value)
        require(decimal >= BigDecimal.ZERO && decimal <= BigDecimal("100"))
        data.edit { it[stringPreferencesKey("fx_spread_$currency")] = decimal.toPlainString() }
    }
}
