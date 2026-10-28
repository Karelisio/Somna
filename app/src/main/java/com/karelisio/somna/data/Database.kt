package com.karelisio.somna.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    /** Repeat days bitmask: bit 0 = Monday … bit 6 = Sunday. 0 = one-shot. */
    val days: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    val sound: String,
    val vibrate: Boolean = true,
    val sunrise: Boolean = true,
    /** Smart wake window in minutes before the alarm (0 = off, 20–30). */
    val smartWakeMinutes: Int = 0,
    val mission: String = Mission.NONE.name,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val repeating: Boolean get() = days != 0
    val missionType: Mission get() = runCatching { Mission.valueOf(mission) }.getOrDefault(Mission.NONE)

    fun repeatsOn(day: DayOfWeek): Boolean = days and (1 shl (day.value - 1)) != 0

    companion object {
        const val WEEKDAYS = 0b0011111
        const val WEEKEND = 0b1100000
        const val EVERY_DAY = 0b1111111
    }
}

@Entity(tableName = "sleep_entries")
data class SleepEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bedtime: Long,
    val wakeTime: Long,
    /** Wake-up mood 1 (😴) … 5 (😄); 0 = not rated yet. */
    val rating: Int = 0,
    val note: String = "",
) {
    val durationMinutes: Long get() = ((wakeTime - bedtime) / 60_000).coerceAtLeast(0)
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms")
    suspend fun all(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Int): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE id = :id")
    fun observe(id: Int): Flow<AlarmEntity?>

    @Query("SELECT * FROM alarms WHERE hour = :hour AND minute = :minute AND days = 0 LIMIT 1")
    suspend fun findOneShot(hour: Int, minute: Int): AlarmEntity?

    @Insert
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Delete
    suspend fun delete(alarm: AlarmEntity)
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_entries ORDER BY wakeTime DESC")
    fun observeAll(): Flow<List<SleepEntry>>

    @Query("SELECT * FROM sleep_entries WHERE wakeTime >= :since ORDER BY wakeTime ASC")
    fun observeSince(since: Long): Flow<List<SleepEntry>>

    @Query("SELECT * FROM sleep_entries WHERE id = :id")
    suspend fun get(id: Long): SleepEntry?

    @Query("SELECT * FROM sleep_entries ORDER BY wakeTime DESC LIMIT 1")
    suspend fun latest(): SleepEntry?

    @Insert
    suspend fun insert(entry: SleepEntry): Long

    @Update
    suspend fun update(entry: SleepEntry)

    @Delete
    suspend fun delete(entry: SleepEntry)
}

@Database(entities = [AlarmEntity::class, SleepEntry::class], version = 1, exportSchema = true)
abstract class SomnaDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun sleepDao(): SleepDao

    companion object {
        fun create(context: Context): SomnaDatabase =
            Room.databaseBuilder(context.applicationContext, SomnaDatabase::class.java, "somna.db").build()
    }
}
