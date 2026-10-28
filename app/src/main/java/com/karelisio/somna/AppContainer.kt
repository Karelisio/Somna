package com.karelisio.somna

import android.content.Context
import com.karelisio.somna.alarm.AlarmRepository
import com.karelisio.somna.alarm.AlarmScheduler
import com.karelisio.somna.data.SettingsRepository
import com.karelisio.somna.data.SleepRepository
import com.karelisio.somna.data.SomnaDatabase
import com.karelisio.somna.routine.BedtimeScheduler
import com.karelisio.somna.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container. Everything is lazy so nothing touches credential storage before unlock. */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings by lazy { SettingsRepository(context) }
    val database by lazy { SomnaDatabase.create(context) }
    val scheduler by lazy { AlarmScheduler(context) }
    val bedtime by lazy { BedtimeScheduler(context, settings, database.alarmDao(), scheduler) }
    val alarms by lazy { AlarmRepository(context, database.alarmDao(), settings, scheduler, bedtime) }
    val sleep by lazy { SleepRepository(database.sleepDao(), settings) }
    val updates by lazy { UpdateRepository(context, settings) }
}

val Context.container: AppContainer get() = (applicationContext as SomnaApp).container
