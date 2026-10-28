package com.karelisio.somna.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/** Subtle haptics, synced with animations. Disabled entirely when the user turns haptics off. */
class Haptics(private val view: View, private val enabled: Boolean) {
    fun tick() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    fun click() = perform(HapticFeedbackConstants.CONTEXT_CLICK)
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    fun reject() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    fun toggle(on: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT >= 34 && on -> HapticFeedbackConstants.TOGGLE_ON
            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.TOGGLE_OFF
            else -> HapticFeedbackConstants.CLOCK_TICK
        },
    )
    fun threshold() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CONTEXT_CLICK)
    fun heavy() = perform(HapticFeedbackConstants.LONG_PRESS)

    private fun perform(constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }
}

val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
