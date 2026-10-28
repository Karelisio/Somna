package com.karelisio.somna

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.UserManagerCompat
import com.karelisio.somna.alarm.Notifications
import com.karelisio.somna.update.UpdateWorker
import com.karelisio.somna.widget.SomnaWidget
import com.karelisio.somna.widget.WidgetTicker
import java.util.concurrent.atomic.AtomicBoolean
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

    private val started = AtomicBoolean(false)

    /**
     * Work that needs credential storage. Runs once per process: at start when already unlocked,
     * otherwise from [com.karelisio.somna.alarm.BootReceiver] (BOOT_COMPLETED) or the first screen.
     */
    @OptIn(FlowPreview::class)
    fun onUnlocked() {
        if (!UserManagerCompat.isUserUnlocked(this) || !started.compareAndSet(false, true)) return
        if (!BuildConfig.DEBUG) UpdateWorker.schedule(this)
        publishShortcuts()
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

    /** Long-press shortcuts on the launcher icon. */
    private fun publishShortcuts() {
        fun shortcut(id: String, short: String, long: String, icon: Int, destination: String) =
            ShortcutInfoCompat.Builder(this, id)
                .setShortLabel(short)
                .setLongLabel(long)
                .setIcon(IconCompat.createWithResource(this, icon))
                .setIntent(
                    Intent(this, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(MainActivity.EXTRA_DESTINATION, destination)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
                .build()
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(
                this,
                listOf(
                    shortcut("sleep_now", "Je me couche", "Je me couche maintenant", R.mipmap.ic_shortcut_bedtime, MainActivity.DEST_SLEEP_NOW),
                    shortcut("nap", "Sieste 20 min", "Sieste de 20 minutes", R.mipmap.ic_shortcut_nap, MainActivity.DEST_NAP),
                    shortcut("new_alarm", "Nouveau réveil", "Nouveau réveil", R.mipmap.ic_shortcut_alarm, MainActivity.DEST_NEW_ALARM),
                ),
            )
        }
    }
}
