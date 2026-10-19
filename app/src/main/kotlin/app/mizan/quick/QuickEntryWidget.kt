// Copyright (c) 2026 Mizan contributors
package app.mizan.quick

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.mizan.MainActivity
import app.mizan.data.settings.SettingFlag
import app.mizan.ui.v2.AmountStyle
import app.mizan.ui.v2.formatMizanAmount
import app.mizan.worker.WorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

class QuickEntryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val repository = EntryPointAccessors.fromApplication(context.applicationContext, WorkerEntryPoint::class.java).repository()
        val preferences = repository.settings.snapshots.first()
        val enabled = preferences[SettingFlag.WIDGET]
        val hideAmounts = repository.observeHideAmounts().first()
        val balance = repository.observeAccountBalances().first().firstOrNull()
        val display = balance?.let { widgetAmountText(it.entryMinor + it.openingMinor, it.currency, hideAmounts) }
        provideContent {
            val wide = LocalSize.current.width >= 220.dp
            Box(
                GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF17191D))).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(GlanceModifier.fillMaxSize(), verticalAlignment = androidx.glance.layout.Alignment.Vertical.CenterVertically) {
                    Row(verticalAlignment = androidx.glance.layout.Alignment.Vertical.CenterVertically) {
                        Text("MIZAN", style = TextStyle(color = ColorProvider(Color(0xFFE4C77B)), fontSize = 12.sp))
                        if (display != null) {
                            Spacer(GlanceModifier.width(8.dp))
                            Text(display, style = TextStyle(color = ColorProvider(Color(0xFFF3F0E8)), fontSize = 14.sp))
                        }
                    }
                    if (enabled) {
                        Row(verticalAlignment = androidx.glance.layout.Alignment.Vertical.CenterVertically) {
                            quickAction("+ Expense", context, ACTION_EXPENSE)
                            Spacer(GlanceModifier.width(12.dp))
                            if (wide) {
                                quickAction("+ Income", context, ACTION_INCOME)
                                Spacer(GlanceModifier.width(12.dp))
                            }
                            quickAction("Mic", context, ACTION_VOICE)
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun quickAction(
    label: String,
    context: Context,
    action: String,
) {
    Text(
        label,
        GlanceModifier.clickable(actionStartActivity(quickIntent(context, action))).padding(vertical = 8.dp, horizontal = 5.dp),
        style = TextStyle(color = ColorProvider(Color(0xFFF3F0E8)), fontSize = 13.sp),
    )
}

private fun quickIntent(
    context: Context,
    action: String,
): Intent =
    Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_QUICK_ACTION, action)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

class QuickEntryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickEntryWidget()
}

const val EXTRA_QUICK_ACTION = "app.mizan.extra.QUICK_ACTION"
const val ACTION_EXPENSE = "expense"
const val ACTION_INCOME = "income"
const val ACTION_VOICE = "voice"

internal fun widgetAmountText(
    minor: Long,
    currency: String,
    hideAmounts: Boolean,
): String = formatMizanAmount(minor, currency, AmountStyle.NEUTRAL, hideAmounts)
