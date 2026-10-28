package com.karelisio.somna.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.somna.AppContainer
import com.karelisio.somna.alarm.AlarmScheduler
import com.karelisio.somna.alarm.AlarmService
import com.karelisio.somna.alarm.AlarmSpec
import com.karelisio.somna.data.AlarmEntity
import com.karelisio.somna.data.SomnaSettings
import java.time.DayOfWeek
import java.time.ZonedDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmsViewModel(private val container: AppContainer) : ViewModel() {
    val alarms: StateFlow<List<AlarmEntity>?> =
        container.alarms.alarms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<SomnaSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SomnaSettings())

    val now: StateFlow<ZonedDateTime> = flow {
        while (true) {
            emit(ZonedDateTime.now())
            delay(15_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZonedDateTime.now())

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) = viewModelScope.launch { container.alarms.setEnabled(alarm, enabled) }
    fun delete(alarm: AlarmEntity) = viewModelScope.launch { container.alarms.delete(alarm) }
    fun restore(alarm: AlarmEntity) = viewModelScope.launch { container.alarms.restore(alarm) }
    suspend fun create(hour: Int, minute: Int): AlarmEntity = container.alarms.create(hour, minute)
}

class AlarmEditViewModel(private val container: AppContainer, val alarmId: Int) : ViewModel() {
    val alarm: StateFlow<AlarmEntity?> =
        container.alarms.observe(alarmId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<SomnaSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SomnaSettings())

    private var saveJob: Job? = null
    /** Latest edit not yet reflected by the database flow, so quick successive edits all apply. */
    private var pending: AlarmEntity? = null

    /** Applies a change immediately; text edits are debounced. */
    fun update(debounce: Boolean = false, change: (AlarmEntity) -> AlarmEntity) {
        val current = pending ?: alarm.value ?: return
        val updated = change(current)
        pending = updated
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            if (debounce) delay(400)
            withContext(NonCancellable) { container.alarms.save(updated) }
            if (pending == updated) pending = null
        }
    }

    fun delete(onDone: () -> Unit) {
        val current = alarm.value ?: return
        viewModelScope.launch {
            container.alarms.delete(current)
            onDone()
        }
    }

    /** Rings this alarm's configuration right now, without touching its schedule. */
    fun test(context: android.content.Context) {
        val current = alarm.value ?: return
        val spec: AlarmSpec = container.scheduler.buildSpec(current, settings.value, System.currentTimeMillis())
            .copy(alarmId = 0, test = true, sunriseMinutes = 0, smartMinutes = 0, crescendoSeconds = 20)
        AlarmService.start(context, AlarmService.ACTION_RING, spec)
    }
}

fun nextTrigger(alarm: AlarmEntity, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime =
    AlarmScheduler.nextTrigger(alarm.hour, alarm.minute, alarm.days, now)

private val dayLetters = listOf("L", "M", "M", "J", "V", "S", "D")
private val dayShort = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

fun dayLetter(day: DayOfWeek) = dayLetters[day.value - 1]

fun daysSummary(days: Int): String = when (days) {
    0 -> "Une fois"
    AlarmEntity.EVERY_DAY -> "Tous les jours"
    AlarmEntity.WEEKDAYS -> "En semaine"
    AlarmEntity.WEEKEND -> "Le week-end"
    else -> DayOfWeek.entries.filter { days and (1 shl (it.value - 1)) != 0 }.joinToString(" · ") { dayShort[it.value - 1] }
}
