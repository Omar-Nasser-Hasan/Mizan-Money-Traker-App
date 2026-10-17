// Copyright (c) 2026 Mizan contributors
package app.mizan.quick

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.text.Text
import app.mizan.MainActivity

class QuickEntryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            Column {
                Text("Mizan")
                Row {
                    Text("+ Expense", GlanceModifier.clickable(actionStartActivity(quickIntent(context, ACTION_EXPENSE))))
                    Text("+ Income", GlanceModifier.clickable(actionStartActivity(quickIntent(context, ACTION_INCOME))))
                    Text("Mic", GlanceModifier.clickable(actionStartActivity(quickIntent(context, ACTION_VOICE))))
                }
            }
        }
    }

    private fun quickIntent(
        context: Context,
        action: String,
    ): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_QUICK_ACTION, action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}

class QuickEntryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickEntryWidget()
}

const val EXTRA_QUICK_ACTION = "app.mizan.extra.QUICK_ACTION"
const val ACTION_EXPENSE = "expense"
const val ACTION_INCOME = "income"
const val ACTION_VOICE = "voice"
