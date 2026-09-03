package com.lime.touchlab.export

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.lime.rawtouchcollector.Schema
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
 * Запись внутрь ZIP во время сессии не выполняется ни разу: данные всё это время
 * лежат в Room, а архив строится из них одним проходом.
 *
 * Состав и порядок колонок берутся из [ExportSchema] — оттуда же собирается `schema.json`,
 * поэтому шапка CSV и описание полей не могут разойтись.
 *
 * Пустая ячейка означает `null`. У большинства полей это «неприменимо» — так пишутся
 * `history_index` у текущего отсчёта и `ended_at_wall_clock_ms` у незавершённой сессии;
 * у счётчиков `sessions.csv` это «неизвестно: сессия не была закрыта штатно».
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

    fun exportDir(): File = File(context.filesDir, "exports").apply { mkdirs() }

    /**
     * Собрать архив сессии. Бросает исключение, если сессии нет.
     *
     * Успешный экспорт ничего не удаляет из базы — метод только читает.
     */
    fun export(sessionId: String): File {
        val session = dao.session(sessionId)
            ?: throw IllegalStateException("Сессия $sessionId не найдена в хранилище")
        val device = dao.device(session.deviceId)
        val trials = dao.trialsOf(sessionId)
        val profiles = dao.displayProfilesOf(sessionId)
        val syncPoints = dao.clockSyncOf(sessionId)

        pruneOldArchives()

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
            .format(Date(session.startedAtWallClockMs))
        val target = File(exportDir(), "touchlab-" + stamp + "-" + sessionId.take(8) + ".zip")

        ZipOutputStream(target.outputStream().buffered()).use { zip ->

            zip.entry(ExportSchema.DEVICES) { w ->
                if (device != null) {
                    w.row(
                        device.deviceId, device.manufacturer, device.model,
                        device.androidVersion, device.sdkInt, device.appVersion,
                        device.aarVersion, device.densityDpi,
                    )
                }
            }

            zip.entry(ExportSchema.DISPLAY_PROFILES) { w ->
                for (p in profiles) {
                    w.row(
                        p.displayProfileId, p.windowWidthPx, p.windowHeightPx,
                        p.displayModeWidthPx, p.displayModeHeightPx, p.displayRefreshRateHz,
                        p.densityDpi, p.capturedAtElapsedNs,
                    )
                }
            }

            zip.entry(ExportSchema.SESSIONS) { w ->
                w.row(
                    session.sessionId, session.deviceId, session.participantId,
                    session.startedAtWallClockMs, session.endedAtWallClockMs,
                    session.phoneSupportMode, session.clockSyncId,
                    session.sessionStatus, session.schemaVersion,
                    session.trialsAccepted, session.trialsConfirmed,
                    session.queueOverflows, session.writeFailures,
                    session.eventsBeforeStart, session.eventsAfterEnd,
                    session.eventsAfterSessionClose, session.eventsDiscardedAfterMultitouch,
                    session.implicitCancels, session.multitouchErrors,
                    session.trialsWithStaleDisplayProfile, session.clockSyncFallbacks,
                )
            }

            zip.entry(ExportSchema.CLOCK_SYNC) { w ->
                for (c in syncPoints) {
                    w.row(
                        c.clockSyncId, c.sessionId, c.uptimeTimestampNs,
                        c.elapsedRealtimeTimestampNs, c.offsetNs, c.samplingUncertaintyNs,
                        c.quantizationUncertaintyNs, c.syncMethod, c.uptimeMeasurementPrecision,
                    )
                }
            }

            zip.entry(ExportSchema.TRIALS) { w ->
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

            zip.entry(ExportSchema.TOUCH_SAMPLES) { w ->
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

            zip.rawEntry("schema.json", ExportSchema.schemaJson())
            zip.rawEntry("README.md", readme(session.sessionId, trials.size))
        }

        Log.i(TAG, "архив собран: " + target.absolutePath + ", попыток " + trials.size)
        return target
    }

    /**
     * Оставить только последние [KEEP_ARCHIVES] архивов.
     *
     * Каталог иначе растёт без предела: каждый экспорт добавляет файл и никогда ничего не
     * убирает. На данные в Room не влияет, они не удаляются при экспорте.
     */
    private fun pruneOldArchives() {
        val archives = exportDir().listFiles { f -> f.isFile && f.name.endsWith(".zip") }
            ?: return
        if (archives.size < KEEP_ARCHIVES) return
        archives.sortByDescending { it.lastModified() }
        for (i in (KEEP_ARCHIVES - 1) until archives.size) {
            if (!archives[i].delete()) {
                Log.w(TAG, "не удалось удалить старый архив " + archives[i].name)
            }
        }
    }

    private fun readme(sessionId: String, trialCount: Int): String = """
# Экспорт сессии TouchLab, этап 1

Версия схемы: `${Schema.VERSION}`
Сессия: `$sessionId`
Попыток в архиве: $trialCount

Описание всех полей — `schema.json` в этом же архиве: имя, тип, единица измерения,
допустимость `null` и смысл каждой колонки каждого CSV. Развёрнутое описание схемы и
временной модели — `docs/schema-stage1.md` в исходниках.

Файлы в кодировке UTF-8 без BOM, перевод строки LF, экранирование по RFC 4180.

Пустая ячейка CSV означает `null`. У большинства полей это «неприменимо» — так
записываются `history_index` у текущего отсчёта и `ended_at_wall_clock_ms` у сессии,
завершение которой не состоялось. У счётчиков в `sessions.csv` пустая ячейка означает
другое: «сессия не была закрыта штатно, счётчик неизвестен».

Счётчики `sessions.csv` (§9.5) показывают, что происходило с записью: `trials_accepted`
против `trials_confirmed`, `queue_overflows` и `write_failures`. Попытка, не попавшая в
`trials.csv`, обязана быть объяснена ими — сохранённой без подтверждения она не
показывается нигде.

Три временные шкалы хранятся раздельно и не выводятся одна из другой: календарное
время сессии (`*_wall_clock_ms`), время события и получения (`uptime`) и общая
монотонная шкала (`common_timestamp_ns`, `elapsedRealtime`).

Проверяется скриптом `validate_export.py`.
""".trimIndent()

    private companion object {
        const val TAG = "TouchLabExport"
        const val KEY_DUMP = "diagnostic_dump_enabled"

        /** Сколько архивов хранить в каталоге экспорта. */
        const val KEEP_ARCHIVES = 10
    }
}

/**
 * Один CSV-файл архива: шапка печатается из объявления, строки сверяются с ней.
 * Поток не закрывается — его закрывает ZipOutputStream.
 */
private inline fun ZipOutputStream.entry(spec: FileSpec, body: (CsvSink) -> Unit) {
    putNextEntry(ZipEntry(spec.fileName))
    val writer = OutputStreamWriter(this, StandardCharsets.UTF_8)
    val sink = CsvSink(writer, spec)
    sink.header()
    body(sink)
    writer.flush()
    closeEntry()
}

/** Запись архива, которая не является CSV: schema.json и README.md. */
private fun ZipOutputStream.rawEntry(name: String, text: String) {
    putNextEntry(ZipEntry(name))
    val writer = OutputStreamWriter(this, StandardCharsets.UTF_8)
    writer.write(text)
    writer.flush()
    closeEntry()
}
