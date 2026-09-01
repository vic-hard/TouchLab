package com.lime.touchlab.export

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.lime.rawtouchcollector.Schema
import com.lime.rawtouchcollector.SchemaFields
import com.lime.touchlab.storage.TouchLabDao
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Сборка ZIP-архива сессии и диагностический дамп попытки.
 *
 * Архив собирается только по уже зафиксированной сессии и только вне главного потока.
 * Запись внутрь ZIP во время сессии не выполняется ни разу:
 * данные всё это время лежат в Room, а архив строится из них одним проходом.
 *
 * Имена колонок берутся из [SchemaFields] — тех же, что в JSON попытки, поэтому
 * `validate_export.py` может опираться на одно описание схемы для обоих форматов.
 *
 * Пустая ячейка означает `null`, то есть «неприменимо»: так пишутся `history_index`
 * у текущего отсчёта и `ended_at_wall_clock_ms` у незавершённой сессии.
 */
class SessionExporter(
    private val context: Context,
    private val dao: TouchLabDao,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("touchlab", Context.MODE_PRIVATE)

    /**
     * Диагностический дамп последней попытки в JSON.
     *
     * Хранением не является и по умолчанию выключен. Нужен для процедуры замера частоты
     * отсчётов (`docs/measure_touch_rate.py`), поэтому не удалён, а вынесен под флаг.
     */
    var diagnosticDumpEnabled: Boolean
        get() = prefs.getBoolean(KEY_DUMP, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DUMP, value).apply()
        }

    fun dumpTrialIfEnabled(trialJson: String) {
        if (!diagnosticDumpEnabled) return
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            File(dir, "last-trial.json").writeText(trialJson)
        } catch (e: Exception) {
            Log.w(TAG, "диагностический дамп не записан", e)
        }
    }

    /** Каталог архивов. Отдан FileProvider'у, см. res/xml/file_paths.xml. */
    fun exportDir(): File = File(context.cacheDir, "exports").apply { mkdirs() }

    /**
     * Собрать архив сессии. Бросает исключение, если сессии нет.
     *
     * Успешный экспорт ничего не удаляет из базы.
     */
    fun export(sessionId: String): File {
        val session = dao.session(sessionId)
            ?: throw IllegalStateException("Сессия $sessionId не найдена в хранилище")
        val device = dao.device(session.deviceId)
        val trials = dao.trialsOf(sessionId)
        val profiles = dao.displayProfilesOf(sessionId)
        val syncPoints = dao.clockSyncOf(sessionId)

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
            .format(Date(session.startedAtWallClockMs))
        val target = File(exportDir(), "touchlab-" + stamp + "-" + sessionId.take(8) + ".zip")

        ZipOutputStream(target.outputStream().buffered()).use { zip ->

            zip.entry("devices.csv") { w ->
                w.row(
                    SchemaFields.DEVICE_ID, SchemaFields.MANUFACTURER, SchemaFields.MODEL,
                    SchemaFields.ANDROID_VERSION, SchemaFields.SDK_INT, SchemaFields.APP_VERSION,
                    SchemaFields.AAR_VERSION, SchemaFields.DENSITY_DPI,
                )
                if (device != null) {
                    w.row(
                        device.deviceId, device.manufacturer, device.model,
                        device.androidVersion, device.sdkInt, device.appVersion,
                        device.aarVersion, device.densityDpi,
                    )
                }
            }

            zip.entry("display_profiles.csv") { w ->
                w.row(
                    SchemaFields.DISPLAY_PROFILE_ID, SchemaFields.WINDOW_WIDTH_PX,
                    SchemaFields.WINDOW_HEIGHT_PX, SchemaFields.DISPLAY_MODE_WIDTH_PX,
                    SchemaFields.DISPLAY_MODE_HEIGHT_PX, SchemaFields.DISPLAY_REFRESH_RATE_HZ,
                    SchemaFields.DENSITY_DPI, SchemaFields.CAPTURED_AT_ELAPSED_NS,
                )
                for (p in profiles) {
                    w.row(
                        p.displayProfileId, p.windowWidthPx, p.windowHeightPx,
                        p.displayModeWidthPx, p.displayModeHeightPx, p.displayRefreshRateHz,
                        p.densityDpi, p.capturedAtElapsedNs,
                    )
                }
            }

            zip.entry("sessions.csv") { w ->
                w.row(
                    SchemaFields.SESSION_ID, SchemaFields.DEVICE_ID, SchemaFields.PARTICIPANT_ID,
                    SchemaFields.STARTED_AT_WALL_CLOCK_MS, SchemaFields.ENDED_AT_WALL_CLOCK_MS,
                    SchemaFields.PHONE_SUPPORT_MODE, SchemaFields.CLOCK_SYNC_ID,
                    SchemaFields.SESSION_STATUS, SchemaFields.SCHEMA_VERSION,
                )
                w.row(
                    session.sessionId, session.deviceId, session.participantId,
                    session.startedAtWallClockMs, session.endedAtWallClockMs,
                    session.phoneSupportMode, session.clockSyncId,
                    session.sessionStatus, session.schemaVersion,
                )
            }

            zip.entry("clock_sync.csv") { w ->
                w.row(
                    SchemaFields.CLOCK_SYNC_ID, SchemaFields.SESSION_ID,
                    SchemaFields.UPTIME_TIMESTAMP_NS, SchemaFields.ELAPSED_REALTIME_TIMESTAMP_NS,
                    SchemaFields.OFFSET_NS, SchemaFields.SYNC_SAMPLING_UNCERTAINTY_NS,
                    SchemaFields.SYNC_QUANTIZATION_UNCERTAINTY_NS, SchemaFields.SYNC_METHOD,
                    SchemaFields.UPTIME_MEASUREMENT_PRECISION,
                )
                for (c in syncPoints) {
                    w.row(
                        c.clockSyncId, c.sessionId, c.uptimeTimestampNs,
                        c.elapsedRealtimeTimestampNs, c.offsetNs, c.samplingUncertaintyNs,
                        c.quantizationUncertaintyNs, c.syncMethod, c.uptimeMeasurementPrecision,
                    )
                }
            }

            zip.entry("trials.csv") { w ->
                w.row(
                    SchemaFields.TRIAL_ID, SchemaFields.SESSION_ID, SchemaFields.PARTICIPANT_ID,
                    SchemaFields.TRIAL_INDEX, SchemaFields.TASK_GROUP, SchemaFields.SCENARIO_TYPE,
                    SchemaFields.DISPLAY_PROFILE_ID, SchemaFields.CLOCK_SYNC_ID,
                    SchemaFields.TOUCH_DOWN_COMMON_TIMESTAMP_NS,
                    SchemaFields.TOUCH_UP_COMMON_TIMESTAMP_NS, SchemaFields.CONTACT_DURATION_NS,
                    SchemaFields.COMPLETION_STATUS, SchemaFields.CURRENT_SAMPLE_COUNT,
                    SchemaFields.HISTORICAL_SAMPLE_COUNT, SchemaFields.SECOND_POINTER_OBSERVED,
                    SchemaFields.TIMESTAMP_PRECISION, SchemaFields.APP_RECEIPT_UPTIME_PRECISION,
                    SchemaFields.SCHEMA_VERSION,
                )
                for (t in trials) {
                    w.row(
                        t.trialId, t.sessionId, t.participantId, t.trialIndex, t.taskGroup,
                        t.scenarioType, t.displayProfileId, t.clockSyncId,
                        t.touchDownCommonTimestampNs, t.touchUpCommonTimestampNs,
                        t.contactDurationNs, t.completionStatus, t.currentSampleCount,
                        t.historicalSampleCount, t.secondPointerObserved, t.timestampPrecision,
                        t.appReceiptUptimePrecision, t.schemaVersion,
                    )
                }
            }

            zip.entry("touch_samples.csv") { w ->
                w.row(
                    SchemaFields.TRIAL_ID, SchemaFields.SAMPLE_INDEX, SchemaFields.EVENT_ACTION,
                    SchemaFields.POINTER_ID, SchemaFields.POINTER_INDEX, SchemaFields.ACTION_INDEX,
                    SchemaFields.POINTER_COUNT, SchemaFields.TOUCH_EVENT_TIME_UPTIME_MS,
                    SchemaFields.TOUCH_EVENT_TIME_UPTIME_NS, SchemaFields.TIMESTAMP_PRECISION,
                    SchemaFields.COMMON_TIMESTAMP_NS, SchemaFields.APP_RECEIPT_TIME_UPTIME_NS,
                    SchemaFields.APP_RECEIPT_UPTIME_PRECISION,
                    SchemaFields.APP_RECEIPT_TIME_ELAPSED_NS, SchemaFields.CLOCK_SYNC_ID,
                    SchemaFields.RELATIVE_TIME_MS, SchemaFields.X, SchemaFields.Y,
                    SchemaFields.TOUCH_MAJOR, SchemaFields.TOUCH_MINOR, SchemaFields.SIZE,
                    SchemaFields.PRESSURE, SchemaFields.ORIENTATION, SchemaFields.TOOL_TYPE,
                    SchemaFields.IS_HISTORICAL, SchemaFields.HISTORY_INDEX,
                )
                // Попытка за попыткой: сто попыток по ~250 отсчётов — это десятки тысяч
                // строк, и держать их в памяти одним списком незачем.
                for (t in trials) {
                    for (s in dao.samplesOf(t.trialId)) {
                        w.row(
                            s.trialId, s.sampleIndex, s.eventAction, s.pointerId, s.pointerIndex,
                            s.actionIndex, s.pointerCount, s.eventTimeUptimeMs, s.eventTimeUptimeNs,
                            // Постоянные внутри попытки поля подставляются из строки попытки:
                            // в базе они лежат один раз, в CSV печатаются в каждой строке
                            t.timestampPrecision, s.commonTimestampNs, s.appReceiptTimeUptimeNs,
                            t.appReceiptUptimePrecision, s.appReceiptTimeElapsedNs, t.clockSyncId,
                            s.relativeTimeMs, s.x, s.y, s.touchMajor, s.touchMinor, s.size,
                            s.pressure, s.orientation, s.toolType, s.isHistorical, s.historyIndex,
                        )
                    }
                }
            }

            zip.entry("schema.json") { w -> w.raw(schemaJson()) }
            zip.entry("README.md") { w -> w.raw(readme(session.sessionId, trials.size)) }
        }

        Log.i(TAG, "архив собран: " + target.absolutePath + ", попыток " + trials.size)
        return target
    }

    private fun schemaJson(): String = """
{
  "schema_version": "${Schema.VERSION}",
  "null_representation": "empty CSV cell",
  "files": {
    "devices.csv": "устройство: производитель, модель, версии приложения и AAR",
    "display_profiles.csv": "профили дисплея, действовавшие в сессии",
    "sessions.csv": "сессия; ended_at_wall_clock_ms пуст только у незавершённой",
    "clock_sync.csv": "точки синхронизации uptime и elapsedRealtime",
    "trials.csv": "завершённые попытки",
    "touch_samples.csv": "отсчёты; historical sample — отдельная строка"
  },
  "identifiers": {
    "session_id": "UUID",
    "trial_id": "UUID",
    "clock_sync_id": "UUID",
    "display_profile_id": "UUID",
    "participant_id": "произвольная строка"
  },
  "notes": [
    "common_timestamp_ns = touch_event_time_uptime_ns + offset_ns соответствующей clock_sync",
    "display_refresh_rate_hz не является частотой тач-контроллера",
    "постоянный pressure или size на конкретном устройстве сохраняется как есть"
  ]
}
""".trimIndent()

    private fun readme(sessionId: String, trialCount: Int): String = """
# Экспорт сессии TouchLab, этап 1

Версия схемы: `${Schema.VERSION}`
Сессия: `$sessionId`
Попыток в архиве: $trialCount

Состав архива и смысл полей — `schema.json` и `docs/schema-stage1.md` в исходниках.

Пустая ячейка CSV означает `null` — «неприменимо», а не ноль. Так записываются
`history_index` у текущего отсчёта и `ended_at_wall_clock_ms` у сессии, завершение
которой не состоялось.

Три временные шкалы хранятся раздельно и не выводятся одна из другой: календарное
время сессии (`*_wall_clock_ms`), время события и получения (`uptime`) и общая
монотонная шкала (`common_timestamp_ns`, `elapsedRealtime`).

Проверяется скриптом `validate_export.py`.
""".trimIndent()

    private companion object {
        const val TAG = "TouchLabExport"
        const val KEY_DUMP = "diagnostic_dump_enabled"
    }
}

/** Одна запись архива. Поток не закрывается — его закрывает ZipOutputStream. */
private inline fun ZipOutputStream.entry(name: String, body: (CsvSink) -> Unit) {
    putNextEntry(ZipEntry(name))
    val writer = OutputStreamWriter(this, StandardCharsets.UTF_8)
    body(CsvSink(writer))
    writer.flush()
    closeEntry()
}
