// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mizan.BuildConfig
import app.mizan.R
import app.mizan.data.backup.LocalCrashLog
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun V2AboutScreen(repository: MizanRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    V2SettingsList {
        item {
            MzCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.p13_version, BuildConfig.VERSION_NAME), style = MzTypography.title)
                    Text(stringResource(R.string.p13_privacy))
                }
            }
        }
        item {
            MzCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.p13_licenses), style = MzTypography.title)
                    Text(stringResource(R.string.p13_font_license))
                    V2SettingRow("open_in_new", "ExchangeRate-API") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.exchangerate-api.com")))
                    }
                    V2SettingRow("open_in_new", "Daleelak") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://getdaleelak.com")))
                    }
                }
            }
        }
        item {
            MzCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.p13_log_note))
                    MzButton(stringResource(R.string.p13_export_log), {
                        scope.launch {
                            error =
                                runCatching {
                                    val file =
                                        withContext(Dispatchers.IO) {
                                            LocalCrashLog.export(
                                                File(context.filesDir, "logs"),
                                                File(context.filesDir, "exports/mizan-log.txt"),
                                                BuildConfig.VERSION_NAME,
                                            )
                                        }
                                    shareP13File(context, file, "text/plain")
                                }.isFailure
                        }
                    })
                }
            }
        }
        if (error) item { Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense) }
    }
}
