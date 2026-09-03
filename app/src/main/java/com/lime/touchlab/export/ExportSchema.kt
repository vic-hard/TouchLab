package com.lime.touchlab.export

import android.util.JsonWriter
import com.lime.rawtouchcollector.Schema
import com.lime.rawtouchcollector.SchemaFields
import java.io.StringWriter

/**
 * Состав CSV-файлов архива — единственное объявление, из которого берутся и шапка
 * каждого файла, и `schema.json`.
 */
internal class Column(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val unit: String? = null,
    val note: String? = null,
)

internal class FileSpec(
    val fileName: String,
    val description: String,
    val columns: List<Column>,
)

internal object ExportSchema {

    private const val STRING = "string"
    private const val INT = "int"
    private const val LONG = "long"
    private const val FLOAT = "float"
    private const val DOUBLE = "double"
    private const val BOOL = "bool"

    private const val PX = "px"
    private const val MS = "ms"
    private const val NS = "ns"

    private const val COUNTER_NULL = "null — сессия не была закрыта штатно, счётчик неизвестен"

    val DEVICES: FileSpec = FileSpec(
        "devices.csv",
        "устройство: производитель, модель, версии Android, приложения и AAR",
        listOf(
            Column(SchemaFields.DEVICE_ID, STRING, note = "UUID, выведен из полей устройства"),
            Column(SchemaFields.MANUFACTURER, STRING),
            Column(SchemaFields.MODEL, STRING),
            Column(SchemaFields.ANDROID_VERSION, STRING),
            Column(SchemaFields.SDK_INT, INT),
            Column(SchemaFields.APP_VERSION, STRING),
            Column(SchemaFields.AAR_VERSION, STRING),
            Column(SchemaFields.DENSITY_DPI, INT, unit = "dpi"),
        ),
    )

    val DISPLAY_PROFILES: FileSpec = FileSpec(
        "display_profiles.csv",
        "профили дисплея, на которые ссылаются попытки сессии",
        listOf(
            Column(SchemaFields.DISPLAY_PROFILE_ID, STRING, note = "UUID"),
            Column(
                SchemaFields.WINDOW_WIDTH_PX, INT, unit = PX,
                note = "окно приложения, не экран",
            ),
            Column(SchemaFields.WINDOW_HEIGHT_PX, INT, unit = PX),
            Column(SchemaFields.DISPLAY_MODE_WIDTH_PX, INT, unit = PX),
            Column(SchemaFields.DISPLAY_MODE_HEIGHT_PX, INT, unit = PX),
            Column(
                SchemaFields.DISPLAY_REFRESH_RATE_HZ, FLOAT, unit = "Hz",
                note = "НЕ частота тач-контроллера, критерий 22",
            ),
            Column(SchemaFields.DENSITY_DPI, INT, unit = "dpi"),
            Column(
                SchemaFields.CAPTURED_AT_ELAPSED_NS, LONG, unit = NS,
                note = "момент фиксации профиля в шкале elapsedRealtimeNanos",
            ),
        ),
    )

    val SESSIONS: FileSpec = FileSpec(
        "sessions.csv",
        "сессия; ended_at_wall_clock_ms пуст только у незавершённой",
        listOf(
            Column(SchemaFields.SESSION_ID, STRING, note = "UUID"),
            Column(SchemaFields.DEVICE_ID, STRING),
            Column(SchemaFields.PARTICIPANT_ID, STRING, note = "произвольная строка, не UUID"),
            Column(
                SchemaFields.STARTED_AT_WALL_CLOCK_MS, LONG, unit = "ms от эпохи Unix",
                note = "календарное время, хранится отдельно от uptime и elapsedRealtime",
            ),
            Column(
                SchemaFields.ENDED_AT_WALL_CLOCK_MS, LONG, nullable = true,
                unit = "ms от эпохи Unix",
                note = "null только у незавершённой сессии, критерий 18",
            ),
            Column(SchemaFields.PHONE_SUPPORT_MODE, STRING, note = "этап 1 — всегда HAND"),
            Column(
                SchemaFields.CLOCK_SYNC_ID, STRING, nullable = true,
                note = "точка синхронизации, снятая при старте сессии",
            ),
            Column(
                SchemaFields.SESSION_STATUS, STRING,
                note = "COMPLETED / INCOMPLETE / ACTIVE; INCOMPLETE — процесс был убит",
            ),
            Column(SchemaFields.SCHEMA_VERSION, STRING),

            Column(
                SchemaFields.TRIALS_ACCEPTED, LONG, nullable = true,
                note = "попытки, принятые в очередь фоновой обработки. " + COUNTER_NULL,
            ),
            Column(
                SchemaFields.TRIALS_CONFIRMED, LONG, nullable = true,
                note = "попытки, фиксацию которых подтвердило хранилище; " +
                    "именно столько строк должно быть в trials.csv. " + COUNTER_NULL,
            ),
            Column(
                SchemaFields.QUEUE_OVERFLOWS, LONG, nullable = true,
                note = "попытки, потерянные из-за переполнения очереди; норма 0. " + COUNTER_NULL,
            ),
            Column(
                SchemaFields.WRITE_FAILURES, LONG, nullable = true,
                note = "попытки, которые хранилище не смогло записать; норма 0. " + COUNTER_NULL,
            ),
            Column(
                SchemaFields.EVENTS_BEFORE_START, LONG, nullable = true,
                note = "события до ACTION_DOWN попытки; норма 0. " + COUNTER_NULL,
            ),
            Column(
                SchemaFields.EVENTS_AFTER_END, LONG, nullable = true,
                note = "события после терминального события попытки; норма 0. " + COUNTER_NULL,
            ),
            Column(
                SchemaFields.EVENTS_AFTER_SESSION_CLOSE, LONG, nullable = true,
                note = "события после закрытия сессии; норма 0. " + COUNTER_NULL,
            ),
            Column(
                SchemaFields.EVENTS_DISCARDED_AFTER_MULTITOUCH, LONG, nullable = true,
                note = "события прерванного жеста после MULTITOUCH_ERROR; " +
                    "ненулевое значение — норма, §8. " + COUNTER_NULL,
            ),
            Column(
                SchemaFields.IMPLICIT_CANCELS, LONG, nullable = true,
                note = "startTrial поверх незакрытой попытки; норма 0. " + COUNTER_NULL,
            ),
            Column(
                SchemaFields.MULTITOUCH_ERRORS, LONG, nullable = true,
                note = "попытки, прерванные вторым пальцем. " + COUNTER_NULL,
            ),
            Column(
                SchemaFields.TRIALS_WITH_STALE_DISPLAY_PROFILE, LONG, nullable = true,
                note = "попытки без обновления профиля дисплея перед стартом; норма 0. " +
                    COUNTER_NULL,
            ),
            Column(
                SchemaFields.CLOCK_SYNC_FALLBACKS, LONG, nullable = true,
                note = "точки синхронизации, огрублённые до MS_PLAIN; норма 0. " + COUNTER_NULL,
            ),
        ),
    )

    val CLOCK_SYNC: FileSpec = FileSpec(
        "clock_sync.csv",
        "точки синхронизации шкал uptime и elapsedRealtime",
        listOf(
            Column(SchemaFields.CLOCK_SYNC_ID, STRING, note = "UUID"),
            Column(SchemaFields.SESSION_ID, STRING),
            Column(SchemaFields.UPTIME_TIMESTAMP_NS, LONG, unit = NS),
            Column(SchemaFields.ELAPSED_REALTIME_TIMESTAMP_NS, LONG, unit = NS),
            Column(
                SchemaFields.OFFSET_NS, LONG, unit = NS,
                note = "elapsed − uptime; common_timestamp_ns = uptime + offset_ns",
            ),
            Column(
                SchemaFields.SYNC_SAMPLING_UNCERTAINTY_NS, LONG, unit = NS,
                note = "ширина вилки между двумя чтениями таймеров; всегда > 0",
            ),
            Column(
                SchemaFields.SYNC_QUANTIZATION_UNCERTAINTY_NS, LONG, unit = NS,
                note = "грубость самого uptime: 0 при UPTIME_NANOS и MS_BOUNDARY, " +
                    "1 000 000 при MS_PLAIN",
            ),
            Column(
                SchemaFields.SYNC_METHOD, STRING,
                note = "UPTIME_NANOS / MS_BOUNDARY / MS_PLAIN",
            ),
            Column(
                SchemaFields.UPTIME_MEASUREMENT_PRECISION, STRING,
                note = "фактическая точность измерения uptime",
            ),
        ),
    )

    val TRIALS: FileSpec = FileSpec(
        "trials.csv",
        "завершённые попытки; одна строка — один контакт от DOWN до UP/CANCEL/POINTER_DOWN",
        listOf(
            Column(SchemaFields.TRIAL_ID, STRING, note = "UUID"),
            Column(SchemaFields.SESSION_ID, STRING),
            Column(SchemaFields.PARTICIPANT_ID, STRING),
            Column(
                SchemaFields.TRIAL_INDEX, INT,
                note = "порядковый номер внутри сессии, с 1; идентификатором не является",
            ),
            Column(SchemaFields.TASK_GROUP, STRING, note = "этап 1 — TAP"),
            Column(SchemaFields.SCENARIO_TYPE, STRING, note = "этап 1 — STAGE1_TAP"),
            Column(
                SchemaFields.DISPLAY_PROFILE_ID, STRING,
                note = "профиль, действовавший непосредственно перед попыткой",
            ),
            Column(SchemaFields.CLOCK_SYNC_ID, STRING, note = "точка, снятая перед попыткой"),
            Column(
                SchemaFields.TOUCH_DOWN_COMMON_TIMESTAMP_NS, LONG, unit = NS,
                note = "шкала elapsedRealtimeNanos",
            ),
            Column(SchemaFields.TOUCH_UP_COMMON_TIMESTAMP_NS, LONG, unit = NS),
            Column(
                SchemaFields.CONTACT_DURATION_NS, LONG, unit = NS,
                note = "по времени события, не по времени обработки callback",
            ),
            Column(
                SchemaFields.COMPLETION_STATUS, STRING,
                note = "UP / CANCEL / MULTITOUCH_ERROR",
            ),
            Column(SchemaFields.CURRENT_SAMPLE_COUNT, INT),
            Column(SchemaFields.HISTORICAL_SAMPLE_COUNT, INT),
            Column(SchemaFields.SECOND_POINTER_OBSERVED, BOOL),
            Column(
                SchemaFields.TIMESTAMP_PRECISION, STRING,
                note = "NANOSECONDS только при фактическом использовании nanos-методов, API 34+",
            ),
            Column(
                SchemaFields.APP_RECEIPT_UPTIME_PRECISION, STRING,
                note = "NANOSECONDS только при SystemClock.uptimeNanos(), API 35+",
            ),
            Column(SchemaFields.SCHEMA_VERSION, STRING),
        ),
    )

    val TOUCH_SAMPLES: FileSpec = FileSpec(
        "touch_samples.csv",
        "отсчёты; historical sample — самостоятельная строка со своим временем",
        listOf(
            Column(SchemaFields.TRIAL_ID, STRING),
            Column(SchemaFields.SAMPLE_INDEX, INT, note = "порядковый номер внутри попытки, с 0"),
            Column(SchemaFields.EVENT_ACTION, INT, note = "MotionEvent.getActionMasked()"),
            Column(SchemaFields.POINTER_ID, INT),
            Column(SchemaFields.POINTER_INDEX, INT),
            Column(SchemaFields.ACTION_INDEX, INT),
            Column(SchemaFields.POINTER_COUNT, INT),
            Column(
                SchemaFields.TOUCH_EVENT_TIME_UPTIME_MS, LONG, unit = MS,
                note = "исходный eventTime / historicalEventTime",
            ),
            Column(
                SchemaFields.TOUCH_EVENT_TIME_UPTIME_NS, LONG, unit = NS,
                note = "API 34+ — nanos-методы; ниже — мс × 1e6 как преобразование единиц",
            ),
            Column(SchemaFields.TIMESTAMP_PRECISION, STRING, note = "постоянно внутри попытки"),
            Column(
                SchemaFields.COMMON_TIMESTAMP_NS, LONG, unit = NS,
                note = "touch_event_time_uptime_ns + offset_ns применённой clock_sync",
            ),
            Column(
                SchemaFields.APP_RECEIPT_TIME_UPTIME_NS, LONG, unit = NS,
                note = "та же база, что eventTime; годится для прямой разности",
            ),
            Column(
                SchemaFields.APP_RECEIPT_UPTIME_PRECISION, STRING,
                note = "постоянно внутри попытки",
            ),
            Column(
                SchemaFields.APP_RECEIPT_TIME_ELAPSED_NS, LONG, unit = NS,
                note = "шкала elapsedRealtimeNanos; разность с eventTime задержкой НЕ является",
            ),
            Column(SchemaFields.CLOCK_SYNC_ID, STRING, note = "постоянно внутри попытки"),
            Column(
                SchemaFields.RELATIVE_TIME_MS, DOUBLE, unit = MS,
                note = "от ACTION_DOWN этой попытки, по времени события; у DOWN — 0",
            ),
            Column(SchemaFields.X, FLOAT, unit = PX),
            Column(SchemaFields.Y, FLOAT, unit = PX),
            Column(SchemaFields.TOUCH_MAJOR, FLOAT, note = "сырое значение Android"),
            Column(SchemaFields.TOUCH_MINOR, FLOAT, note = "сырое значение Android"),
            Column(SchemaFields.SIZE, FLOAT, note = "сырое значение Android"),
            Column(
                SchemaFields.PRESSURE, FLOAT,
                note = "сырой канал; постоянство на конкретном устройстве не ошибка, критерий 33",
            ),
            Column(SchemaFields.ORIENTATION, FLOAT, unit = "rad"),
            Column(SchemaFields.TOOL_TYPE, INT),
            Column(SchemaFields.IS_HISTORICAL, BOOL),
            Column(
                SchemaFields.HISTORY_INDEX, INT, nullable = true,
                note = "индекс historical sample; null у текущего отсчёта — «неприменимо»",
            ),
        ),
    )

    /** Порядок файлов в архиве. */
    val FILES: List<FileSpec> = listOf(
        DEVICES, DISPLAY_PROFILES, SESSIONS, CLOCK_SYNC, TRIALS, TOUCH_SAMPLES,
    )

    /**
     * Правила, которые нельзя вывести из состава колонок, но которые обязан знать
     * получатель архива и `validate_export.py`.
     */
    private val NOTES: List<String> = listOf(
        "Пустая ячейка CSV означает null. У большинства полей это «неприменимо»; " +
            "у счётчиков sessions.csv — «неизвестно, сессия не закрыта штатно».",
        "Три временные шкалы хранятся раздельно и не выводятся одна из другой: " +
            "календарное время сессии, uptime (событие и получение) и elapsedRealtime.",
        "common_timestamp_ns = touch_event_time_uptime_ns + offset_ns соответствующей clock_sync.",
        "sync_quantization_uncertainty_ns == 0 влечёт sdk_int >= 35 ИЛИ sync_method == MS_BOUNDARY.",
        "sync_method == UPTIME_NANOS влечёт sdk_int >= 35.",
        "uptime_measurement_precision == NANOSECONDS влечёт sdk_int >= 35.",
        "timestamp_precision == NANOSECONDS влечёт sdk_int >= 34.",
        "display_refresh_rate_hz не является частотой тач-контроллера.",
        "Постоянный pressure или другой канал на конкретном устройстве сохраняется как есть " +
            "и ошибкой не считается.",
        "trials_confirmed равно числу строк в trials.csv. Расхождение с trials_accepted " +
            "объясняется queue_overflows и write_failures и означает, что попытка НЕ сохранена.",
        "Файлы в кодировке UTF-8 без BOM, перевод строки LF, экранирование по RFC 4180.",
    )

    /** `schema.json` — описание полей и версия схемы внутри архива. */
    fun schemaJson(): String {
        val out = StringWriter()
        JsonWriter(out).use { w ->
            w.setIndent("  ")
            w.beginObject()
            w.name("schema_version").value(Schema.VERSION)
            w.name("null_representation").value("пустая ячейка CSV")

            w.name("identifiers").beginObject()
            w.name(SchemaFields.SESSION_ID).value("UUID")
            w.name(SchemaFields.TRIAL_ID).value("UUID")
            w.name(SchemaFields.CLOCK_SYNC_ID).value("UUID")
            w.name(SchemaFields.DISPLAY_PROFILE_ID).value("UUID")
            w.name(SchemaFields.DEVICE_ID).value("UUID")
            w.name(SchemaFields.PARTICIPANT_ID).value("произвольная строка")
            w.endObject()

            w.name("files").beginArray()
            for (file in FILES) {
                w.beginObject()
                w.name("name").value(file.fileName)
                w.name("description").value(file.description)
                w.name("columns").beginArray()
                for (c in file.columns) {
                    w.beginObject()
                    w.name("name").value(c.name)
                    w.name("type").value(c.type)
                    w.name("nullable").value(c.nullable)
                    if (c.unit != null) w.name("unit").value(c.unit)
                    if (c.note != null) w.name("note").value(c.note)
                    w.endObject()
                }
                w.endArray()
                w.endObject()
            }
            w.endArray()

            w.name("notes").beginArray()
            for (note in NOTES) w.value(note)
            w.endArray()
            w.endObject()
        }
        return out.toString()
    }
}
