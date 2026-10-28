package com.karelisio.somna.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.somna.AppContainer
import com.karelisio.somna.core.SleepCalculator
import com.karelisio.somna.core.SleepOption
import com.karelisio.somna.core.TimeFormat
import com.karelisio.somna.core.toEpochMillis
import com.karelisio.somna.data.AlarmEntity
import com.karelisio.somna.data.SomnaSettings
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CalcMode { WAKE_AT, SLEEP_NOW }

data class CreatedAlarm(val alarm: AlarmEntity, val existed: Boolean, val message: String)

class CalculatorViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<SomnaSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SomnaSettings())

    /** Current minute, ticking exactly on minute boundaries. */
    val now: StateFlow<LocalDateTime> = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now.withSecond(0).withNano(0))
            delay(60_000L - (now.second * 1000L + now.nano / 1_000_000))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now().withSecond(0).withNano(0))

    var mode by mutableStateOf(defaultMode())
    var wakeTarget by mutableStateOf(LocalTime.of(7, 0))
    /** Option highlighted in the night preview (last tapped, or the ideal one). */
    var focusedCycles by mutableStateOf(SleepCalculator.IDEAL_CYCLES)

    init {
        viewModelScope.launch {
            container.alarms.next()?.let { (alarm, _) -> wakeTarget = LocalTime.of(alarm.hour, alarm.minute) }
        }
    }

    fun wakeDateTime(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(wakeTarget)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun options(now: LocalDateTime, s: SomnaSettings): List<SleepOption> = when (mode) {
        CalcMode.SLEEP_NOW -> SleepCalculator.wakeTimesFor(now, s.cycleMinutes, s.fallAsleepMinutes)
        CalcMode.WAKE_AT -> SleepCalculator.bedtimesFor(wakeDateTime(now), s.cycleMinutes, s.fallAsleepMinutes)
    }

    fun shiftWakeTarget(minutes: Long) {
        wakeTarget = wakeTarget.plusMinutes(minutes)
    }

    /** Creates the alarm matching a tapped option. */
    suspend fun create(option: SleepOption): CreatedAlarm {
        focusedCycles = option.cycles
        val now = LocalDateTime.now()
        return when (mode) {
            CalcMode.SLEEP_NOW -> {
                val t = option.time
                val existed = container.database.alarmDao().findOneShot(t.hour, t.minute)?.enabled == true
                val alarm = container.alarms.create(t.hour, t.minute)
                container.sleep.markBedtime()
                CreatedAlarm(alarm, existed, "Réveil réglé à ${TimeFormat.clock(t)} · bonne nuit 🌙")
            }
            CalcMode.WAKE_AT -> {
                val wake = wakeDateTime(now)
                val existed = container.database.alarmDao().findOneShot(wake.hour, wake.minute)?.enabled == true
                val bedtime = option.time
                if (bedtime.isAfter(now)) container.settings.setBedtimeOverride(bedtime.toEpochMillis())
                val alarm = container.alarms.create(wake.hour, wake.minute)
                val reminder = if (bedtime.isAfter(now)) " · rappel à ${TimeFormat.clock(bedtime)}" else ""
                CreatedAlarm(alarm, existed, "Réveil réglé à ${TimeFormat.clock(wake)}$reminder")
            }
        }
    }

    /** Nap: 20–30 min power nap, or one full cycle. */
    suspend fun createNap(minutes: Int): CreatedAlarm {
        val alarm = container.alarms.createNap(minutes)
        return CreatedAlarm(alarm, existed = false, message = "Sieste de $minutes min · réveil à %02d:%02d 😴".format(alarm.hour, alarm.minute))
    }

    fun undo(created: CreatedAlarm) {
        viewModelScope.launch {
            if (!created.existed) container.alarms.delete(created.alarm)
            container.settings.setBedtimeOverride(0)
        }
    }

    private fun defaultMode(): CalcMode {
        val hour = LocalTime.now().hour
        return if (hour >= 19 || hour < 5) CalcMode.SLEEP_NOW else CalcMode.WAKE_AT
    }
}
