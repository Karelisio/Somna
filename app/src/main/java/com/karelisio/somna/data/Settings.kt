package com.karelisio.somna.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karelisio.somna.audio.SoundType
import com.karelisio.somna.core.SleepCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
    AUTO_NIGHT("Auto (sombre la nuit)"),
    SYSTEM("Système"),
    LIGHT("Clair"),
    DARK("Sombre"),
}

enum class Mission(val label: String) {
    NONE("Aucune"),
    MATH("Calcul"),
    PUZZLE("Puzzle"),
}

/** Alarm sound id: a built-in [SoundType] name or [SYSTEM_SOUND] for the phone's alarm ringtone. */
const val SYSTEM_SOUND = "SYSTEM"

fun soundLabel(id: String): String =
    if (id == SYSTEM_SOUND) "Sonnerie du téléphone" else runCatching { SoundType.valueOf(id).label }.getOrDefault(id)

data class SomnaSettings(
    val onboardingDone: Boolean = false,
    // Sleep
    val cycleMinutes: Int = SleepCalculator.DEFAULT_CYCLE_MINUTES,
    val fallAsleepMinutes: Int = SleepCalculator.DEFAULT_FALL_ASLEEP_MINUTES,
    val preferredCycles: Int = 5,
    // Alarm defaults
    val defaultSound: String = SoundType.BIRDS.name,
    val sunriseEnabled: Boolean = true,
    val sunriseMinutes: Int = 10,
    val crescendoSeconds: Int = 90,
    val alarmVolume: Float = 0.8f,
    val vibration: Boolean = true,
    val snoozeMinutes: Int = 9,
    val mission: Mission = Mission.NONE,
    val smartWakeMinutes: Int = 0,
    val smartSensitivity: Int = 1,
    // Routine
    val bedtimeReminder: Boolean = true,
    val bedtimeOverride: Long = 0,
    val pendingBedtime: Long = 0,
    val sleepSound: String = SoundType.RAIN.name,
    val sleepTimerMinutes: Int = 30,
    val sleepVolume: Float = 0.6f,
    // Appearance
    val themeMode: ThemeMode = ThemeMode.AUTO_NIGHT,
    val dynamicColor: Boolean = true,
    val haptics: Boolean = true,
    // Permissions skipped during onboarding (discreet reminder later)
    val skippedPermissions: Set<String> = emptySet(),
    val permissionReminderDismissedAt: Long = 0,
    // Updates
    val autoUpdateCheck: Boolean = true,
    val skippedVersion: String = "",
    val lastUpdateCheck: Long = 0,
    val latestReleaseJson: String = "",
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "somna_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val cycleMinutes = intPreferencesKey("cycle_minutes")
        val fallAsleepMinutes = intPreferencesKey("fall_asleep_minutes")
        val preferredCycles = intPreferencesKey("preferred_cycles")
        val defaultSound = stringPreferencesKey("default_sound")
        val sunriseEnabled = booleanPreferencesKey("sunrise_enabled")
        val sunriseMinutes = intPreferencesKey("sunrise_minutes")
        val crescendoSeconds = intPreferencesKey("crescendo_seconds")
        val alarmVolume = floatPreferencesKey("alarm_volume")
        val vibration = booleanPreferencesKey("vibration")
        val snoozeMinutes = intPreferencesKey("snooze_minutes")
        val mission = stringPreferencesKey("mission")
        val smartWakeMinutes = intPreferencesKey("smart_wake_minutes")
        val smartSensitivity = intPreferencesKey("smart_sensitivity")
        val bedtimeReminder = booleanPreferencesKey("bedtime_reminder")
        val bedtimeOverride = longPreferencesKey("bedtime_override")
        val pendingBedtime = longPreferencesKey("pending_bedtime")
        val sleepSound = stringPreferencesKey("sleep_sound")
        val sleepTimerMinutes = intPreferencesKey("sleep_timer_minutes")
        val sleepVolume = floatPreferencesKey("sleep_volume")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val haptics = booleanPreferencesKey("haptics")
        val skippedPermissions = stringSetPreferencesKey("skipped_permissions")
        val permissionReminderDismissedAt = longPreferencesKey("permission_reminder_dismissed_at")
        val autoUpdateCheck = booleanPreferencesKey("auto_update_check")
        val skippedVersion = stringPreferencesKey("skipped_version")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val latestReleaseJson = stringPreferencesKey("latest_release_json")
    }

    val settings: Flow<SomnaSettings> = store.data.map { p ->
        val d = SomnaSettings()
        SomnaSettings(
            onboardingDone = p[Keys.onboardingDone] ?: d.onboardingDone,
            cycleMinutes = p[Keys.cycleMinutes] ?: d.cycleMinutes,
            fallAsleepMinutes = p[Keys.fallAsleepMinutes] ?: d.fallAsleepMinutes,
            preferredCycles = p[Keys.preferredCycles] ?: d.preferredCycles,
            defaultSound = p[Keys.defaultSound] ?: d.defaultSound,
            sunriseEnabled = p[Keys.sunriseEnabled] ?: d.sunriseEnabled,
            sunriseMinutes = p[Keys.sunriseMinutes] ?: d.sunriseMinutes,
            crescendoSeconds = p[Keys.crescendoSeconds] ?: d.crescendoSeconds,
            alarmVolume = p[Keys.alarmVolume] ?: d.alarmVolume,
            vibration = p[Keys.vibration] ?: d.vibration,
            snoozeMinutes = p[Keys.snoozeMinutes] ?: d.snoozeMinutes,
            mission = p[Keys.mission]?.let { runCatching { Mission.valueOf(it) }.getOrNull() } ?: d.mission,
            smartWakeMinutes = p[Keys.smartWakeMinutes] ?: d.smartWakeMinutes,
            smartSensitivity = p[Keys.smartSensitivity] ?: d.smartSensitivity,
            bedtimeReminder = p[Keys.bedtimeReminder] ?: d.bedtimeReminder,
            bedtimeOverride = p[Keys.bedtimeOverride] ?: d.bedtimeOverride,
            pendingBedtime = p[Keys.pendingBedtime] ?: d.pendingBedtime,
            sleepSound = p[Keys.sleepSound] ?: d.sleepSound,
            sleepTimerMinutes = p[Keys.sleepTimerMinutes] ?: d.sleepTimerMinutes,
            sleepVolume = p[Keys.sleepVolume] ?: d.sleepVolume,
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[Keys.dynamicColor] ?: d.dynamicColor,
            haptics = p[Keys.haptics] ?: d.haptics,
            skippedPermissions = p[Keys.skippedPermissions] ?: d.skippedPermissions,
            permissionReminderDismissedAt = p[Keys.permissionReminderDismissedAt] ?: d.permissionReminderDismissedAt,
            autoUpdateCheck = p[Keys.autoUpdateCheck] ?: d.autoUpdateCheck,
            skippedVersion = p[Keys.skippedVersion] ?: d.skippedVersion,
            lastUpdateCheck = p[Keys.lastUpdateCheck] ?: d.lastUpdateCheck,
            latestReleaseJson = p[Keys.latestReleaseJson] ?: d.latestReleaseJson,
        )
    }.distinctUntilChanged()

    suspend fun current(): SomnaSettings = settings.first()

    suspend fun setOnboardingDone(done: Boolean) = store.edit { it[Keys.onboardingDone] = done }
    suspend fun setCycleMinutes(v: Int) = store.edit {
        it[Keys.cycleMinutes] = v.coerceIn(SleepCalculator.MIN_CYCLE_MINUTES, SleepCalculator.MAX_CYCLE_MINUTES)
    }
    suspend fun setFallAsleepMinutes(v: Int) = store.edit {
        it[Keys.fallAsleepMinutes] = v.coerceIn(0, SleepCalculator.MAX_FALL_ASLEEP_MINUTES)
    }
    suspend fun setPreferredCycles(v: Int) = store.edit { it[Keys.preferredCycles] = v.coerceIn(4, 6) }
    suspend fun setDefaultSound(v: String) = store.edit { it[Keys.defaultSound] = v }
    suspend fun setSunriseEnabled(v: Boolean) = store.edit { it[Keys.sunriseEnabled] = v }
    suspend fun setSunriseMinutes(v: Int) = store.edit { it[Keys.sunriseMinutes] = v.coerceIn(5, 30) }
    suspend fun setCrescendoSeconds(v: Int) = store.edit { it[Keys.crescendoSeconds] = v.coerceIn(15, 300) }
    suspend fun setAlarmVolume(v: Float) = store.edit { it[Keys.alarmVolume] = v.coerceIn(0.1f, 1f) }
    suspend fun setVibration(v: Boolean) = store.edit { it[Keys.vibration] = v }
    suspend fun setSnoozeMinutes(v: Int) = store.edit { it[Keys.snoozeMinutes] = v.coerceIn(1, 30) }
    suspend fun setMission(v: Mission) = store.edit { it[Keys.mission] = v.name }
    suspend fun setSmartWakeMinutes(v: Int) = store.edit { it[Keys.smartWakeMinutes] = if (v == 0) 0 else v.coerceIn(20, 30) }
    suspend fun setSmartSensitivity(v: Int) = store.edit { it[Keys.smartSensitivity] = v.coerceIn(0, 2) }
    suspend fun setBedtimeReminder(v: Boolean) = store.edit { it[Keys.bedtimeReminder] = v }
    suspend fun setBedtimeOverride(v: Long) = store.edit { it[Keys.bedtimeOverride] = v }
    suspend fun setPendingBedtime(v: Long) = store.edit { it[Keys.pendingBedtime] = v }
    suspend fun setSleepSound(v: String) = store.edit { it[Keys.sleepSound] = v }
    suspend fun setSleepTimerMinutes(v: Int) = store.edit { it[Keys.sleepTimerMinutes] = v }
    suspend fun setSleepVolume(v: Float) = store.edit { it[Keys.sleepVolume] = v.coerceIn(0f, 1f) }
    suspend fun setThemeMode(v: ThemeMode) = store.edit { it[Keys.themeMode] = v.name }
    suspend fun setDynamicColor(v: Boolean) = store.edit { it[Keys.dynamicColor] = v }
    suspend fun setHaptics(v: Boolean) = store.edit { it[Keys.haptics] = v }
    suspend fun setSkippedPermissions(v: Set<String>) = store.edit { it[Keys.skippedPermissions] = v }
    suspend fun setPermissionReminderDismissedAt(v: Long) = store.edit { it[Keys.permissionReminderDismissedAt] = v }
    suspend fun setAutoUpdateCheck(v: Boolean) = store.edit { it[Keys.autoUpdateCheck] = v }
    suspend fun setSkippedVersion(v: String) = store.edit { it[Keys.skippedVersion] = v }
    suspend fun setUpdateCheckResult(time: Long, json: String) = store.edit {
        it[Keys.lastUpdateCheck] = time
        it[Keys.latestReleaseJson] = json
    }
}
