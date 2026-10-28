package com.karelisio.somna.ui.components

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Immersive mode: no status bar at the top (a swipe shows it briefly). */
fun Window.hideStatusBar() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        attributes = attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    WindowCompat.getInsetsController(this, decorView).apply {
        hide(WindowInsetsCompat.Type.statusBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

/** Top insets once the status bar is hidden: only the camera cutout remains to avoid. */
val TopInsets: WindowInsets
    @Composable get() = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

/** Dialogs and bottom sheets get their own window: keep the status bar hidden there too. */
@Composable
fun ImmersiveDialogWindow() {
    val view = LocalView.current
    LaunchedEffect(view) {
        var current: View? = view
        while (current != null) {
            (current as? DialogWindowProvider)?.window?.let {
                it.hideStatusBar()
                return@LaunchedEffect
            }
            current = current.parent as? View
        }
        // ModalBottomSheet: the dialog window is attached to the root view.
        (view.rootView.context as? android.app.Activity)?.window?.hideStatusBar()
    }
}
