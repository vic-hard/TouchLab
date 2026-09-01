package com.lime.touchlab.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Доступ к локальному хранилищу.
 *
 * Все методы синхронные и вызываются только с фоновых потоков: Room по умолчанию
 * запрещает запросы на главном потоке, и этот запрет здесь намеренно не снят.
 *
 * Профиль дисплея и точка синхронизации вставляются с IGNORE: один профиль
 * переиспользуется многими попытками, и повторная вставка - это норма, а не ошибка.
 */
@Dao
interface TouchLabDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertDevice(device: DeviceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertDisplayProfile(profile: DisplayProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertClockSync(point: ClockSyncEntity)

    /** ABORT, а не IGNORE: повторный trial_id — это ошибка целостности, её нельзя проглотить. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertTrial(trial: TrialEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSamples(samples: List<TouchSampleEntity>)

    @Query(
        "UPDATE sessions SET ended_at_wall_clock_ms = :endedAtMs, session_status = :status " +
            "WHERE session_id = :sessionId",
    )
    fun closeSession(sessionId: String, endedAtMs: Long, status: String): Int

    /**
     * Пометить сессии, оставшиеся активными от прошлого запуска.
     *
     * Вызывается один раз на холодном старте, до начала любой новой сессии.
     * `ended_at_wall_clock_ms` намеренно остаётся null: календарное время завершения
     * такой сессии неизвестно, и подставлять текущее было бы враньём.
     */
    @Query("UPDATE sessions SET session_status = :incomplete WHERE session_status = :active")
    fun markActiveSessionsIncomplete(active: String, incomplete: String): Int

    @Query("SELECT * FROM sessions WHERE session_id = :sessionId")
    fun session(sessionId: String): SessionEntity?

    @Query(
        "SELECT session_id FROM sessions WHERE session_status = :status " +
            "ORDER BY started_at_wall_clock_ms DESC LIMIT 1",
    )
    fun lastSessionWithStatus(status: String): String?

    @Query("SELECT * FROM devices WHERE device_id = :deviceId")
    fun device(deviceId: String): DeviceEntity?

    @Query("SELECT * FROM trials WHERE session_id = :sessionId ORDER BY trial_index")
    fun trialsOf(sessionId: String): List<TrialEntity>

    @Query("SELECT COUNT(*) FROM trials WHERE session_id = :sessionId")
    fun trialCountOf(sessionId: String): Int

    @Query("SELECT * FROM clock_sync WHERE session_id = :sessionId")
    fun clockSyncOf(sessionId: String): List<ClockSyncEntity>

    @Query(
        "SELECT * FROM display_profiles WHERE display_profile_id IN " +
            "(SELECT DISTINCT display_profile_id FROM trials WHERE session_id = :sessionId)",
    )
    fun displayProfilesOf(sessionId: String): List<DisplayProfileEntity>

    /**
     * Отсчёты одной попытки. Выбираются попытка за попыткой, а не всей сессией разом:
     * сто попыток по ~250 отсчётов — это десятки тысяч строк, и держать их в памяти
     * одним списком незачем.
     */
    @Query("SELECT * FROM touch_samples WHERE trial_id = :trialId ORDER BY sample_index")
    fun samplesOf(trialId: String): List<TouchSampleEntity>
}
