package com.karelisio.somna

import android.app.Application
import androidx.core.os.UserManagerCompat
import com.karelisio.somna.alarm.Notifications
import com.karelisio.somna.update.UpdateWorker
import com.karelisio.somna.widget.SomnaWidget
import com.karelisio.somna.widget.WidgetTicker
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SomnaApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // Before the first unlock (Direct Boot) only the alarm components run: no credential storage.
        if (UserManagerCompat.isUserUnlocked(this)) onUnlocked()
    }

    @OptIn(FlowPreview::class)
    private fun onUnlocked() {
        if (!BuildConfig.DEBUG) UpdateWorker.schedule(this)
        val settings = container.settings.settings
        container.appScope.launch {
            // Alarm specs embed some settings (crescendo, volume, snooze…): re-arm when they change.
            settings.map { listOf(it.sunriseMinutes, it.crescendoSeconds, it.alarmVolume, it.snoozeMinutes, it.smartSensitivity) }
                .distinctUntilChanged().drop(1).debounce(500)
                .collect { runCatching { container.alarms.rescheduleAll() } }
        }
        container.appScope.launch {
            settings.map { listOf(it.cycleMinutes, it.fallAsleepMinutes, it.preferredCycles, it.bedtimeReminder, it.dynamicColor) }
                .distinctUntilChanged().drop(1).debounce(500)
                .collect {
                    runCatching { container.bedtime.recompute() }
                    SomnaWidget.refresh(this@SomnaApp)
                }
        }
        container.appScope.launch {
            WidgetTicker.schedule(this@SomnaApp)
            runCatching { container.bedtime.recompute() }
        }
    }
}
