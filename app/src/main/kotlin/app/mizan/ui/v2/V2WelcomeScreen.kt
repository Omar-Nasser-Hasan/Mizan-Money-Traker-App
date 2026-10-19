// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mizan.R

@Composable
fun V2WelcomeScreen(
    onStart: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MzIcon("balance", stringResource(R.string.app_name), tint = LocalMzTokens.current.champagne, size = 88.sp)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.app_name), style = MzTypography.hero, color = LocalMzTokens.current.text)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_tagline), style = MzTypography.title, color = LocalMzTokens.current.textDim)
        Spacer(Modifier.height(64.dp))
        MzButton(stringResource(R.string.setup_start), onStart, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        MzButton(stringResource(R.string.setup_restore), onRestore, Modifier.fillMaxWidth(), MzButtonStyle.SECONDARY)
    }
}
