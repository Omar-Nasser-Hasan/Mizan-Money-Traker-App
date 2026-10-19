// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mizan.R

@Composable
fun V2LockScreen(
    timeoutSeconds: Int,
    onUnlock: () -> Unit,
    onDevicePin: () -> Unit,
    message: String? = null,
    prompting: Boolean = false,
) {
    Box(Modifier.fillMaxSize().background(LocalMzTokens.current.canvas).safeDrawingPadding()) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painterResource(R.drawable.ic_mizan_mark),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.lock_title),
                style = MzTypography.headline,
                color = LocalMzTokens.current.text,
                textAlign = TextAlign.Center,
            )
            Text(stringResource(R.string.lock_hidden), style = MzTypography.body, color = LocalMzTokens.current.textDim)
            Spacer(Modifier.height(24.dp))
            MzButton(stringResource(R.string.lock_unlock), onUnlock, Modifier.fillMaxWidth(), MzButtonStyle.SECONDARY, enabled = !prompting)
            MzButton(
                stringResource(R.string.lock_device_pin),
                onDevicePin,
                Modifier.fillMaxWidth(),
                MzButtonStyle.GHOST,
                enabled = !prompting,
            )
            message?.let { Text(it, style = MzTypography.bodySmall, color = LocalMzTokens.current.warning, textAlign = TextAlign.Center) }
        }
        Text(
            if (timeoutSeconds == 0) stringResource(R.string.lock_immediate) else stringResource(R.string.lock_timeout, timeoutSeconds),
            Modifier.align(Alignment.BottomCenter).padding(24.dp),
            style = MzTypography.bodySmall,
            color = LocalMzTokens.current.textDim,
            textAlign = TextAlign.Center,
        )
    }
}

/** The protected branch is absent from the composition until authentication succeeds. */
@Composable
fun V2PrivacyGate(
    blocked: Boolean,
    timeoutSeconds: Int,
    onUnlock: () -> Unit,
    onDevicePin: () -> Unit,
    message: String? = null,
    prompting: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (blocked) V2LockScreen(timeoutSeconds, onUnlock, onDevicePin, message, prompting) else content()
}
