// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.repository.MizanRepository
import app.mizan.data.settings.SettingText
import app.mizan.data.settings.SettingsSnapshot
import kotlinx.coroutines.launch

@Composable
fun V2SettingsList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun V2SettingRow(
    icon: String,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
) {
    MzListRow(icon, title, title, subtitle, Modifier.clickable(onClick = onClick), trailing = { MzIcon("chevron_right", title) })
}

@Composable
fun V2SettingsScreen(
    repository: MizanRepository,
    onNavigate: (V2Route) -> Unit,
) {
    val state by repository.settings.snapshots.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    V2SettingsHub(state, { key, value ->
        scope.launch { error = runCatching { repository.settings.set(key, value) }.isFailure }
    }, onNavigate, error)
}

@Composable
fun V2SettingsHub(
    state: SettingsSnapshot?,
    onChange: (SettingText, String) -> Unit,
    onNavigate: (V2Route) -> Unit,
    error: Boolean = false,
) {
    val days = stringArrayResource(R.array.p13_week_days)
    var showDays by remember { mutableStateOf(false) }
    val destinations =
        listOf(
            Triple(V2Route.Currencies, R.string.p13_currencies, "payments"),
            Triple(V2Route.Categories, R.string.p13_categories, "sell"),
            Triple(V2Route.Templates, R.string.p13_templates, "bookmarks"),
            Triple(V2Route.SecuritySettings, R.string.p13_security, "fingerprint"),
            Triple(V2Route.Backup, R.string.p13_backup, "cloud_done"),
            Triple(V2Route.Notifications, R.string.p13_notifications, "notifications"),
            Triple(V2Route.DataSources, R.string.p13_sources, "database"),
            Triple(V2Route.QuickEntrySettings, R.string.p13_quick, "bolt"),
            Triple(V2Route.About, R.string.p13_about, "info"),
            Triple(V2Route.More, R.string.p13_more, "more_horiz"),
        )
    V2SettingsList {
        if (state == null) {
            items(5) { MzSkeleton(Modifier.fillMaxWidth(), 80.dp) }
        } else {
            if (error) item { Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense) }
            item {
                MzCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.p13_appearance), style = MzTypography.title)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "DARK" to R.string.p13_dark,
                                "LIGHT" to R.string.p13_light,
                                "FOLLOW_SYSTEM" to R.string.p13_system,
                            ).forEach { (value, label) ->
                                MzChip(
                                    stringResource(label),
                                    state[SettingText.APPEARANCE] == value,
                                    { onChange(SettingText.APPEARANCE, value) },
                                )
                            }
                        }
                        MzToggle(state[SettingText.APPEARANCE] == "TRUE_BLACK", {
                            onChange(SettingText.APPEARANCE, if (it) "TRUE_BLACK" else "DARK")
                        }, stringResource(R.string.p13_black))
                    }
                }
            }
            item {
                MzCard {
                    V2SettingRow("tune", stringResource(R.string.p13_week_start), days[state.weekStart - 1]) { showDays = true }
                    DropdownMenu(showDays, { showDays = false }) {
                        days.forEachIndexed { index, day ->
                            DropdownMenuItem(text = { Text(day) }, onClick = {
                                onChange(SettingText.WEEK_START, (index + 1).toString())
                                showDays = false
                            })
                        }
                    }
                }
            }
            items(destinations, key = { it.first.path }) { (route, label, icon) ->
                MzCard { V2SettingRow(icon, stringResource(label)) { onNavigate(route) } }
            }
        }
    }
}
