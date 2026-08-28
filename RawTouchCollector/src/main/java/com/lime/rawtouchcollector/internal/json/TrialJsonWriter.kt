package com.lime.rawtouchcollector.internal.json

import android.util.JsonWriter
import com.lime.rawtouchcollector.TrialSnapshot
import java.io.StringWriter

/**
 * Сериализация завершённой попытки.
 *
 * Потоковая запись через android.util.JsonWriter: без сторонних библиотек и без
 * построения промежуточного дерева объектов. Вызывается только на worker-потоке.
 *
 * Недоступные поля пишутся явным null, а не пропускаются
 */
internal object TrialJsonWriter {

    fun write(trial: TrialSnapshot): String {
        val out = StringWriter(estimateSize(trial))
        JsonWriter(out).use { w ->
            w.beginObject()

            w.name(SchemaFields.SCHEMA_VERSION).value(trial.schemaVersion)
            w.name(SchemaFields.TRIAL_ID).value(trial.trialId)
            w.name(SchemaFields.SESSION_ID).value(trial.sessionId)
            w.name(SchemaFields.PARTICIPANT_ID).value(trial.participantId)
            w.name(SchemaFields.TRIAL_INDEX).value(trial.trialIndex.toLong())
            w.name(SchemaFields.TASK_GROUP).value(trial.taskGroup)
            w.name(SchemaFields.SCENARIO_TYPE).value(trial.scenarioType)

            w.name(SchemaFields.TOUCH_DOWN_COMMON_TIMESTAMP_NS)
                .value(trial.touchDownCommonTimestampNs)
            w.name(SchemaFields.TOUCH_UP_COMMON_TIMESTAMP_NS)
                .value(trial.touchUpCommonTimestampNs)
            w.name(SchemaFields.CONTACT_DURATION_NS).value(trial.contactDurationNs)
            w.name(SchemaFields.COMPLETION_STATUS).value(trial.completionStatus)
            w.name(SchemaFields.CURRENT_SAMPLE_COUNT).value(trial.currentSampleCount.toLong())
            w.name(SchemaFields.HISTORICAL_SAMPLE_COUNT)
                .value(trial.historicalSampleCount.toLong())
            w.name(SchemaFields.SECOND_POINTER_OBSERVED).value(trial.secondPointerObserved)
            w.name(SchemaFields.TIMESTAMP_PRECISION).value(trial.timestampPrecision)
            w.name(SchemaFields.APP_RECEIPT_UPTIME_PRECISION)
                .value(trial.appReceiptUptimePrecision)

            writeDisplayProfile(w, trial)
            writeClockSync(w, trial)
            writeSamples(w, trial)

            w.endObject()
        }
        return out.toString()
    }

    private fun writeDisplayProfile(w: JsonWriter, trial: TrialSnapshot) {
        val p = trial.displayProfile
        w.name(SchemaFields.DISPLAY_PROFILE).beginObject()
        w.name(SchemaFields.DISPLAY_PROFILE_ID).value(p.displayProfileId)
        w.name(SchemaFields.WINDOW_WIDTH_PX).value(p.windowWidthPx.toLong())
        w.name(SchemaFields.WINDOW_HEIGHT_PX).value(p.windowHeightPx.toLong())
        w.name(SchemaFields.DISPLAY_MODE_WIDTH_PX).value(p.displayModeWidthPx.toLong())
        w.name(SchemaFields.DISPLAY_MODE_HEIGHT_PX).value(p.displayModeHeightPx.toLong())
        w.name(SchemaFields.DISPLAY_REFRESH_RATE_HZ).value(p.displayRefreshRateHz.toDouble())
        w.name(SchemaFields.DENSITY_DPI).value(p.densityDpi.toLong())
        w.name(SchemaFields.CAPTURED_AT_ELAPSED_NS).value(p.capturedAtElapsedNs)
        w.endObject()
    }

    private fun writeClockSync(w: JsonWriter, trial: TrialSnapshot) {
        val c = trial.clockSync
        w.name(SchemaFields.CLOCK_SYNC).beginObject()
        w.name(SchemaFields.CLOCK_SYNC_ID).value(c.clockSyncId)
        w.name(SchemaFields.UPTIME_TIMESTAMP_NS).value(c.uptimeTimestampNs)
        w.name(SchemaFields.ELAPSED_REALTIME_TIMESTAMP_NS).value(c.elapsedRealtimeTimestampNs)
        w.name(SchemaFields.OFFSET_NS).value(c.offsetNs)
        w.name(SchemaFields.SYNC_SAMPLING_UNCERTAINTY_NS).value(c.samplingUncertaintyNs)
        w.name(SchemaFields.SYNC_QUANTIZATION_UNCERTAINTY_NS)
            .value(c.quantizationUncertaintyNs)
        w.name(SchemaFields.SYNC_METHOD).value(c.syncMethod)
        w.name(SchemaFields.UPTIME_MEASUREMENT_PRECISION).value(c.uptimeMeasurementPrecision)
        w.endObject()
    }

    private fun writeSamples(w: JsonWriter, trial: TrialSnapshot) {
        val s = trial.samples
        val clockSyncId = trial.clockSync.clockSyncId
        val timestampPrecision = trial.timestampPrecision
        val receiptPrecision = trial.appReceiptUptimePrecision

        w.name(SchemaFields.SAMPLES).beginArray()
        for (i in 0 until s.count) {
            w.beginObject()
            w.name(SchemaFields.SAMPLE_INDEX).value(i.toLong())
            w.name(SchemaFields.EVENT_ACTION).value(s.eventAction(i).toLong())
            w.name(SchemaFields.POINTER_ID).value(s.pointerId(i).toLong())
            w.name(SchemaFields.POINTER_INDEX).value(s.pointerIndex(i).toLong())
            w.name(SchemaFields.ACTION_INDEX).value(s.actionIndex(i).toLong())
            w.name(SchemaFields.POINTER_COUNT).value(s.pointerCount(i).toLong())

            w.name(SchemaFields.TOUCH_EVENT_TIME_UPTIME_MS).value(s.eventTimeUptimeMs(i))
            w.name(SchemaFields.TOUCH_EVENT_TIME_UPTIME_NS).value(s.eventTimeUptimeNs(i))
            w.name(SchemaFields.TIMESTAMP_PRECISION).value(timestampPrecision)
            w.name(SchemaFields.COMMON_TIMESTAMP_NS).value(s.commonTimestampNs(i))
            w.name(SchemaFields.APP_RECEIPT_TIME_UPTIME_NS).value(s.appReceiptTimeUptimeNs(i))
            w.name(SchemaFields.APP_RECEIPT_UPTIME_PRECISION).value(receiptPrecision)
            w.name(SchemaFields.APP_RECEIPT_TIME_ELAPSED_NS).value(s.appReceiptTimeElapsedNs(i))
            w.name(SchemaFields.CLOCK_SYNC_ID).value(clockSyncId)
            w.name(SchemaFields.RELATIVE_TIME_MS).value(s.relativeTimeMs(i))

            w.name(SchemaFields.X).value(s.x(i).toDouble())
            w.name(SchemaFields.Y).value(s.y(i).toDouble())
            w.name(SchemaFields.TOUCH_MAJOR).value(s.touchMajor(i).toDouble())
            w.name(SchemaFields.TOUCH_MINOR).value(s.touchMinor(i).toDouble())
            w.name(SchemaFields.SIZE).value(s.size(i).toDouble())
            w.name(SchemaFields.PRESSURE).value(s.pressure(i).toDouble())
            w.name(SchemaFields.ORIENTATION).value(s.orientation(i).toDouble())
            w.name(SchemaFields.TOOL_TYPE).value(s.toolType(i).toLong())

            w.name(SchemaFields.IS_HISTORICAL).value(s.isHistorical(i))
            if (s.isHistorical(i)) {
                w.name(SchemaFields.HISTORY_INDEX).value(s.historyIndex(i).toLong())
            } else {
                // Не пропуск, а явное «неприменимо»: критерий 16.
                w.name(SchemaFields.HISTORY_INDEX).nullValue()
            }
            w.endObject()
        }
        w.endArray()
    }

    private fun estimateSize(trial: TrialSnapshot): Int = 512 + trial.samples.count * 420
}
