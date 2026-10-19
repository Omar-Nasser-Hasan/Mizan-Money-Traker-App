// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.mizan.R

@Composable
fun V2SecureSetupScreen(
    canLock: Boolean,
    saving: Boolean,
    error: String?,
    onFinish: (Boolean, CharArray?) -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    var lockEnabled by rememberSaveable { mutableStateOf(false) }
    // Passphrases deliberately never enter saved state or the setup draft.
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val validationMessage = stringResource(R.string.setup_error_passphrase)
    V2SetupFrame(
        4,
        stringResource(R.string.setup_secure_title),
        stringResource(R.string.setup_secure_description),
        onBack,
        onContinue = {
            invalid = (passphrase.isNotEmpty() || confirmation.isNotEmpty()) && (passphrase.length < 8 || passphrase != confirmation)
            if (!invalid) onFinish(lockEnabled && canLock, passphrase.takeIf { it.isNotEmpty() }?.toCharArray())
        },
        onSkip = onSkip,
        continueLabel = stringResource(if (saving) R.string.setup_saving else R.string.setup_finish),
        enabled = !saving,
        skipEnabled = !saving,
        error = if (invalid) validationMessage else error,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                MzCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.setup_lock_toggle), style = MzTypography.title)
                                Text(stringResource(R.string.setup_lock_description), style = MzTypography.bodySmall)
                            }
                            Switch(lockEnabled, { lockEnabled = it }, enabled = canLock && !saving)
                        }
                        if (!canLock) Text(stringResource(R.string.setup_lock_unavailable), style = MzTypography.bodySmall)
                    }
                }
            }
            item {
                MzCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MzIcon("encrypted", stringResource(R.string.setup_passphrase), tint = LocalMzTokens.current.champagne)
                        Text(
                            stringResource(R.string.setup_passphrase_warning),
                            color = LocalMzTokens.current.warning,
                            style = MzTypography.body,
                        )
                        OutlinedTextField(
                            passphrase,
                            {
                                passphrase = it
                                invalid = false
                            },
                            label = { Text(stringResource(R.string.setup_passphrase)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !saving,
                            singleLine = true,
                        )
                        OutlinedTextField(
                            confirmation,
                            {
                                confirmation = it
                                invalid = false
                            },
                            label = { Text(stringResource(R.string.setup_confirm_passphrase)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !saving,
                            singleLine = true,
                        )
                    }
                }
            }
        }
    }
}
