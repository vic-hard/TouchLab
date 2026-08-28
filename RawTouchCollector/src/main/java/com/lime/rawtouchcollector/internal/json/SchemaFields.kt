package com.lime.rawtouchcollector.internal.json

/**
 * Имена полей схемы — единственное место, где они существуют как строки.
 *
 * Эти же имена используют CSV-экспорт и validate_export.py.
 * Литералов имён полей вне этого файла быть не должно.
 *
 * Описание полей — docs/schema-stage1.md.
 */
internal object SchemaFields {

    // --- попытка ---
    const val SCHEMA_VERSION = "schema_version"
    const val TRIAL_ID = "trial_id"
    const val SESSION_ID = "session_id"
    const val PARTICIPANT_ID = "participant_id"
    const val TRIAL_INDEX = "trial_index"
    const val TASK_GROUP = "task_group"
    const val SCENARIO_TYPE = "scenario_type"
    const val TOUCH_DOWN_COMMON_TIMESTAMP_NS = "touch_down_common_timestamp_ns"
    const val TOUCH_UP_COMMON_TIMESTAMP_NS = "touch_up_common_timestamp_ns"
    const val CONTACT_DURATION_NS = "contact_duration_ns"
    const val COMPLETION_STATUS = "completion_status"
    const val CURRENT_SAMPLE_COUNT = "current_sample_count"
    const val HISTORICAL_SAMPLE_COUNT = "historical_sample_count"
    const val SECOND_POINTER_OBSERVED = "second_pointer_observed"

    // --- профиль дисплея ---
    const val DISPLAY_PROFILE = "display_profile"
    const val DISPLAY_PROFILE_ID = "display_profile_id"
    const val WINDOW_WIDTH_PX = "window_width_px"
    const val WINDOW_HEIGHT_PX = "window_height_px"
    const val DISPLAY_MODE_WIDTH_PX = "display_mode_width_px"
    const val DISPLAY_MODE_HEIGHT_PX = "display_mode_height_px"
    const val DISPLAY_REFRESH_RATE_HZ = "display_refresh_rate_hz"
    const val DENSITY_DPI = "density_dpi"
    const val CAPTURED_AT_ELAPSED_NS = "captured_at_elapsed_ns"

    // --- точка синхронизации ---
    const val CLOCK_SYNC = "clock_sync"
    const val CLOCK_SYNC_ID = "clock_sync_id"
    const val UPTIME_TIMESTAMP_NS = "uptime_timestamp_ns"
    const val ELAPSED_REALTIME_TIMESTAMP_NS = "elapsed_realtime_timestamp_ns"
    const val OFFSET_NS = "offset_ns"
    const val SYNC_SAMPLING_UNCERTAINTY_NS = "sync_sampling_uncertainty_ns"
    const val SYNC_QUANTIZATION_UNCERTAINTY_NS = "sync_quantization_uncertainty_ns"
    const val SYNC_METHOD = "sync_method"
    const val UPTIME_MEASUREMENT_PRECISION = "uptime_measurement_precision"

    // --- отсчёты ---
    const val SAMPLES = "samples"
    const val SAMPLE_INDEX = "sample_index"
    const val EVENT_ACTION = "event_action"
    const val POINTER_ID = "pointer_id"
    const val POINTER_INDEX = "pointer_index"
    const val ACTION_INDEX = "action_index"
    const val POINTER_COUNT = "pointer_count"
    const val TOUCH_EVENT_TIME_UPTIME_MS = "touch_event_time_uptime_ms"
    const val TOUCH_EVENT_TIME_UPTIME_NS = "touch_event_time_uptime_ns"
    const val TIMESTAMP_PRECISION = "timestamp_precision"
    const val COMMON_TIMESTAMP_NS = "common_timestamp_ns"
    const val APP_RECEIPT_TIME_UPTIME_NS = "app_receipt_time_uptime_ns"
    const val APP_RECEIPT_UPTIME_PRECISION = "app_receipt_uptime_precision"
    const val APP_RECEIPT_TIME_ELAPSED_NS = "app_receipt_time_elapsed_ns"
    const val RELATIVE_TIME_MS = "relative_time_ms"
    const val X = "x"
    const val Y = "y"
    const val TOUCH_MAJOR = "touch_major"
    const val TOUCH_MINOR = "touch_minor"
    const val SIZE = "size"
    const val PRESSURE = "pressure"
    const val ORIENTATION = "orientation"
    const val TOOL_TYPE = "tool_type"
    const val IS_HISTORICAL = "is_historical"
    const val HISTORY_INDEX = "history_index"

    // --- сессия и устройство ---
    const val STARTED_AT_WALL_CLOCK_MS = "started_at_wall_clock_ms"
    const val ENDED_AT_WALL_CLOCK_MS = "ended_at_wall_clock_ms"
    const val PHONE_SUPPORT_MODE = "phone_support_mode"
    const val MANUFACTURER = "manufacturer"
    const val MODEL = "model"
    const val ANDROID_VERSION = "android_version"
    const val SDK_INT = "sdk_int"
    const val APP_VERSION = "app_version"
    const val AAR_VERSION = "aar_version"
}
