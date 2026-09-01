package com.lime.rawtouchcollector

/**
 * Имена полей схемы — единственное место, где они существуют как строки.
 *
 * Эти же имена используют CSV-экспорт приложения и validate_export.py, поэтому объект
 * публичный: иначе слой экспорта завёл бы вторую копию имён, и схема начала бы
 * расходиться сама с собой. Java-совместим — `SchemaFields.TRIAL_ID` вызывается напрямую.
 *
 * Описание полей — docs/schema-stage1.md.
 */
public object SchemaFields {

    // --- попытка ---
    public const val SCHEMA_VERSION: String = "schema_version"
    public const val TRIAL_ID: String = "trial_id"
    public const val SESSION_ID: String = "session_id"
    public const val PARTICIPANT_ID: String = "participant_id"
    public const val TRIAL_INDEX: String = "trial_index"
    public const val TASK_GROUP: String = "task_group"
    public const val SCENARIO_TYPE: String = "scenario_type"
    public const val TOUCH_DOWN_COMMON_TIMESTAMP_NS: String = "touch_down_common_timestamp_ns"
    public const val TOUCH_UP_COMMON_TIMESTAMP_NS: String = "touch_up_common_timestamp_ns"
    public const val CONTACT_DURATION_NS: String = "contact_duration_ns"
    public const val COMPLETION_STATUS: String = "completion_status"
    public const val CURRENT_SAMPLE_COUNT: String = "current_sample_count"
    public const val HISTORICAL_SAMPLE_COUNT: String = "historical_sample_count"
    public const val SECOND_POINTER_OBSERVED: String = "second_pointer_observed"

    // --- профиль дисплея ---
    public const val DISPLAY_PROFILE: String = "display_profile"
    public const val DISPLAY_PROFILE_ID: String = "display_profile_id"
    public const val WINDOW_WIDTH_PX: String = "window_width_px"
    public const val WINDOW_HEIGHT_PX: String = "window_height_px"
    public const val DISPLAY_MODE_WIDTH_PX: String = "display_mode_width_px"
    public const val DISPLAY_MODE_HEIGHT_PX: String = "display_mode_height_px"
    public const val DISPLAY_REFRESH_RATE_HZ: String = "display_refresh_rate_hz"
    public const val DENSITY_DPI: String = "density_dpi"
    public const val CAPTURED_AT_ELAPSED_NS: String = "captured_at_elapsed_ns"

    // --- точка синхронизации ---
    public const val CLOCK_SYNC: String = "clock_sync"
    public const val CLOCK_SYNC_ID: String = "clock_sync_id"
    public const val UPTIME_TIMESTAMP_NS: String = "uptime_timestamp_ns"
    public const val ELAPSED_REALTIME_TIMESTAMP_NS: String = "elapsed_realtime_timestamp_ns"
    public const val OFFSET_NS: String = "offset_ns"
    public const val SYNC_SAMPLING_UNCERTAINTY_NS: String = "sync_sampling_uncertainty_ns"
    public const val SYNC_QUANTIZATION_UNCERTAINTY_NS: String = "sync_quantization_uncertainty_ns"
    public const val SYNC_METHOD: String = "sync_method"
    public const val UPTIME_MEASUREMENT_PRECISION: String = "uptime_measurement_precision"

    // --- отсчёты ---
    public const val SAMPLES: String = "samples"
    public const val SAMPLE_INDEX: String = "sample_index"
    public const val EVENT_ACTION: String = "event_action"
    public const val POINTER_ID: String = "pointer_id"
    public const val POINTER_INDEX: String = "pointer_index"
    public const val ACTION_INDEX: String = "action_index"
    public const val POINTER_COUNT: String = "pointer_count"
    public const val TOUCH_EVENT_TIME_UPTIME_MS: String = "touch_event_time_uptime_ms"
    public const val TOUCH_EVENT_TIME_UPTIME_NS: String = "touch_event_time_uptime_ns"
    public const val TIMESTAMP_PRECISION: String = "timestamp_precision"
    public const val COMMON_TIMESTAMP_NS: String = "common_timestamp_ns"
    public const val APP_RECEIPT_TIME_UPTIME_NS: String = "app_receipt_time_uptime_ns"
    public const val APP_RECEIPT_UPTIME_PRECISION: String = "app_receipt_uptime_precision"
    public const val APP_RECEIPT_TIME_ELAPSED_NS: String = "app_receipt_time_elapsed_ns"
    public const val RELATIVE_TIME_MS: String = "relative_time_ms"
    public const val X: String = "x"
    public const val Y: String = "y"
    public const val TOUCH_MAJOR: String = "touch_major"
    public const val TOUCH_MINOR: String = "touch_minor"
    public const val SIZE: String = "size"
    public const val PRESSURE: String = "pressure"
    public const val ORIENTATION: String = "orientation"
    public const val TOOL_TYPE: String = "tool_type"
    public const val IS_HISTORICAL: String = "is_historical"
    public const val HISTORY_INDEX: String = "history_index"

    // --- сессия и устройство ---
    public const val DEVICE_ID: String = "device_id"
    public const val SESSION_STATUS: String = "session_status"
    public const val STARTED_AT_WALL_CLOCK_MS: String = "started_at_wall_clock_ms"
    public const val ENDED_AT_WALL_CLOCK_MS: String = "ended_at_wall_clock_ms"
    public const val PHONE_SUPPORT_MODE: String = "phone_support_mode"
    public const val MANUFACTURER: String = "manufacturer"
    public const val MODEL: String = "model"
    public const val ANDROID_VERSION: String = "android_version"
    public const val SDK_INT: String = "sdk_int"
    public const val APP_VERSION: String = "app_version"
    public const val AAR_VERSION: String = "aar_version"
}
