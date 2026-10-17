// Copyright (c) 2026 Mizan contributors
package app.mizan.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class BankSmsReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: app.mizan.data.repository.MizanRepository

    @Inject lateinit var capture: InboxCapture

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!repository.observeSmsInboxEnabled().first()) return@launch
                Telephony.Sms.Intents.getMessagesFromIntent(intent).forEach { message ->
                    capture.capture(
                        "SMS",
                        message.originatingAddress.orEmpty(),
                        message.messageBody.orEmpty(),
                        Instant.ofEpochMilli(message.timestampMillis),
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
