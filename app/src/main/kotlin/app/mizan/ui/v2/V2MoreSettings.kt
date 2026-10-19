// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.repository.MizanRepository
import app.mizan.data.settings.SettingFlag
import kotlinx.coroutines.launch

@Composable
fun V2MoreScreen(onNavigate: (V2Route) -> Unit) {
    val groups =
        listOf(
            R.string.p13_faith to listOf(Triple("balance", R.string.p13_zakat, V2Route.Zakat)),
            R.string.p13_data to
                listOf(
                    Triple("upload_file", R.string.p13_import, V2Route.WalletImport),
                    Triple("download", R.string.p13_export, V2Route.Export),
                    Triple("delete", R.string.p13_trash, V2Route.Trash),
                ),
            R.string.p13_system_group to
                listOf(
                    Triple("database", R.string.p13_sources, V2Route.DataSources),
                    Triple("info", R.string.p13_about, V2Route.About),
                ),
        )
    V2SettingsList {
        groups.forEach { (heading, rows) ->
            item { Text(stringResource(heading), color = LocalMzTokens.current.text, style = MzTypography.title) }
            item {
                MzCard {
                    Column {
                        rows.forEach { (icon, label, route) ->
                            V2SettingRow(icon, stringResource(label)) { onNavigate(route) }
                        }
                    }
                }
            }
        }
        item { Text(stringResource(R.string.p13_privacy), color = LocalMzTokens.current.textDim) }
    }
}
