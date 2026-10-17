// Copyright (c) 2026 Mizan contributors
package app.mizan.sms

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class BankNotificationListener : NotificationListenerService() {
    @Inject lateinit var repository: app.mizan.data.repository.MizanRepository

    @Inject lateinit var capture: InboxCapture

    override fun onNotificationPosted(notification: StatusBarNotification) {
        val extras = notification.notification.extras
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        if (text.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            if (repository.observeNotificationInboxEnabled().first()) {
                capture.capture("NOTIFICATION", title, text, Instant.ofEpochMilli(notification.postTime))
            }
        }
    }
}
