package com.karelisio.somna.ui.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.karelisio.somna.data.SomnaSettings
import com.karelisio.somna.ui.alarms.AlarmEditScreen
import com.karelisio.somna.ui.home.MainScreen
import com.karelisio.somna.ui.onboarding.OnboardingScreen
import com.karelisio.somna.ui.settings.BatteryGuideScreen
import com.karelisio.somna.ui.settings.PermissionsScreen
import com.karelisio.somna.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun SomnaNavHost(settings: SomnaSettings, destination: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val start: Any = remember { if (settings.onboardingDone) MainRoute else OnboardingRoute }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val scale = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = nav,
                startDestination = start,
                // Shared-axis X, compatible with predictive back (the pop transitions are seekable).
                enterTransition = { slideInHorizontally(spatial) { it / 5 } + fadeIn(effects) },
                exitTransition = { slideOutHorizontally(spatial) { -it / 8 } + fadeOut(fastEffects) },
                popEnterTransition = { slideInHorizontally(spatial) { -it / 8 } + fadeIn(effects) },
                popExitTransition = { slideOutHorizontally(spatial) { it / 5 } + fadeOut(fastEffects) + scaleOut(scale, 0.94f) },
            ) {
                composable<OnboardingRoute> {
                    OnboardingScreen(
                        onFinished = {
                            nav.navigate(MainRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
                        },
                    )
                }
                composable<MainRoute> {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        MainScreen(
                            destination = destination,
                            onOpenAlarm = { nav.navigate(AlarmEditRoute(it)) },
                            onOpenSettings = { nav.navigate(SettingsRoute) },
                            onOpenPermissions = { nav.navigate(PermissionsRoute) },
                        )
                    }
                }
                composable<AlarmEditRoute> { entry ->
                    val route = entry.toRoute<AlarmEditRoute>()
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        AlarmEditScreen(alarmId = route.id, onBack = { nav.popBackStack() })
                    }
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenPermissions = { nav.navigate(PermissionsRoute) },
                        onOpenBattery = { nav.navigate(BatteryRoute) },
                    )
                }
                composable<PermissionsRoute> {
                    PermissionsScreen(onBack = { nav.popBackStack() }, onOpenBattery = { nav.navigate(BatteryRoute) })
                }
                composable<BatteryRoute> { BatteryGuideScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}
