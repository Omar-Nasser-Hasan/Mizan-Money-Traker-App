// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import app.mizan.MainActivity

internal fun restartApp(context: Context) {
    val restart =
        PendingIntent.getActivity(
            context,
            93,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    context.getSystemService(AlarmManager::class.java).set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 500, restart)
    Process.killProcess(Process.myPid())
}
