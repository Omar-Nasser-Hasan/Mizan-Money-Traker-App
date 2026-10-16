// Copyright (c) 2026 Mizan contributors
package app.mizan.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val REMINDERS = "reminders"
    const val BUDGETS = "budget_alerts"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(REMINDERS, "Mizan reminders", NotificationManager.IMPORTANCE_DEFAULT))
            manager.createNotificationChannel(NotificationChannel(BUDGETS, "Mizan budget alerts", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
