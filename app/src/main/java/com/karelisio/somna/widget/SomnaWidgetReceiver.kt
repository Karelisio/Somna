package com.karelisio.somna.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.karelisio.somna.container
import kotlinx.coroutines.launch

class SomnaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SomnaWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetTicker.arm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetTicker.cancel(context)
    }
}

/**
 * Keeps "si tu te couches maintenant" fresh: a non-waking RTC alarm every 5 minutes, delivered only
 * while the phone is awake, so it costs no battery at night.
 */
object WidgetTicker {
    private const val PERIOD_MS = 5 * 60_000L

    suspend fun schedule(context: Context) {
        val hasWidgets = runCatching {
            GlanceAppWidgetManager(context).getGlanceIds(SomnaWidget::class.java).isNotEmpty()
        }.getOrDefault(false)
        if (hasWidgets) arm(context) else cancel(context)
    }

    fun arm(context: Context) {
        val now = System.currentTimeMillis()
        val next = (now / PERIOD_MS + 1) * PERIOD_MS
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, next, pending(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 90, Intent(context, WidgetTickReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.container.appScope.launch {
            try {
                SomnaWidget.refresh(context)
                WidgetTicker.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
