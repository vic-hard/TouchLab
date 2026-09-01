package com.lime.rawtouchcollector

import android.content.Context
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import com.lime.rawtouchcollector.internal.capture.SampleBuffer
import com.lime.rawtouchcollector.internal.capture.SessionState
import com.lime.rawtouchcollector.internal.capture.TrialState
import com.lime.rawtouchcollector.internal.json.TrialJsonWriter
import com.lime.rawtouchcollector.internal.pipeline.PersistWorker
import com.lime.rawtouchcollector.internal.session.DisplayProfileRegistry
import com.lime.rawtouchcollector.internal.time.ClockSyncCapture
import com.lime.rawtouchcollector.internal.time.TimeSource
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Сбор сырых данных касания.
 *
 * Не зависит от Activity и не содержит UI: снаружи в него отдают MotionEvent, он
 * отдаёт завершённые попытки. Профиль дисплея приходит извне, потому что параметры
 * окна нельзя получить из application context, а зависеть от Activity библиотека
 * не имеет права.
 *
 * ## Потоковая модель
 *
 * [processMotionEvent] вызывается на главном потоке и снимает две receipt-метки,
 * копирует поля MotionEvent в примитивные массивы, кладёт готовый снимок в очередь
 * неблокирующим offer. Сериализация, запись и вызовы [TrialSink] идут на отдельном worker-потоке.
 *
 * Сам объект MotionEvent на отложенную обработку не передаётся: система его
 * переиспользует, и после возврата из обработчика он недействителен.
 *
 * ## Порядок вызовов
 *
 * ```
 * startSession(sessionId, participantId)
 *   updateDisplayProfile(...)          // перед каждой попыткой
 *   startTrial(trialId, TAP, STAGE1_TAP)
 *     processMotionEvent(...) × N
 *   ...
 * endSession()                          // ждёт фиксации всех принятых попыток
 * ```
 *
 * ## Порядок очистки на границе сессии
 *
 * [endSession] переводит сессию в CLOSING, после чего новые события отвергаются и
 * считаются; затем через очередь проходит барьер, и только после него сессия
 * считается закрытой. Идентификатор сессии штампуется в снимок в момент постановки
 * в очередь, на главном потоке, поэтому попытки предыдущей сессии, ещё лежащие в
 * очереди, физически не могут получить идентификатор новой.
 */
public class RawTouchCollector(appContext: Context) {

    private val context: Context = appContext.applicationContext

    private val buffer = SampleBuffer()
    private val displayProfiles = DisplayProfileRegistry()
    private val coords = MotionEvent.PointerCoords()

    private val worker = PersistWorker(
        onCompleted = { json -> listener?.onTrialCompleted(json) },
        onPersisted = { trialId -> listener?.onTrialPersisted(trialId) },
        onError = { code, message -> listener?.onCollectorError(code, message) },
        serialize = { snapshot -> TrialJsonWriter.write(snapshot) },
    )

    @Volatile
    private var listener: TrialListener? = null

    // --- состояние сессии ---
    private var sessionState = SessionState.NONE
    private var sessionId: String = ""
    private var participantId: String = ""
    private var sessionStartedAtWallClockMs: Long = 0
    private var sessionEndedAtWallClockMs: Long? = null
    private var sessionClockSync: ClockSyncPoint? = null

    // --- состояние попытки ---
    private var trialState = TrialState.IDLE
    private var trialId: String = ""
    private var taskGroup: String = TaskGroup.TAP
    private var scenarioType: String = ScenarioType.STAGE1_TAP
    private var trialIndex = 0
    private var trialClockSync: ClockSyncPoint? = null
    private var trialDisplayProfile: DisplayProfile? = null
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var downEventTimeUptimeNs: Long = 0
    private var downCommonTimestampNs: Long = 0
    private var secondPointerObserved = false

    /**
     * Идёт доигрывание жеста, прерванного мультитачем.
     *
     * После MULTITOUCH_ERROR пальцы ещё на экране, и система продолжает слать MOVE,
     * POINTER_UP и UP. Записывать их нельзя, но и считать отклонением тоже:
     * это штатный хвост прерванного жеста, а не события «до начала попытки».
     * Флаг снимается, когда жест дойдёт до конца или начнётся новый.
     */
    private var discardingAbortedGesture = false

    // --- счётчики ---
    private val eventsBeforeStart = AtomicLong()
    private val eventsDiscardedAfterMultitouch = AtomicLong()
    private val eventsAfterEnd = AtomicLong()
    private val eventsAfterSessionClose = AtomicLong()
    private val implicitCancels = AtomicLong()
    private val multitouchErrors = AtomicLong()
    private val staleDisplayProfiles = AtomicLong()
    private val clockSyncFallbacks = AtomicLong()

    init {
        worker.start()
    }

    // ------------------------------------------------------------------
    // Сессия
    // ------------------------------------------------------------------

    /**
     * Начать сессию.
     *
     * @throws IllegalStateException если предыдущая сессия не закрыта. Неявное
     * закрытие здесь было бы удобнее, но скрыло бы от приложения факт потери границы.
     */
    public fun startSession(sessionId: String, participantId: String) {
        // Отвергаем ACTIVE и CLOSING, пока endSession не вернул управление
        check(sessionState == SessionState.NONE) {
            "Сессия " + this.sessionId + " ещё не закрыта (" + sessionState +
                "): дождитесь возврата из endSession()"
        }

        this.sessionId = sessionId
        this.participantId = participantId
        sessionStartedAtWallClockMs = System.currentTimeMillis()
        sessionEndedAtWallClockMs = null
        trialIndex = 0
        trialState = TrialState.IDLE
        activePointerId = MotionEvent.INVALID_POINTER_ID
        buffer.reset()
        displayProfiles.reset()
        sessionClockSync = newClockSync()
        sessionState = SessionState.ACTIVE
    }

    /**
     * Закрыть сессию и дождаться фиксации всех принятых попыток.
     *
     * Нельзя вызывать с главного потока: метод блокируется до подтверждения записи.
     */
    public fun endSession() {
        if (sessionState == SessionState.NONE) return
        requireBackgroundThread("endSession")

        sessionState = SessionState.CLOSING
        if (trialState == TrialState.ACTIVE) {
            finishTrial(TrialStatus.CANCEL, lastEventTimeUptimeNs())
        }

        // Если барьер не сомкнулся за отведённое время, гарантия не выполнена.
        // Молчать об этом нельзя: приложение не должно экспортировать сессию,
        // считая её полностью зафиксированной.
        if (!worker.awaitQuiescence(DEFAULT_QUIESCENCE_TIMEOUT_MS)) {
            val d = getDiagnostics()
            listener?.onCollectorError(
                ErrorCode.QUIESCENCE_TIMEOUT,
                "Не все принятые попытки зафиксированы за " +
                    DEFAULT_QUIESCENCE_TIMEOUT_MS + " мс: осталось " + d.pendingTrials,
            )
        }

        sessionEndedAtWallClockMs = System.currentTimeMillis()
        sessionState = SessionState.NONE
    }

    /** Данные сессии для слоя хранения. */
    public fun currentSessionInfo(): SessionInfo? {
        if (sessionId.isEmpty()) return null
        return SessionInfo(
            sessionId = sessionId,
            participantId = participantId,
            startedAtWallClockMs = sessionStartedAtWallClockMs,
            endedAtWallClockMs = sessionEndedAtWallClockMs,
            phoneSupportMode = PhoneSupportMode.HAND,
            schemaVersion = Schema.VERSION,
            device = deviceInfo(),
        )
    }

    /** Точка синхронизации, снятая при старте сессии. */
    public fun sessionClockSync(): ClockSyncPoint? = sessionClockSync

    // ------------------------------------------------------------------
    // Попытка
    // ------------------------------------------------------------------

    /**
     * Начать попытку. Снимает свежую точку clock_sync и фиксирует профиль дисплея,
     * действующий непосредственно перед попыткой.
     */
    public fun startTrial(trialId: String, taskGroup: String, scenarioType: String) {
        check(sessionState == SessionState.ACTIVE) {
            "Нет активной сессии: вызовите startSession()"
        }

        if (trialState == TrialState.ACTIVE) {
            implicitCancels.incrementAndGet()
            listener?.onCollectorError(
                ErrorCode.IMPLICIT_CANCEL,
                "startTrial поверх незакрытой попытки " + this.trialId + ": закрыта как CANCEL",
            )
            finishTrial(TrialStatus.CANCEL, lastEventTimeUptimeNs())
        }

        if (!displayProfiles.updatedSinceLastTrial) {
            staleDisplayProfiles.incrementAndGet()
            listener?.onCollectorError(
                ErrorCode.STALE_DISPLAY_PROFILE,
                "Профиль дисплея не обновлялся перед попыткой " + trialId,
            )
        }

        this.trialId = trialId
        this.taskGroup = taskGroup
        this.scenarioType = scenarioType
        trialClockSync = newClockSync()
        trialDisplayProfile = displayProfiles.profileForTrial()
        displayProfiles.markConsumed()
        secondPointerObserved = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        buffer.reset()
        trialState = TrialState.ARMED
    }

    /**
     * Принять MotionEvent. Вызывается с главного потока из onTouchEvent.
     *
     * Обе метки времени получения снимаются первыми, до чтения любых полей и до
     * какой-либо обработки.
     */
    public fun processMotionEvent(event: MotionEvent) {
        val receiptUptimeNs = TimeSource.receiptUptimeNs()
        val receiptElapsedNs = SystemClock.elapsedRealtimeNanos()

        if (sessionState != SessionState.ACTIVE) {
            eventsAfterSessionClose.incrementAndGet()
            return
        }

        when (trialState) {
            TrialState.IDLE, TrialState.TERMINATED -> {
                countDroppedEvent(event)
                return
            }

            TrialState.ARMED -> {
                if (event.actionMasked != MotionEvent.ACTION_DOWN) {
                    countDroppedEvent(event)
                    return
                }
                beginContact(event)
            }

            TrialState.ACTIVE -> Unit
        }

        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) {
            countDroppedEvent(event)
            return
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            -> record(event, pointerIndex, receiptUptimeNs, receiptElapsedNs)

            MotionEvent.ACTION_UP -> {
                record(event, pointerIndex, receiptUptimeNs, receiptElapsedNs)
                finishTrial(TrialStatus.UP, TimeSource.eventTimeNs(event))
            }

            MotionEvent.ACTION_CANCEL -> {
                record(event, pointerIndex, receiptUptimeNs, receiptElapsedNs)
                finishTrial(TrialStatus.CANCEL, TimeSource.eventTimeNs(event))
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // сначала сохранить сам POINTER_DOWN как последний отсчёт
                // прерванной попытки, и только потом закрыть её.
                secondPointerObserved = true
                record(event, pointerIndex, receiptUptimeNs, receiptElapsedNs)
                multitouchErrors.incrementAndGet()
                finishTrial(TrialStatus.MULTITOUCH_ERROR, TimeSource.eventTimeNs(event))
                // Пальцы ещё на экране: хвост жеста досчитывается отдельно.
                discardingAbortedGesture = true
            }

            else -> countDroppedEvent(event)
        }
    }

    /**
     * Учесть отброшенное событие в подходящий счётчик.
     *
     * Хвост жеста, прерванного мультитачем, считается отдельно: это штатное
     * поведение, а не отклонение, и смешивать его с «событиями до начала
     * попытки» нельзя — иначе чистый прогон выглядит грязным.
     */
    private fun countDroppedEvent(event: MotionEvent) {
        if (discardingAbortedGesture) {
            eventsDiscardedAfterMultitouch.incrementAndGet()
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                discardingAbortedGesture = false
            }
            return
        }
        if (trialState == TrialState.TERMINATED) {
            eventsAfterEnd.incrementAndGet()
        } else {
            eventsBeforeStart.incrementAndGet()
        }
    }

    // ------------------------------------------------------------------
    // Окружение
    // ------------------------------------------------------------------

    /**
     * Сообщить текущие параметры окна и дисплея.
     *
     * Вызывать перед каждой попыткой: параметры меняются посреди сессии сами —
     * адаптивная частота обновления, поворот, многооконный режим, складные устройства.
     * Вызов идемпотентный: при совпадении всех значений возвращается тот же
     * display_profile_id и новая запись не создаётся.
     */
    @Suppress("LongParameterList")
    public fun updateDisplayProfile(
        windowWidthPx: Int,
        windowHeightPx: Int,
        modeWidthPx: Int,
        modeHeightPx: Int,
        refreshRateHz: Float,
        densityDpi: Int,
    ) {
        displayProfiles.update(
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx,
            displayModeWidthPx = modeWidthPx,
            displayModeHeightPx = modeHeightPx,
            displayRefreshRateHz = refreshRateHz,
            densityDpi = densityDpi,
        )
    }

    /**
     * Снять новую точку синхронизации.
     *
     * Вызывать из Activity.onResume: после возобновления приложения старое смещение
     * применять к новым событиям нельзя. Каждый startTrial и так снимает свежую точку,
     * поэтому этот вызов обновляет сессионную.
     */
    public fun invalidateClockSync() {
        if (sessionState == SessionState.ACTIVE) {
            sessionClockSync = newClockSync()
        }
    }

    // ------------------------------------------------------------------
    // Выдача наружу
    // ------------------------------------------------------------------

    public fun setTrialListener(listener: TrialListener?) {
        this.listener = listener
    }

    /** Подставить слой хранения. Блок 3 передаёт сюда реализацию на Room. */
    public fun setTrialSink(sink: TrialSink?) {
        worker.sink = sink
    }

    public fun getLastTrialJson(): String? = worker.lastTrialJson

    public fun getSchemaVersion(): String = Schema.VERSION

    public fun getDiagnostics(): Diagnostics = Diagnostics(
        acceptedTrials = worker.acceptedTrials.get(),
        confirmedTrials = worker.confirmedTrials.get(),
        queueOverflows = worker.queueOverflows.get(),
        writeFailures = worker.writeFailures.get(),
        eventsBeforeStart = eventsBeforeStart.get(),
        eventsDiscardedAfterMultitouch = eventsDiscardedAfterMultitouch.get(),
        eventsAfterEnd = eventsAfterEnd.get(),
        eventsAfterSessionClose = eventsAfterSessionClose.get(),
        implicitCancels = implicitCancels.get(),
        multitouchErrors = multitouchErrors.get(),
        trialsWithStaleDisplayProfile = staleDisplayProfiles.get(),
        clockSyncFallbacks = clockSyncFallbacks.get(),
    )

    /**
     * Дождаться фиксации всего принятого.
     *
     * Возвращает true, только если worker дошёл до барьера. Это сильнее проверки
     * «очередь пуста»: обработчик мог забрать последний элемент и ещё не зафиксировать
     * его. Нельзя вызывать с главного потока.
     */
    public fun awaitQuiescence(timeoutMs: Long): Boolean {
        requireBackgroundThread("awaitQuiescence")
        return worker.awaitQuiescence(timeoutMs)
    }

    // ------------------------------------------------------------------
    // Управление
    // ------------------------------------------------------------------

    /** Сбросить состояние попытки и сессии. Уже принятые попытки не отменяются. */
    public fun reset() {
        trialState = TrialState.IDLE
        activePointerId = MotionEvent.INVALID_POINTER_ID
        secondPointerObserved = false
        discardingAbortedGesture = false
        buffer.reset()
        sessionState = SessionState.NONE
        sessionId = ""
        participantId = ""
        sessionClockSync = null
        trialClockSync = null
        trialDisplayProfile = null
        displayProfiles.reset()
    }

    /** Очистить оперативный буфер незавершённой попытки. Завершённые не трогает. */
    public fun clearBuffer() {
        buffer.reset()
    }

    /** Остановить фоновый поток. После этого коллектор не используется. */
    public fun shutdown() {
        worker.stop()
    }

    // ------------------------------------------------------------------
    // Внутреннее
    // ------------------------------------------------------------------

    private fun beginContact(event: MotionEvent) {
        discardingAbortedGesture = false
        // Номер присваивается здесь, а не в startTrial: попытка существует как
        // данные только с момента ACTION_DOWN. Хост вправе арминовать впрок —
        // например, после мультитача он делает это дважды, — и такие холостые
        // армирования не должны оставлять дыр в нумерации записанных попыток.
        trialIndex++
        activePointerId = event.getPointerId(0)
        downEventTimeUptimeNs = TimeSource.eventTimeNs(event)
        downCommonTimestampNs = commonTimestampNs(downEventTimeUptimeNs)
        trialState = TrialState.ACTIVE
    }

    /**
     * Копирование значений в собственные структуры прямо во время обработки события.
     *
     * Historical samples пишутся раньше текущего отсчёта: они произошли раньше, и
     * порядок внутри попытки должен быть хронологическим. Для них сохраняются те же
     * receipt-метки, что у родительского события, — они относятся к моменту доставки
     * всего пакета приложению.
     */
    private fun record(
        event: MotionEvent,
        pointerIndex: Int,
        receiptUptimeNs: Long,
        receiptElapsedNs: Long,
    ) {
        val action = event.actionMasked
        val pointerId = event.getPointerId(pointerIndex)
        val actionIndex = event.actionIndex
        val pointerCount = event.pointerCount
        val toolType = event.getToolType(pointerIndex)

        val historySize = event.historySize
        for (h in 0 until historySize) {
            event.getHistoricalPointerCoords(pointerIndex, h, coords)
            val eventTimeNs = TimeSource.historicalEventTimeNs(event, h)
            buffer.append(
                eventAction = action,
                pointerId = pointerId,
                pointerIndex = pointerIndex,
                actionIndex = actionIndex,
                pointerCount = pointerCount,
                toolType = toolType,
                historyIndex = h,
                eventTimeUptimeMs = TimeSource.historicalEventTimeMs(event, h),
                eventTimeUptimeNs = eventTimeNs,
                commonTimestampNs = commonTimestampNs(eventTimeNs),
                appReceiptTimeUptimeNs = receiptUptimeNs,
                appReceiptTimeElapsedNs = receiptElapsedNs,
                x = coords.x,
                y = coords.y,
                touchMajor = coords.touchMajor,
                touchMinor = coords.touchMinor,
                size = coords.size,
                pressure = coords.pressure,
                orientation = coords.orientation,
            )
        }

        event.getPointerCoords(pointerIndex, coords)
        val eventTimeNs = TimeSource.eventTimeNs(event)
        buffer.append(
            eventAction = action,
            pointerId = pointerId,
            pointerIndex = pointerIndex,
            actionIndex = actionIndex,
            pointerCount = pointerCount,
            toolType = toolType,
            historyIndex = -1,
            eventTimeUptimeMs = TimeSource.eventTimeMs(event),
            eventTimeUptimeNs = eventTimeNs,
            commonTimestampNs = commonTimestampNs(eventTimeNs),
            appReceiptTimeUptimeNs = receiptUptimeNs,
            appReceiptTimeElapsedNs = receiptElapsedNs,
            x = coords.x,
            y = coords.y,
            touchMajor = coords.touchMajor,
            touchMinor = coords.touchMinor,
            size = coords.size,
            pressure = coords.pressure,
            orientation = coords.orientation,
        )
    }

    /**
     * Закрыть попытку: собрать неизменяемый снимок и отдать его в фоновую очередь.
     *
     * Снимок собирается здесь, на главном потоке, и получает собственные копии
     * массивов. Буфер сразу свободен для следующей попытки, а снимок ни на что
     * переиспользуемое не ссылается.
     */
    private fun finishTrial(status: String, terminalEventTimeUptimeNs: Long) {
        val clockSync = trialClockSync ?: return
        val profile = trialDisplayProfile ?: return

        val snapshot = TrialSnapshot(
            trialId = trialId,
            sessionId = sessionId,
            participantId = participantId,
            trialIndex = trialIndex,
            taskGroup = taskGroup,
            scenarioType = scenarioType,
            schemaVersion = Schema.VERSION,
            displayProfile = profile,
            clockSync = clockSync,
            touchDownCommonTimestampNs = downCommonTimestampNs,
            touchUpCommonTimestampNs = commonTimestampNs(terminalEventTimeUptimeNs),
            contactDurationNs = terminalEventTimeUptimeNs - downEventTimeUptimeNs,
            completionStatus = status,
            currentSampleCount = buffer.currentSampleCount,
            historicalSampleCount = buffer.historicalSampleCount,
            secondPointerObserved = secondPointerObserved,
            timestampPrecision = TimeSource.eventTimePrecision,
            appReceiptUptimePrecision = TimeSource.receiptUptimePrecision,
            samples = buffer.toSamples(downEventTimeUptimeNs),
        )

        trialState = TrialState.TERMINATED
        activePointerId = MotionEvent.INVALID_POINTER_ID
        buffer.reset()

        worker.submit(snapshot)
    }

    private fun commonTimestampNs(uptimeNs: Long): Long =
        trialClockSync?.toCommonTimestampNs(uptimeNs)
            ?: sessionClockSync?.toCommonTimestampNs(uptimeNs)
            ?: uptimeNs

    private fun lastEventTimeUptimeNs(): Long =
        if (buffer.count > 0) buffer.eventTimeUptimeNsAt(buffer.count - 1) else downEventTimeUptimeNs

    private fun newClockSync(): ClockSyncPoint {
        val point = ClockSyncCapture.capture(UUID.randomUUID().toString(), sessionId)
        if (point.syncMethod == SyncMethod.MS_PLAIN) {
            clockSyncFallbacks.incrementAndGet()
            listener?.onCollectorError(
                ErrorCode.CLOCK_SYNC_FALLBACK,
                "Границу миллисекунды поймать не удалось, точность синхронизации — миллисекундная",
            )
        }
        return point
    }

    private fun deviceInfo(): DeviceInfo = DeviceInfo(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidVersion = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        appVersion = appVersionName(),
        aarVersion = BuildConfig.AAR_VERSION,
        densityDpi = context.resources.configuration.densityDpi,
    )

    private fun appVersionName(): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "unknown"
    } catch (_: RuntimeException) {
        "unknown"
    }

    private fun requireBackgroundThread(method: String) {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            method + " блокируется до подтверждения записи и не может вызываться " +
                "с главного потока"
        }
    }

    private companion object {
        const val DEFAULT_QUIESCENCE_TIMEOUT_MS = 5_000L
    }
}
