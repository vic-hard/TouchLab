package com.lime.touchlab.session

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import com.lime.rawtouchcollector.Diagnostics
import com.lime.rawtouchcollector.RawTouchCollector
import com.lime.rawtouchcollector.ScenarioType
import com.lime.rawtouchcollector.TaskGroup
import com.lime.rawtouchcollector.TrialListener
import com.lime.rawtouchcollector.TrialSink
import com.lime.rawtouchcollector.TrialSnapshot
import com.lime.touchlab.export.SessionExporter
import com.lime.touchlab.storage.SessionCounters
import com.lime.touchlab.storage.TrialRepository
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Управление сессией: автомат состояний, счётчики, техсводка, экспорт.
 *
 * Живёт дольше Activity — им владеет [SessionViewModel].
 *
 * ## Потоки
 *
 * Публичные методы вызываются с главного потока, кроме [persist] — его зовёт worker
 * коллектора. Блокирующие операции (`endSession`, запись в Room, сборка архива) уходят
 * на отдельные потоки; результат возвращается на главный через [main].
 */
class SessionController(
    private val collector: RawTouchCollector,
    private val repository: TrialRepository,
    private val exporter: SessionExporter,
) : TrialSink, TrialListener {

    private val main = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<SessionStateListener>()

    @Volatile
    private var displaySource: DisplaySource? = null

    @Volatile
    private var state: SessionState = SessionState.IDLE

    @Volatile
    private var sessionId: String? = null

    @Volatile
    private var participantId: String = ""

    @Volatile
    private var summary: TrialSummary? = null

    @Volatile
    private var error: SessionError? = null

    @Volatile
    private var exportState: ExportState = ExportState.NONE

    @Volatile
    private var exportFile: File? = null

    @Volatile
    private var exportMessage: String? = null

    @Volatile
    private var exportableSessionId: String? = null

    /** Палец на экране: пока true, перевооружать попытку нельзя. */
    @Volatile
    private var inContact: Boolean = false

    private val recoveredSessions = AtomicInteger(0)
    private val errorOccurrences = AtomicInteger(0)

    /**
     * Снимок счётчиков коллектора на момент старта сессии.
     *
     * `Diagnostics` копит за всё время жизни коллектора, а коллектор переживает и экран,
     * и предыдущие сессии. Всё, что показывается оператору и уезжает в архив, — разность
     * с этим снимком, то есть «за эту сессию».
     */
    @Volatile
    private var baseline: Diagnostics = collector.getDiagnostics()

    init {
        collector.setTrialListener(this)
        collector.setTrialSink(this)
        // Разбор последствий аварийного рестарта — до того, как оператор сможет начать
        // новую сессию. Кнопка старта до этого момента и так неактивна: состояние
        // отдаётся в UI только после первого notifyListeners.
        repository.execute {
            val recovered = repository.markCrashedSessions()
            if (recovered > 0) {
                recoveredSessions.set(recovered)
                Log.w(TAG, "помечено незавершённых сессий после аварийного рестарта: $recovered")
            }
            exportableSessionId = repository.lastCompletedSessionId()
            publish()
        }
    }

    // ------------------------------------------------------------------
    // Подписка
    // ------------------------------------------------------------------

    fun addListener(listener: SessionStateListener) {
        listeners.add(listener)
        listener.onStateChanged(snapshot())
    }

    fun removeListener(listener: SessionStateListener) {
        listeners.remove(listener)
    }

    /** Источник параметров дисплея. Снимается в onDestroy, чтобы не держать Activity. */
    fun bindDisplaySource(source: DisplaySource?) {
        displaySource = source
    }

    // ------------------------------------------------------------------
    // Сессия
    // ------------------------------------------------------------------

    /**
     * Начать сессию.
     */
    fun startSession(participantId: String): Boolean {
        if (state != SessionState.IDLE && state != SessionState.CLOSED) return false
        if (participantId.isBlank()) return false

        val newSessionId = UUID.randomUUID().toString()
        this.participantId = participantId
        this.sessionId = newSessionId
        summary = null
        error = null
        errorOccurrences.set(0)
        exportState = ExportState.NONE
        exportFile = null
        exportMessage = null

        baseline = collector.getDiagnostics()

        collector.startSession(newSessionId, participantId)

        val info = collector.currentSessionInfo()
        if (info != null) {
            repository.beginSession(info, collector.sessionClockSync())
        }

        state = SessionState.ACTIVE
        armNextTrial()
        publish()
        return true
    }

    /**
     * Завершить сессию.
     *
     * `endSession` блокируется до подтверждения фиксации всех принятых попыток, поэтому
     * выполняется на отдельном потоке. Пока он не вернулся, состояние — CLOSING, и ни
     * начать новую сессию, ни экспортировать нельзя: барьер ещё не сомкнут.
     */
    fun finishSession() {
        if (state != SessionState.ACTIVE) return
        state = SessionState.CLOSING
        publish()

        val closingSessionId = sessionId
        Thread({
            collector.endSession()

            val info = collector.currentSessionInfo()
            val endedAt = info?.endedAtWallClockMs ?: System.currentTimeMillis()
            // Счётчики снимаются здесь, а не раньше: endSession уже вернулся, барьер
            // сомкнут, и все принятые попытки прошли через persist(). Снимок до барьера
            // занизил бы trials_confirmed.
            val counters = SessionCounters.between(baseline, collector.getDiagnostics())
            if (closingSessionId != null) {
                val closed = repository.closeSession(closingSessionId, endedAt, counters)
                if (!closed) {
                    raiseError(
                        ERROR_SESSION_NOT_CLOSED,
                        "Строку сессии не удалось закрыть: экспорт будет неполным",
                    )
                }
                exportableSessionId = closingSessionId
            }

            state = SessionState.CLOSED
            publish()
        }, "session-finish").start()
    }

    // ------------------------------------------------------------------
    // Попытки
    // ------------------------------------------------------------------

    /**
     * Терминальное событие попытки. Вызывается синхронно из onTouchEvent, чтобы
     * следующая попытка была вооружена раньше, чем палец коснётся экрана снова.
     */
    fun onTrialTerminated() {
        armNextTrial()
    }

    fun onContactChanged(inContact: Boolean) {
        this.inContact = inContact
    }

    /**
     * Передать событие коллектору. Вызывается с главного потока из onTouchEvent.
     *
     * Область касания не знает про библиотеку и отдаёт события сюда: коллектор живёт
     * дольше экрана, и держать ссылку на него во View значило бы держать её и после
     * пересоздания Activity.
     */
    fun processTouch(event: MotionEvent) {
        collector.processMotionEvent(event)
    }

    /**
     * Возобновление приложения.
     *
     * Мало снять новую сессионную точку синхронизации: попытка, вооружённая до
     * сворачивания, держит точку, снятую до паузы, и первое же касание после возврата
     * посчитало бы `common_timestamp_ns` по устаревшему смещению. Это запрещено,
     * поэтому попытка перевооружается заново — с новым trial_id и свежей точкой.
     *
     * Перевооружение из состояния ARMED неявной отмены не вызывает, а `trial_index`
     * присваивается только на ACTION_DOWN, поэтому дыр в нумерации не появляется.
     */
    fun onActivityResumed() {
        if (state != SessionState.ACTIVE) return
        collector.invalidateClockSync()
        if (!inContact) armNextTrial()
        publish()
    }

    fun pushDisplayProfile() {
        val metrics = displaySource?.read() ?: return
        collector.updateDisplayProfile(
            windowWidthPx = metrics.windowWidthPx,
            windowHeightPx = metrics.windowHeightPx,
            modeWidthPx = metrics.modeWidthPx,
            modeHeightPx = metrics.modeHeightPx,
            refreshRateHz = metrics.refreshRateHz,
            densityDpi = metrics.densityDpi,
        )
    }

    private fun armNextTrial() {
        if (state != SessionState.ACTIVE) return
        // Порядок обязателен: профиль дисплея фиксируется непосредственно перед
        // попыткой, иначе она свяжется с профилем предыдущей.
        pushDisplayProfile()
        collector.startTrial(
            UUID.randomUUID().toString(),
            TaskGroup.TAP,
            ScenarioType.STAGE1_TAP,
        )
    }

    // ------------------------------------------------------------------
    // Приёмник и обратная связь коллектора
    // ------------------------------------------------------------------

    /**
     * Фиксация попытки. Вызывается на worker-потоке коллектора.
     *
     * Сводка публикуется независимо от исхода записи, но счётчик подтверждённых растёт
     * только при `true` — его ведёт сама библиотека по возвращённому отсюда значению.
     */
    override fun persist(trial: TrialSnapshot): Boolean {
        summary = TrialSummary.from(trial)
        val ok = repository.persist(trial)
        publish()
        return ok
    }

    override fun onTrialCompleted(trialJson: String) {
        // Дамп попытки в файл — диагностика, а не хранение; включается отдельно.
        exporter.dumpTrialIfEnabled(trialJson)
    }

    override fun onTrialPersisted(trialId: String) {
        publish()
    }

    override fun onCollectorError(code: Int, message: String) {
        Log.w(TAG, "коллектор: $code $message")
        raiseError(code, message)
    }

    private fun raiseError(code: Int, message: String) {
        error = SessionError(code, message, errorOccurrences.incrementAndGet())
        publish()
    }

    // ------------------------------------------------------------------
    // Экспорт
    // ------------------------------------------------------------------

    /**
     * Собрать архив зафиксированной сессии.
     *
     * Только вне ACTIVE и CLOSING: требуется экспортировать по зафиксированному
     * снимку завершённой сессии, а не по той, в которую ещё могут прийти касания.
     */
    fun exportSession() {
        val target = exportableSessionId ?: return
        if (state == SessionState.ACTIVE || state == SessionState.CLOSING) return
        if (exportState == ExportState.RUNNING) return

        exportState = ExportState.RUNNING
        exportMessage = null
        publish()

        repository.execute {
            try {
                val file = exporter.export(target)
                exportFile = file
                exportState = ExportState.READY
                exportMessage = file.name
            } catch (e: Exception) {
                Log.e(TAG, "экспорт сессии $target не удался", e)
                exportState = ExportState.FAILED
                exportMessage = e.message ?: e.javaClass.simpleName
            }
            publish()
        }
    }

    fun consumeExportFile(): File? {
        val file = exportFile
        exportFile = null
        if (exportState == ExportState.READY) {
            exportState = ExportState.NONE
            publish()
        }
        return file
    }

    // ------------------------------------------------------------------
    // Завершение работы
    // ------------------------------------------------------------------

    /**
     * Остановить коллектор. Вызывается, когда Activity завершена окончательно.
     *
     * Незакрытая сессия здесь не закрывается: `endSession` блокирующий, а её строка
     * останется `ACTIVE` и будет честно помечена INCOMPLETE на следующем старте.
     */
    fun shutdown() {
        Thread({ collector.shutdown() }, "collector-shutdown").start()
    }

    // ------------------------------------------------------------------
    // Публикация состояния
    // ------------------------------------------------------------------

    private fun snapshot(): SessionUiState {
        val c = SessionCounters.between(baseline, collector.getDiagnostics())
        val accepted = c.trialsAccepted
        val confirmed = c.trialsConfirmed
        val overflows = c.queueOverflows
        val failures = c.writeFailures
        return SessionUiState(
            state = state,
            participantId = participantId,
            sessionId = sessionId,
            // Завершённых — принятые плюс те, что не влезли в очередь: попытка
            // состоялась в обоих случаях, разница только в том, сохранена ли она.
            completedTrials = accepted + overflows,
            confirmedTrials = confirmed,
            queueOverflows = overflows,
            writeFailures = failures,
            summary = summary,
            error = error,
            recoveredIncompleteSessions = recoveredSessions.get(),
            exportState = exportState,
            exportFile = exportFile,
            exportMessage = exportMessage,
            exportableSessionId = exportableSessionId,
        )
    }

    private fun publish() {
        val snapshot = snapshot()
        main.post {
            for (listener in ArrayList(listeners)) listener.onStateChanged(snapshot)
        }
    }

    companion object {
        private const val TAG: String = "TouchLabSession"

        /** Код ошибки приложения, не библиотеки: строку сессии не удалось закрыть. */
        const val ERROR_SESSION_NOT_CLOSED: Int = 101
    }
}
