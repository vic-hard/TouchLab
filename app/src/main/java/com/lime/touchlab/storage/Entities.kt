package com.lime.touchlab.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lime.rawtouchcollector.SchemaFields

/**
 * Имена колонок берутся из [SchemaFields] и нигде не дублируются строковыми литералами:
 * CSV-экспорт и `validate_export.py` обязаны видеть те же имена, что и JSON попытки.
 *
 * Нормализация: `timestamp_precision`, `app_receipt_uptime_precision` и `clock_sync_id`
 * постоянны внутри попытки, поэтому хранятся один раз в `trials`, а в `touch_samples.csv`
 * подставляются экспортом.
 */

@Entity(tableName = "devices")
class DeviceEntity(
    @PrimaryKey @ColumnInfo(name = SchemaFields.DEVICE_ID) val deviceId: String,
    @ColumnInfo(name = SchemaFields.MANUFACTURER) val manufacturer: String,
    @ColumnInfo(name = SchemaFields.MODEL) val model: String,
    @ColumnInfo(name = SchemaFields.ANDROID_VERSION) val androidVersion: String,
    @ColumnInfo(name = SchemaFields.SDK_INT) val sdkInt: Int,
    @ColumnInfo(name = SchemaFields.APP_VERSION) val appVersion: String,
    @ColumnInfo(name = SchemaFields.AAR_VERSION) val aarVersion: String,
    @ColumnInfo(name = SchemaFields.DENSITY_DPI) val densityDpi: Int,
)

@Entity(tableName = "display_profiles")
class DisplayProfileEntity(
    @PrimaryKey @ColumnInfo(name = SchemaFields.DISPLAY_PROFILE_ID) val displayProfileId: String,
    @ColumnInfo(name = SchemaFields.WINDOW_WIDTH_PX) val windowWidthPx: Int,
    @ColumnInfo(name = SchemaFields.WINDOW_HEIGHT_PX) val windowHeightPx: Int,
    @ColumnInfo(name = SchemaFields.DISPLAY_MODE_WIDTH_PX) val displayModeWidthPx: Int,
    @ColumnInfo(name = SchemaFields.DISPLAY_MODE_HEIGHT_PX) val displayModeHeightPx: Int,
    @ColumnInfo(name = SchemaFields.DISPLAY_REFRESH_RATE_HZ) val displayRefreshRateHz: Float,
    @ColumnInfo(name = SchemaFields.DENSITY_DPI) val densityDpi: Int,
    @ColumnInfo(name = SchemaFields.CAPTURED_AT_ELAPSED_NS) val capturedAtElapsedNs: Long,
)

/**
 * Сессия.
 *
 * `ended_at_wall_clock_ms` допускает `null` только у незавершённой сессии.
 * `session_status` отличает «идёт» от «закрыта штатно» и от «процесс убили»:
 * запрещено молча продолжать активную сессию после аварийного рестарта.
 */
@Entity(tableName = "sessions")
class SessionEntity(
    @PrimaryKey @ColumnInfo(name = SchemaFields.SESSION_ID) val sessionId: String,
    @ColumnInfo(name = SchemaFields.DEVICE_ID) val deviceId: String,
    @ColumnInfo(name = SchemaFields.PARTICIPANT_ID) val participantId: String,
    @ColumnInfo(name = SchemaFields.STARTED_AT_WALL_CLOCK_MS) val startedAtWallClockMs: Long,
    @ColumnInfo(name = SchemaFields.ENDED_AT_WALL_CLOCK_MS) val endedAtWallClockMs: Long?,
    @ColumnInfo(name = SchemaFields.PHONE_SUPPORT_MODE) val phoneSupportMode: String,
    @ColumnInfo(name = SchemaFields.CLOCK_SYNC_ID) val clockSyncId: String?,
    @ColumnInfo(name = SchemaFields.SESSION_STATUS) val sessionStatus: String,
    @ColumnInfo(name = SchemaFields.SCHEMA_VERSION) val schemaVersion: String,
)

@Entity(tableName = "clock_sync", indices = [Index(SchemaFields.SESSION_ID)])
class ClockSyncEntity(
    @PrimaryKey @ColumnInfo(name = SchemaFields.CLOCK_SYNC_ID) val clockSyncId: String,
    @ColumnInfo(name = SchemaFields.SESSION_ID) val sessionId: String,
    @ColumnInfo(name = SchemaFields.UPTIME_TIMESTAMP_NS) val uptimeTimestampNs: Long,
    @ColumnInfo(name = SchemaFields.ELAPSED_REALTIME_TIMESTAMP_NS) val elapsedRealtimeTimestampNs: Long,
    @ColumnInfo(name = SchemaFields.OFFSET_NS) val offsetNs: Long,
    @ColumnInfo(name = SchemaFields.SYNC_SAMPLING_UNCERTAINTY_NS) val samplingUncertaintyNs: Long,
    @ColumnInfo(name = SchemaFields.SYNC_QUANTIZATION_UNCERTAINTY_NS) val quantizationUncertaintyNs: Long,
    @ColumnInfo(name = SchemaFields.SYNC_METHOD) val syncMethod: String,
    @ColumnInfo(name = SchemaFields.UPTIME_MEASUREMENT_PRECISION) val uptimeMeasurementPrecision: String,
)

@Entity(tableName = "trials", indices = [Index(SchemaFields.SESSION_ID)])
class TrialEntity(
    @PrimaryKey @ColumnInfo(name = SchemaFields.TRIAL_ID) val trialId: String,
    @ColumnInfo(name = SchemaFields.SESSION_ID) val sessionId: String,
    @ColumnInfo(name = SchemaFields.PARTICIPANT_ID) val participantId: String,
    @ColumnInfo(name = SchemaFields.TRIAL_INDEX) val trialIndex: Int,
    @ColumnInfo(name = SchemaFields.TASK_GROUP) val taskGroup: String,
    @ColumnInfo(name = SchemaFields.SCENARIO_TYPE) val scenarioType: String,
    @ColumnInfo(name = SchemaFields.DISPLAY_PROFILE_ID) val displayProfileId: String,
    @ColumnInfo(name = SchemaFields.CLOCK_SYNC_ID) val clockSyncId: String,
    @ColumnInfo(name = SchemaFields.TOUCH_DOWN_COMMON_TIMESTAMP_NS) val touchDownCommonTimestampNs: Long,
    @ColumnInfo(name = SchemaFields.TOUCH_UP_COMMON_TIMESTAMP_NS) val touchUpCommonTimestampNs: Long,
    @ColumnInfo(name = SchemaFields.CONTACT_DURATION_NS) val contactDurationNs: Long,
    @ColumnInfo(name = SchemaFields.COMPLETION_STATUS) val completionStatus: String,
    @ColumnInfo(name = SchemaFields.CURRENT_SAMPLE_COUNT) val currentSampleCount: Int,
    @ColumnInfo(name = SchemaFields.HISTORICAL_SAMPLE_COUNT) val historicalSampleCount: Int,
    @ColumnInfo(name = SchemaFields.SECOND_POINTER_OBSERVED) val secondPointerObserved: Boolean,
    @ColumnInfo(name = SchemaFields.TIMESTAMP_PRECISION) val timestampPrecision: String,
    @ColumnInfo(name = SchemaFields.APP_RECEIPT_UPTIME_PRECISION) val appReceiptUptimePrecision: String,
    @ColumnInfo(name = SchemaFields.SCHEMA_VERSION) val schemaVersion: String,
)

/**
 * Отсчёт. Historical sample — самостоятельная строка с собственным временем и
 * координатами, отличается только `is_historical` и `history_index`.
 *
 * `history_index` у текущего отсчёта — `null`, а не `-1`: «неприменимо» пишется явным
 * null, а не значением-заглушкой.
 */
@Entity(tableName = "touch_samples", indices = [Index(SchemaFields.TRIAL_ID)])
class TouchSampleEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "row_id") val rowId: Long = 0,
    @ColumnInfo(name = SchemaFields.TRIAL_ID) val trialId: String,
    @ColumnInfo(name = SchemaFields.SAMPLE_INDEX) val sampleIndex: Int,
    @ColumnInfo(name = SchemaFields.EVENT_ACTION) val eventAction: Int,
    @ColumnInfo(name = SchemaFields.POINTER_ID) val pointerId: Int,
    @ColumnInfo(name = SchemaFields.POINTER_INDEX) val pointerIndex: Int,
    @ColumnInfo(name = SchemaFields.ACTION_INDEX) val actionIndex: Int,
    @ColumnInfo(name = SchemaFields.POINTER_COUNT) val pointerCount: Int,
    @ColumnInfo(name = SchemaFields.TOUCH_EVENT_TIME_UPTIME_MS) val eventTimeUptimeMs: Long,
    @ColumnInfo(name = SchemaFields.TOUCH_EVENT_TIME_UPTIME_NS) val eventTimeUptimeNs: Long,
    @ColumnInfo(name = SchemaFields.COMMON_TIMESTAMP_NS) val commonTimestampNs: Long,
    @ColumnInfo(name = SchemaFields.APP_RECEIPT_TIME_UPTIME_NS) val appReceiptTimeUptimeNs: Long,
    @ColumnInfo(name = SchemaFields.APP_RECEIPT_TIME_ELAPSED_NS) val appReceiptTimeElapsedNs: Long,
    @ColumnInfo(name = SchemaFields.RELATIVE_TIME_MS) val relativeTimeMs: Double,
    @ColumnInfo(name = SchemaFields.X) val x: Float,
    @ColumnInfo(name = SchemaFields.Y) val y: Float,
    @ColumnInfo(name = SchemaFields.TOUCH_MAJOR) val touchMajor: Float,
    @ColumnInfo(name = SchemaFields.TOUCH_MINOR) val touchMinor: Float,
    @ColumnInfo(name = SchemaFields.SIZE) val size: Float,
    @ColumnInfo(name = SchemaFields.PRESSURE) val pressure: Float,
    @ColumnInfo(name = SchemaFields.ORIENTATION) val orientation: Float,
    @ColumnInfo(name = SchemaFields.TOOL_TYPE) val toolType: Int,
    @ColumnInfo(name = SchemaFields.IS_HISTORICAL) val isHistorical: Boolean,
    @ColumnInfo(name = SchemaFields.HISTORY_INDEX) val historyIndex: Int?,
)
