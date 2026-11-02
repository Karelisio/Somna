package com.karelisio.somna.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.karelisio.somna.MainActivity
import com.karelisio.somna.R
import com.karelisio.somna.container
import com.karelisio.somna.core.TimeFormat
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * "Horloge Somna": hours over minutes, huge and bold, in the wallpaper's colours, plus the next alarm.
 * Plain RemoteViews around TextClock: the system ticks it, so the app never wakes up to refresh it.
 */
class ClockWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, manager, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        render(context, manager, intArrayOf(id))

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        context.container.appScope.launch {
            try {
                val next = runCatching { context.container.alarms.next() }.getOrNull()?.second
                val alarm = next?.let { "⏰ ${TimeFormat.relativeDay(it.toLocalDate())} ${TimeFormat.clock(it.toLocalTime())}" }
                ids.forEach { id -> manager.updateAppWidget(id, views(context, manager.getAppWidgetOptions(id), alarm)) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun views(context: Context, options: Bundle, alarm: String?) =
        RemoteViews(context.packageName, R.layout.widget_clock).apply {
            // Portrait: the smallest reported width, the tallest height.
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110).coerceAtLeast(90)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110).coerceAtLeast(90)
            val details = if (alarm != null) 44 else 24
            // Outfit bold digits are ~0.6 em wide; two lines at 0.8 spacing plus the last descent take ~2.4 em.
            val digits = min((width - 28) / 1.3f, (height - 28 - details) / 2.4f).coerceIn(28f, 140f)
            setTextViewTextSize(R.id.clock_time, TypedValue.COMPLEX_UNIT_DIP, digits)
            setTextViewTextSize(R.id.clock_date, TypedValue.COMPLEX_UNIT_DIP, (digits * 0.2f).coerceIn(12f, 18f))

            setViewVisibility(R.id.clock_alarm, if (alarm != null) View.VISIBLE else View.GONE)
            setTextViewText(R.id.clock_alarm, alarm.orEmpty())
            setOnClickPendingIntent(R.id.clock_root, open(context, null, 0))
            setOnClickPendingIntent(R.id.clock_alarm, open(context, MainActivity.DEST_ALARMS, 1))
        }

    private fun open(context: Context, destination: String?, code: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { destination?.let { putExtra(MainActivity.EXTRA_DESTINATION, it) } }
        return PendingIntent.getActivity(context, REQUEST_BASE + code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        private const val REQUEST_BASE = 9_100

        /** Re-renders every clock widget (the next alarm changed). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetReceiver::class.java))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, ClockWidgetReceiver::class.java)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                )
            }
        }
    }
}
