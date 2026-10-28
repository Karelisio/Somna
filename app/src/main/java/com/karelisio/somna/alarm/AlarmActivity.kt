package com.karelisio.somna.alarm

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
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
        setContent {
            val session by AlarmRuntime.session.collectAsStateWithLifecycle()
            val outcome by AlarmRuntime.outcome.collectAsStateWithLifecycle()
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
