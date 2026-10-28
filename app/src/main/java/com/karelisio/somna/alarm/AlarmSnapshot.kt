package com.karelisio.somna.alarm

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Copy of the scheduled alarms in device-protected storage, readable before the first unlock after
 * a reboot (Direct Boot) so alarms are re-armed even if the phone restarted during the night.
 */
class AlarmSnapshot(context: Context) {
    private val prefs: SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences("alarm_snapshot", Context.MODE_PRIVATE)

    fun put(spec: AlarmSpec) = prefs.edit { putString(key(spec.alarmId), spec.toJson()) }
    fun remove(alarmId: Int) = prefs.edit { remove(key(alarmId)) }
    fun putSnooze(spec: AlarmSpec) = prefs.edit { putString(snoozeKey(spec.alarmId), spec.toJson()) }
    fun removeSnooze(alarmId: Int) = prefs.edit { remove(snoozeKey(alarmId)) }

    fun specs(): List<AlarmSpec> = prefs.all.filterKeys { it.startsWith(PREFIX) }.values.mapNotNull { AlarmSpec.fromJson(it as? String) }
    fun snoozes(): List<AlarmSpec> = prefs.all.filterKeys { it.startsWith(SNOOZE_PREFIX) }.values.mapNotNull { AlarmSpec.fromJson(it as? String) }

    /** One-shot alarms that rang while the database was locked; disabled after unlock. */
    fun markOneShotFired(alarmId: Int) = prefs.edit {
        putStringSet(FIRED, (prefs.getStringSet(FIRED, emptySet()).orEmpty() + alarmId.toString()))
    }

    fun consumeFiredOneShots(): Set<Int> {
        val ids = prefs.getStringSet(FIRED, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        prefs.edit { remove(FIRED) }
        return ids
    }

    fun clear() = prefs.edit { clear() }

    private fun key(id: Int) = "$PREFIX$id"
    private fun snoozeKey(id: Int) = "$SNOOZE_PREFIX$id"

    private companion object {
        const val PREFIX = "alarm_"
        const val SNOOZE_PREFIX = "snooze_"
        const val FIRED = "fired_one_shots"
    }
}
