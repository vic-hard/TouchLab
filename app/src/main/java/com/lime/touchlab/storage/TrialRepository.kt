package com.lime.touchlab.storage

import android.util.Log
import com.lime.rawtouchcollector.ClockSyncPoint
import com.lime.rawtouchcollector.DisplayProfile
import com.lime.rawtouchcollector.SessionInfo
import com.lime.rawtouchcollector.SessionStatus
import com.lime.rawtouchcollector.TrialSink
import com.lime.rawtouchcollector.TrialSnapshot
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Приёмник завершённых попыток поверх Room.
 *
 * `persist()` вызывается коллектором с его worker-потока и возвращает `true` только
 * после коммита транзакции. Это единственный источник истины для счётчика
 * «подтверждённо сохранённых»: показывать попытку сохранённой без
 * подтверждения запрещено.
 */
class TrialRepository(private val db: TouchLabDatabase) : TrialSink {

    private val dao: TouchLabDao = db.dao()

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "touchlab-storage").apply { isDaemon = true }
    }

    @Volatile
    private var pending: PendingSession? = null

    @Volatile
    private var sessionRowWritten: Boolean = false

    /**
     * Объявить сессию. Диска не касается — только запоминает данные и ставит вставку
     * строки в очередь собственного потока хранилища.
     */
    fun beginSession(info: SessionInfo, sessionClockSync: ClockSyncPoint?) {
        val device = info.device
        val naturalKey = device.manufacturer + "|" + device.model + "|" + device.sdkInt +
            "|" + device.appVersion + "|" + device.aarVersion + "|" + device.densityDpi
        val deviceId = UUID
            .nameUUIDFromBytes(naturalKey.toByteArray(StandardCharsets.UTF_8))
            .toString()

        sessionRowWritten = false
        pending = PendingSession(info, sessionClockSync, deviceId)
        io.execute {
            try {
                ensureSessionRow()
            } catch (e: RuntimeException) {
                Log.e(TAG, "не удалось записать строку сессии", e)
            }
        }
    }

    @Synchronized
    private fun ensureSessionRow() {
        if (sessionRowWritten) return
        val session = pending ?: return
        val info = session.info
        val device = info.device

        dao.insertDevice(
            DeviceEntity(
                deviceId = session.deviceId,
                manufacturer = device.manufacturer,
                model = device.model,
                androidVersion = device.androidVersion,
                sdkInt = device.sdkInt,
                appVersion = device.appVersion,
                aarVersion = device.aarVersion,
                densityDpi = device.densityDpi,
            ),
        )
        session.clockSync?.let { dao.insertClockSync(it.toEntity()) }
        dao.insertSession(
            SessionEntity(
                sessionId = info.sessionId,
                deviceId = session.deviceId,
                participantId = info.participantId,
                startedAtWallClockMs = info.startedAtWallClockMs,
                endedAtWallClockMs = null,
                phoneSupportMode = info.phoneSupportMode,
                clockSyncId = session.clockSync?.clockSyncId,
                sessionStatus = SessionStatus.ACTIVE,
                schemaVersion = info.schemaVersion,
            ),
        )
        sessionRowWritten = true
    }

    /**
     * Зафиксировать попытку. Возвращает true только после коммита.
     *
     * Вызывается с worker-потока коллектора; исключение наружу не выпускается —
     * библиотека трактует `false` как отказ записи и сама поднимает `WRITE_FAILURE`.
     */
    override fun persist(trial: TrialSnapshot): Boolean = try {
        db.runInTransaction<Boolean> {
            ensureSessionRow()
            dao.insertDisplayProfile(trial.displayProfile.toEntity())
            dao.insertClockSync(trial.clockSync.toEntity())
            dao.insertTrial(trial.toEntity())
            dao.insertSamples(trial.toSampleEntities())
            true
        }
    } catch (e: RuntimeException) {
        Log.e(TAG, "фиксация попытки " + trial.trialId + " не удалась", e)
        false
    }

    /**
     * Закрыть сессию календарным временем завершения.
     *
     * Вызывать только после возврата из `endSession()` коллектора: до этого барьер не
     * сомкнут, и часть попыток может быть ещё не зафиксирована.
     */
    fun closeSession(sessionId: String, endedAtWallClockMs: Long): Boolean = try {
        db.runInTransaction<Boolean> {
            ensureSessionRow()
            dao.closeSession(sessionId, endedAtWallClockMs, SessionStatus.COMPLETED) == 1
        }
    } catch (e: RuntimeException) {
        Log.e(TAG, "не удалось закрыть сессию " + sessionId, e)
        false
    }

    /**
     * Пометить сессии, пережившие убийство процесса. Возвращает их число.
     *
     * Вызывается один раз на холодном старте до начала любой новой сессии.
     * Запрещает молча продолжать старую активную сессию.
     */
    fun markCrashedSessions(): Int = try {
        dao.markActiveSessionsIncomplete(SessionStatus.ACTIVE, SessionStatus.INCOMPLETE)
    } catch (e: RuntimeException) {
        Log.e(TAG, "не удалось пометить незавершённые сессии", e)
        0
    }

    /** Последняя штатно закрытая сессия — кандидат на экспорт. */
    fun lastCompletedSessionId(): String? =
        try {
            dao.lastSessionWithStatus(SessionStatus.COMPLETED)
        } catch (e: RuntimeException) {
            Log.e(TAG, "не удалось найти последнюю закрытую сессию", e)
            null
        }

    fun trialCount(sessionId: String): Int =
        try {
            dao.trialCountOf(sessionId)
        } catch (e: RuntimeException) {
            0
        }

    /** Выполнить работу на потоке хранилища. */
    fun execute(task: Runnable) {
        io.execute(task)
    }

    private companion object {
        const val TAG = "TouchLabStorage"
    }
}

internal fun ClockSyncPoint.toEntity(): ClockSyncEntity = ClockSyncEntity(
    clockSyncId = clockSyncId,
    sessionId = sessionId,
    uptimeTimestampNs = uptimeTimestampNs,
    elapsedRealtimeTimestampNs = elapsedRealtimeTimestampNs,
    offsetNs = offsetNs,
    samplingUncertaintyNs = samplingUncertaintyNs,
    quantizationUncertaintyNs = quantizationUncertaintyNs,
    syncMethod = syncMethod,
    uptimeMeasurementPrecision = uptimeMeasurementPrecision,
)

internal fun DisplayProfile.toEntity(): DisplayProfileEntity = DisplayProfileEntity(
    displayProfileId = displayProfileId,
    windowWidthPx = windowWidthPx,
    windowHeightPx = windowHeightPx,
    displayModeWidthPx = displayModeWidthPx,
    displayModeHeightPx = displayModeHeightPx,
    displayRefreshRateHz = displayRefreshRateHz,
    densityDpi = densityDpi,
    capturedAtElapsedNs = capturedAtElapsedNs,
)

internal fun TrialSnapshot.toEntity(): TrialEntity = TrialEntity(
    trialId = trialId,
    sessionId = sessionId,
    participantId = participantId,
    trialIndex = trialIndex,
    taskGroup = taskGroup,
    scenarioType = scenarioType,
    displayProfileId = displayProfile.displayProfileId,
    clockSyncId = clockSync.clockSyncId,
    touchDownCommonTimestampNs = touchDownCommonTimestampNs,
    touchUpCommonTimestampNs = touchUpCommonTimestampNs,
    contactDurationNs = contactDurationNs,
    completionStatus = completionStatus,
    currentSampleCount = currentSampleCount,
    historicalSampleCount = historicalSampleCount,
    secondPointerObserved = secondPointerObserved,
    timestampPrecision = timestampPrecision,
    appReceiptUptimePrecision = appReceiptUptimePrecision,
    schemaVersion = schemaVersion,
)

internal fun TrialSnapshot.toSampleEntities(): List<TouchSampleEntity> {
    val s = samples
    val out = ArrayList<TouchSampleEntity>(s.count)
    for (i in 0 until s.count) {
        out.add(
            TouchSampleEntity(
                trialId = trialId,
                sampleIndex = i,
                eventAction = s.eventAction(i),
                pointerId = s.pointerId(i),
                pointerIndex = s.pointerIndex(i),
                actionIndex = s.actionIndex(i),
                pointerCount = s.pointerCount(i),
                eventTimeUptimeMs = s.eventTimeUptimeMs(i),
                eventTimeUptimeNs = s.eventTimeUptimeNs(i),
                commonTimestampNs = s.commonTimestampNs(i),
                appReceiptTimeUptimeNs = s.appReceiptTimeUptimeNs(i),
                appReceiptTimeElapsedNs = s.appReceiptTimeElapsedNs(i),
                relativeTimeMs = s.relativeTimeMs(i),
                x = s.x(i),
                y = s.y(i),
                touchMajor = s.touchMajor(i),
                touchMinor = s.touchMinor(i),
                size = s.size(i),
                pressure = s.pressure(i),
                orientation = s.orientation(i),
                toolType = s.toolType(i),
                isHistorical = s.isHistorical(i),
                // «Неприменимо» — явный null, а не -1: см. критерий 16
                historyIndex = if (s.isHistorical(i)) s.historyIndex(i) else null,
            ),
        )
    }
    return out
}
