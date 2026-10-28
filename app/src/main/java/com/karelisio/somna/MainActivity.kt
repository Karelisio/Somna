package com.karelisio.somna

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.karelisio.somna.ui.LocalSomna
import com.karelisio.somna.ui.SomnaViewModel
import com.karelisio.somna.ui.components.LocalHapticsEnabled
import com.karelisio.somna.ui.components.hideStatusBar
import com.karelisio.somna.ui.navigation.SomnaNavHost
import com.karelisio.somna.ui.theme.LocalDarkTheme
import com.karelisio.somna.ui.theme.SomnaTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val viewModel: SomnaViewModel by viewModels {
        viewModelFactory { initializer { SomnaViewModel(applicationContext.container) } }
    }

    /** Deep-link destination from notifications / widget (alarms, sounds, update…). */
    private val destination = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.settings.value == null }
        splash.setOnExitAnimationListener { provider ->
            val icon: View? = runCatching { provider.iconView }.getOrNull()
            val emphasized = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
            val animators = buildList {
                add(ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f).apply { startDelay = 120; duration = 380 })
                if (icon != null) {
                    add(ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.5f))
                    add(ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.5f))
                    add(ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f))
                }
            }
            AnimatorSet().apply {
                playTogether(animators)
                duration = 420
                interpolator = emphasized
                doOnEnd { provider.remove() }
                start()
            }
        }
        enableEdgeToEdge()
        window.hideStatusBar()
        if (savedInstanceState == null) {
            destination.value = intent.getStringExtra(EXTRA_DESTINATION)
            viewModel.checkUpdateOnLaunch()
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            SomnaTheme(themeMode = s.themeMode, dynamicColor = s.dynamicColor) {
                val dark = LocalDarkTheme.current
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    window.hideStatusBar()
                }
                CompositionLocalProvider(
                    LocalHapticsEnabled provides s.haptics,
                    LocalSomna provides viewModel,
                ) {
                    SomnaNavHost(settings = s, destination = destination)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Back from a dialog, the system settings or the notification shade: hide it again.
        if (hasFocus) window.hideStatusBar()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_DESTINATION)?.let { destination.value = it }
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
        const val DEST_ALARMS = "alarms"
        const val DEST_SOUNDS = "sounds"
        const val DEST_JOURNAL = "journal"
        const val DEST_SLEEP_NOW = "sleep_now"
        const val DEST_UPDATE = "update"
    }
}
