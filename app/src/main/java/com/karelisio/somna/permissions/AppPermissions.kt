package com.karelisio.somna.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat

/** A permission Somna needs, with why, how to check it and how to request it. */
data class AppPermission(
    val id: String,
    val title: String,
    val rationale: String,
    val icon: ImageVector,
    /** Essential for the alarm to ring reliably. */
    val critical: Boolean,
    val runtimePermission: String? = null,
    val settingsIntent: ((Context) -> Intent)? = null,
    val check: (Context) -> Boolean,
) {
    fun isGranted(context: Context): Boolean = runCatching { check(context) }.getOrDefault(false)
}

object AppPermissions {
    const val NOTIFICATIONS = "notifications"
    const val EXACT_ALARMS = "exact_alarms"
    const val FULL_SCREEN = "full_screen"
    const val DND = "dnd"
    const val BATTERY = "battery"
    const val INSTALL = "install"

    private fun packageUri(context: Context) = Uri.fromParts("package", context.packageName, null)

    @SuppressLint("BatteryLife")
    fun all(): List<AppPermission> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                AppPermission(
                    id = NOTIFICATIONS,
                    title = "Notifications",
                    rationale = "Pour afficher le réveil, le snooze et le rappel « va te coucher ». Sans elles, le réveil ne peut pas s'afficher.",
                    icon = Icons.Rounded.NotificationsActive,
                    critical = true,
                    runtimePermission = Manifest.permission.POST_NOTIFICATIONS,
                    check = {
                        ContextCompat.checkSelfPermission(it, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    },
                ),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                AppPermission(
                    id = EXACT_ALARMS,
                    title = "Alarmes exactes",
                    rationale = "Pour que le réveil sonne à la minute près, même quand le téléphone est en veille profonde.",
                    icon = Icons.Rounded.AlarmOn,
                    critical = true,
                    settingsIntent = { Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(it)) },
                    check = { it.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() },
                ),
            )
        }
        if (Build.VERSION.SDK_INT >= 34) {
            add(
                AppPermission(
                    id = FULL_SCREEN,
                    title = "Réveil en plein écran",
                    rationale = "Pour allumer l'écran avec le lever de soleil et afficher le réveil par-dessus l'écran de verrouillage.",
                    icon = Icons.Rounded.Fullscreen,
                    critical = true,
                    settingsIntent = { Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(it)) },
                    check = { it.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() },
                ),
            )
        }
        add(
            AppPermission(
                id = DND,
                title = "Ne pas déranger",
                rationale = "Pour que le réveil passe outre le mode « Ne pas déranger » et le lever de soleil reste visible.",
                icon = Icons.Rounded.DoNotDisturbOn,
                critical = false,
                settingsIntent = { Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
                check = { it.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted },
            ),
        )
        add(
            AppPermission(
                id = BATTERY,
                title = "Optimisation de la batterie",
                rationale = "Certains téléphones (Honor, Huawei, Xiaomi…) coupent les applis en arrière-plan. Exclure Somna garantit que le réveil sonne.",
                icon = Icons.Rounded.BatteryChargingFull,
                critical = true,
                settingsIntent = { Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(it)) },
                check = { it.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(it.packageName) },
            ),
        )
        add(
            AppPermission(
                id = INSTALL,
                title = "Installer les mises à jour",
                rationale = "Pour installer les nouvelles versions de Somna directement depuis l'appli, sans passer par un store.",
                icon = Icons.Rounded.InstallMobile,
                critical = false,
                settingsIntent = { Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri(it)) },
                check = { it.packageManager.canRequestPackageInstalls() },
            ),
        )
    }

    fun byId(id: String): AppPermission? = all().firstOrNull { it.id == id }

    fun missing(context: Context): List<AppPermission> = all().filterNot { it.isGranted(context) }

    fun appDetailsIntent(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
}

/** Manufacturer-specific background restrictions (Honor / MagicOS in particular). */
object OemBattery {
    private val manufacturer = Build.MANUFACTURER.lowercase()
    val isHonorOrHuawei: Boolean = manufacturer in setOf("honor", "huawei")
    val brand: String = Build.MANUFACTURER.replaceFirstChar { it.titlecase() }

    private val launchManagers = listOf(
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
    )

    /** Opens the OEM "app launch" manager when it exists, otherwise the app's system settings. */
    fun openLaunchManager(context: Context): Boolean {
        for (component in launchManagers) {
            try {
                context.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return runCatching {
            context.startActivity(AppPermissions.appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    fun openBatterySettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
