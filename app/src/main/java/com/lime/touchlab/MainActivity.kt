package com.lime.touchlab

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.lime.touchlab.session.DisplayMetrics
import com.lime.touchlab.session.ExportState
import com.lime.touchlab.session.SessionState
import com.lime.touchlab.session.SessionStateListener
import com.lime.touchlab.session.SessionUiState
import com.lime.touchlab.session.SessionViewModel
import com.lime.touchlab.session.TrialSummary

class MainActivity : AppCompatActivity() {

    private val viewModel: SessionViewModel by viewModels()

    private lateinit var tapArea: TapAreaView
    private lateinit var participantInput: EditText
    private lateinit var counters: TextView
    private lateinit var summaryView: TextView
    private lateinit var errorView: TextView
    private lateinit var noticeView: TextView
    private lateinit var startButton: Button
    private lateinit var finishButton: Button
    private lateinit var exportButton: Button
    private lateinit var dumpToggle: CheckBox

    private val stateListener = SessionStateListener { render(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        tapArea = findViewById(R.id.tap_area)
        participantInput = findViewById(R.id.participant_id)
        counters = findViewById(R.id.counters)
        summaryView = findViewById(R.id.summary)
        errorView = findViewById(R.id.error)
        noticeView = findViewById(R.id.notice)
        startButton = findViewById(R.id.start_session)
        finishButton = findViewById(R.id.finish_session)
        exportButton = findViewById(R.id.export_session)
        dumpToggle = findViewById(R.id.dump_toggle)

        applyInsets()

        val controller = viewModel.controller
        controller.bindDisplaySource { readDisplayMetrics() }

        tapArea.onEvent = { event -> controller.processTouch(event) }
        tapArea.onTrialTerminated = { controller.onTrialTerminated() }
        tapArea.onContactChanged = { inContact -> controller.onContactChanged(inContact) }

        startButton.setOnClickListener {
            val participantId = participantInput.text.toString().trim()
            if (participantId.isEmpty()) {
                participantInput.error = getString(R.string.participant_required)
            } else {
                controller.startSession(participantId)
            }
        }
        finishButton.setOnClickListener { controller.finishSession() }
        exportButton.setOnClickListener { controller.exportSession() }

        dumpToggle.isChecked = viewModel.exporter.diagnosticDumpEnabled
        dumpToggle.setOnCheckedChangeListener { _, checked ->
            viewModel.exporter.diagnosticDumpEnabled = checked
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.controller.addListener(stateListener)
    }

    override fun onStop() {
        viewModel.controller.removeListener(stateListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Новая точка синхронизации и перевооружение попытки: старое смещение к
        // событиям после возобновления применять нельзя.
        viewModel.controller.onActivityResumed()
    }

    override fun onDestroy() {
        viewModel.controller.bindDisplaySource(null)
        super.onDestroy()
    }

    /**
     * Безопасный отступ области касания.
     */
    private fun applyInsets() {
        val root = findViewById<View>(R.id.main)
        val base = resources.getDimensionPixelSize(R.dimen.tap_area_min_margin)
        val padding = resources.getDimensionPixelSize(R.dimen.screen_padding)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                padding + bars.left,
                padding + bars.top,
                padding + bars.right,
                padding + bars.bottom,
            )

            val gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val params = tapArea.layoutParams as LinearLayout.LayoutParams
            params.leftMargin = maxOf(base, gestures.left, cutout.left)
            params.rightMargin = maxOf(base, gestures.right, cutout.right)
            params.bottomMargin = maxOf(base, gestures.bottom, cutout.bottom)
            tapArea.layoutParams = params
            insets
        }
    }

    private fun readDisplayMetrics(): DisplayMetrics {
        val bounds = windowManager.currentWindowMetrics.bounds
        val mode = display?.mode
        return DisplayMetrics(
            windowWidthPx = bounds.width(),
            windowHeightPx = bounds.height(),
            modeWidthPx = mode?.physicalWidth ?: bounds.width(),
            modeHeightPx = mode?.physicalHeight ?: bounds.height(),
            refreshRateHz = mode?.refreshRate ?: 0f,
            densityDpi = resources.configuration.densityDpi,
        )
    }

    // ------------------------------------------------------------------
    // Отрисовка
    // ------------------------------------------------------------------

    private fun render(state: SessionUiState) {
        val active = state.state == SessionState.ACTIVE

        startButton.isEnabled = state.canStart
        finishButton.isEnabled = state.canFinish
        exportButton.isEnabled = state.canExport
        participantInput.isEnabled = !active && state.state != SessionState.CLOSING

        tapArea.acceptingTouches = active

        // Экран не гаснет только на время активной сессии
        if (active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        counters.text = getString(
            R.string.counters_format,
            stateLabel(state.state),
            state.completedTrials,
            state.confirmedTrials,
        )

        summaryView.text = state.summary?.let { format(it) } ?: getString(R.string.summary_empty)

        val error = state.error
        if (error == null && state.queueOverflows == 0L && state.writeFailures == 0L) {
            // INVISIBLE, не GONE: место под строку ошибок зарезервировано, иначе её
            // появление посреди сессии сдвинуло бы область касания.
            errorView.visibility = View.INVISIBLE
        } else {
            errorView.visibility = View.VISIBLE
            errorView.text = getString(
                R.string.error_format,
                state.queueOverflows,
                state.writeFailures,
                error?.code ?: 0,
                error?.message ?: "",
            )
        }

        val notices = ArrayList<String>()
        if (state.recoveredIncompleteSessions > 0) {
            notices.add(getString(R.string.notice_incomplete, state.recoveredIncompleteSessions))
        }
        when (state.exportState) {
            ExportState.RUNNING -> notices.add(getString(R.string.notice_export_running))
            ExportState.READY -> notices.add(
                getString(R.string.notice_export_ready, state.exportMessage ?: ""),
            )
            ExportState.FAILED -> notices.add(
                getString(R.string.notice_export_failed, state.exportMessage ?: ""),
            )
            ExportState.NONE -> Unit
        }
        if (notices.isEmpty()) {
            noticeView.visibility = View.GONE
        } else {
            noticeView.visibility = View.VISIBLE
            noticeView.text = notices.joinToString("\n")
        }

        if (state.exportState == ExportState.READY) shareExport()
    }

    private fun stateLabel(state: SessionState): String = getString(
        when (state) {
            SessionState.IDLE -> R.string.state_idle
            SessionState.ACTIVE -> R.string.state_active
            SessionState.CLOSING -> R.string.state_closing
            SessionState.CLOSED -> R.string.state_closed
        },
    )

    private fun format(s: TrialSummary): String = getString(
        R.string.summary_format,
        s.trialIndex,
        s.completionStatus,
        s.durationMs,
        s.currentSampleCount,
        s.historicalSampleCount,
        s.touchMajor.min, s.touchMajor.max, s.touchMajor.distinct,
        s.touchMinor.min, s.touchMinor.max, s.touchMinor.distinct,
        s.size.min, s.size.max, s.size.distinct,
        s.pressure.min, s.pressure.max, s.pressure.distinct,
        s.timestampPrecision,
        s.appReceiptUptimePrecision,
        s.syncMethod,
        s.samplingUncertaintyNs,
        s.quantizationUncertaintyNs,
    )

    /**
     * Отдать архив в стандартное меню «Поделиться».
     *
     * Через FileProvider: прямой file:// URI на Android 7+ приводит к
     * FileUriExposedException, а копировать архив в общедоступный каталог незачем.
     */
    private fun shareExport() {
        val file = viewModel.controller.consumeExportFile() ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.export", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_title)))
    }
}
