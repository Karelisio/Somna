package com.karelisio.somna.alarm

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.somna.ui.theme.SomnaTheme

/**
 * Full-screen alarm UI, shown over the lock screen: sunrise light, ringing, wake-up mission and the
 * morning mood rating. Launched by the full-screen intent of [AlarmService]'s notification.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onBackPressedDispatcher.addCallback(this) {
            // The alarm can't be dismissed by accident with the back gesture.
            if (AlarmRuntime.session.value == null) finish()
        }
        startFromFallback(intent)
        setContent {
            val session by AlarmRuntime.session.collectAsStateWithLifecycle()
            val outcome by AlarmRuntime.outcome.collectAsStateWithLifecycle()
            // Keep the screen on only while something is in progress (not on the rating card).
            LaunchedEffect(session == null) {
                if (session == null) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            SomnaTheme(darkOverride = true, dynamicColor = true) {
                AlarmScreen(
                    session = session,
                    outcome = outcome,
                    onBrightness = ::setBrightness,
                    onFinish = { finishAndRemoveTask() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startFromFallback(intent)
    }

    /** Opened from the fallback notification: the service wasn't running, start it now (we're visible). */
    private fun startFromFallback(intent: Intent?) {
        val spec = AlarmSpec.fromJson(intent?.getStringExtra(AlarmScheduler.EXTRA_SPEC)) ?: return
        intent?.removeExtra(AlarmScheduler.EXTRA_SPEC)
        if (AlarmRuntime.session.value == null) {
            NotificationManagerCompat.from(this).cancel(Notifications.ID_FALLBACK)
            runCatching { AlarmService.start(this, AlarmService.ACTION_RING, spec) }
        }
    }

    private fun setBrightness(level: Float?) {
        window.attributes = window.attributes.apply {
            screenBrightness = level?.coerceIn(0.01f, 1f) ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }
}
