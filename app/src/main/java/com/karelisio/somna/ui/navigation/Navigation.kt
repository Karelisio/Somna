package com.karelisio.somna.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data object MainRoute
@Serializable data class AlarmEditRoute(val id: Int)
@Serializable data object SettingsRoute
@Serializable data object PermissionsRoute
@Serializable data object BatteryRoute

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Container transform between screens (e.g. alarm card → alarm editor). */
@Composable
fun Modifier.sharedContainer(key: String, shape: Shape): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/** Shared element that keeps its identity across screens (e.g. the alarm's time). */
@Composable
fun Modifier.sharedItem(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedItem.sharedElement(rememberSharedContentState(key), animated)
    }
}
