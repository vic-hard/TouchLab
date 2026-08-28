package com.lime.touchlab

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lime.rawtouchcollector.RawTouchCollector
import com.lime.rawtouchcollector.ScenarioType
import com.lime.rawtouchcollector.TaskGroup
import com.lime.rawtouchcollector.TrialListener
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Минимальный хост для проверки коллектора.
 *
 * Это НЕ интерфейс раздела 12: здесь ровно столько, сколько нужно, чтобы отдать
 * коллектору настоящий поток MotionEvent с реального устройства и увидеть счётчики.
 * Полноценный UI, локальное хранение и экспорт ZIP — блоки 2 и 3.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var collector: RawTouchCollector
    private lateinit var tapArea: TapAreaView
    private lateinit var participantInput: EditText
    private lateinit var statusView: TextView
    private lateinit var summaryView: TextView
    private lateinit var startButton: Button
    private lateinit var finishButton: Button

    private val main = Handler(Looper.getMainLooper())
    private val trialCounter = AtomicInteger(0)
    private val confirmedCounter = AtomicInteger(0)

    private var sessionActive = false

    /** Сколько попыток пришло по каждому session_id. */
    private val trialsPerSession = LinkedHashMap<String, Int>()

    @Volatile
    private var lastTrialJson: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tapArea = findViewById(R.id.tap_area)
        participantInput = findViewById(R.id.participant_id)
        statusView = findViewById(R.id.status)
        summaryView = findViewById(R.id.summary)
        startButton = findViewById(R.id.start_session)
        finishButton = findViewById(R.id.finish_session)

        collector = RawTouchCollector(applicationContext)
        collector.setTrialListener(object : TrialListener {
            override fun onTrialCompleted(trialJson: String) {
                lastTrialJson = trialJson
                // Критерий 28: попытка обязана нести идентификатор той сессии, в
                // которой была записана, даже если к моменту сохранения уже началась
                // следующая. Считаем попытки по session_id, чтобы это было видно.
                val sessionId = jsonString(trialJson, "session_id")
                synchronized(trialsPerSession) {
                    trialsPerSession[sessionId] = (trialsPerSession[sessionId] ?: 0) + 1
                }
                dumpTrial(trialJson)
                main.post { summaryView.text = extractSummary(trialJson) }
            }

            override fun onTrialPersisted(trialId: String) {
                confirmedCounter.incrementAndGet()
                main.post { renderStatus() }
            }

            override fun onCollectorError(code: Int, message: String) {
                Log.w(TAG, "collector error $code: $message")
                main.post { renderStatus() }
            }
        })

        tapArea.collector = collector
        tapArea.onTrialTerminated = { armNextTrial() }

        startButton.setOnClickListener { startSession() }
        finishButton.setOnClickListener { finishSession() }
        finishButton.isEnabled = false

        renderStatus()
    }

    override fun onResume() {
        super.onResume()
        // после возобновления приложения старое смещение к новым событиям применять нельзя.
        collector.invalidateClockSync()
        pushDisplayProfile()
    }

    override fun onDestroy() {
        collector.shutdown()
        super.onDestroy()
    }

    private fun startSession() {
        if (sessionActive) return
        val participantId = participantInput.text.toString().ifBlank { "test-participant" }
        val sessionId = "s-" + UUID.randomUUID().toString().take(8)

        trialCounter.set(0)
        confirmedCounter.set(0)

        collector.startSession(sessionId, participantId)
        sessionActive = true
        tapArea.acceptingTouches = true
        tapArea.invalidate()
        startButton.isEnabled = false
        finishButton.isEnabled = true
        armNextTrial()
        renderStatus()
    }

    private fun finishSession() {
        if (!sessionActive) return
        sessionActive = false
        tapArea.acceptingTouches = false
        tapArea.invalidate()
        statusView.text = getString(R.string.status_closing)

        // Обе кнопки заблокированы, пока идёт закрытие: до возврата из endSession
        // барьер предыдущей сессии не сомкнулся, и начинать новую нельзя.
        startButton.isEnabled = false
        finishButton.isEnabled = false

        // endSession блокируется до подтверждения фиксации всех принятых попыток,
        // поэтому только с фонового потока.
        Thread({
            collector.endSession()
            lastTrialJson?.let { dumpLastTrial(it) }
            main.post {
                startButton.isEnabled = true
                renderStatus()
            }
        }, "session-finish").start()
    }

    /**
     * Подготовить следующую попытку.
     *
     * Профиль дисплея и точка clock_sync фиксируются непосредственно перед попыткой,
     * поэтому порядок именно такой: сначала профиль, затем startTrial.
     * Вызывается синхронно после терминального события, чтобы коллектор был готов
     * раньше, чем палец коснётся экрана снова.
     */
    private fun armNextTrial() {
        if (!sessionActive) return
        pushDisplayProfile()
        collector.startTrial(
            "t-" + trialCounter.incrementAndGet(),
            TaskGroup.TAP,
            ScenarioType.STAGE1_TAP,
        )
        renderStatus()
    }

    /**
     * Push-модель профиля дисплея: параметры окна доступны только визуальному
     * контексту, а библиотека не имеет права зависеть от Activity, §3.1.
     */
    private fun pushDisplayProfile() {
        val bounds = windowManager.currentWindowMetrics.bounds
        val mode = display?.mode
        collector.updateDisplayProfile(
            windowWidthPx = bounds.width(),
            windowHeightPx = bounds.height(),
            modeWidthPx = mode?.physicalWidth ?: bounds.width(),
            modeHeightPx = mode?.physicalHeight ?: bounds.height(),
            refreshRateHz = mode?.refreshRate ?: 0f,
            densityDpi = resources.configuration.densityDpi,
        )
    }

    private fun renderStatus() {
        val d = collector.getDiagnostics()
        statusView.text = getString(
            R.string.status_format,
            if (sessionActive) getString(R.string.session_active)
            else getString(R.string.session_idle),
            confirmedCounter.get(),
            d.acceptedTrials,
            d.queueOverflows + d.writeFailures,
        )
    }

    /**
     * Техническая сводка последней попытки. Читается из готового JSON, чтобы не
     * заводить второй путь доступа к тем же данным.
     */
    private fun extractSummary(json: String): String = getString(
        R.string.summary_format,
        jsonString(json, "completion_status"),
        jsonNumber(json, "contact_duration_ns") / 1_000_000.0,
        jsonNumber(json, "current_sample_count"),
        jsonNumber(json, "historical_sample_count"),
        jsonString(json, "timestamp_precision"),
        jsonString(json, "sync_method"),
        jsonNumber(json, "sync_sampling_uncertainty_ns"),
        jsonNumber(json, "sync_quantization_uncertainty_ns"),
    )

    private fun jsonString(json: String, field: String): String {
        val key = "\"" + field + "\":\""
        val start = json.indexOf(key)
        if (start < 0) return "-"
        val from = start + key.length
        val end = json.indexOf('"', from)
        return if (end < 0) "-" else json.substring(from, end)
    }

    private fun jsonNumber(json: String, field: String): Long {
        val key = "\"" + field + "\":"
        val start = json.indexOf(key)
        if (start < 0) return 0
        var i = start + key.length
        val sb = StringBuilder()
        while (i < json.length && (json[i].isDigit() || json[i] == '-')) {
            sb.append(json[i])
            i++
        }
        return sb.toString().toLongOrNull() ?: 0
    }

    /**
     * Пишет каждую завершённую попытку в файл сразу, а не только в конце сессии.
     * Нужно для ручных проверок: результат должен быть виден до закрытия сессии.
     */
    private fun dumpTrial(json: String) {
        val dir = getExternalFilesDir(null) ?: filesDir
        val trialId = jsonString(json, "trial_id")
        val status = jsonString(json, "completion_status")
        File(dir, "last-trial.json").writeText(json)
        if (status != "UP") File(dir, "trial-" + status + ".json").writeText(json)
        Log.i(
            TAG,
            "попытка " + trialId + " -> " + status +
                " · отсчётов " + jsonNumber(json, "current_sample_count") +
                " + history " + jsonNumber(json, "historical_sample_count") +
                " · второй палец " + (json.contains("\"second_pointer_observed\":true")),
        )
    }

    private fun dumpLastTrial(json: String) {
        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, "last-trial.json")
        file.writeText(json)
        Log.i(TAG, "последняя попытка записана в " + file.absolutePath)
        Log.i(TAG, "диагностика: " + collector.getDiagnostics())
        synchronized(trialsPerSession) {
            Log.i(TAG, "попыток по сессиям: " + trialsPerSession)
        }
    }

    private companion object {
        const val TAG = "TouchLab"
    }
}
