package com.lime.rawtouchcollector.internal.time

import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.lime.rawtouchcollector.ClockSyncPoint
import com.lime.rawtouchcollector.Precision
import com.lime.rawtouchcollector.SyncMethod

/**
 * Снятие точки синхронизации шкал uptime и elapsedRealtime.
 *
 * Задача — найти offset, чтобы переводить время касания в общую шкалу:
 * `common_timestamp_ns = touch_event_time_uptime_ns + offset`.
 *
 * Обе шкалы считают от загрузки, но uptime останавливается в глубоком сне, а
 * elapsedRealtime — нет. Поэтому offset не константа: он растёт скачками при каждом
 * засыпании, и точку переснимают перед каждой попыткой.
 *
 * На API 35+ достаточно прочитать наносекундный uptime между двумя чтениями elapsed.
 * Ниже API 35 доступен только `uptimeMillis()`, усекающий значение до целых
 * миллисекунд, — и неизвестная дробная часть до 1 мс целиком ушла бы в offset.
 * Поэтому там используется приём с границей миллисекунды: вместо чтения в
 * произвольный момент мы ловим момент перещёлкивания счётчика, в который истинное
 * uptime заведомо равно целому числу миллисекунд. Квантование снимается измерением,
 * а не оценкой.
 */
internal object ClockSyncCapture {

    private const val MS_TO_NS = 1_000_000L

    /** Предел ожидания щелчка. Тик приходит не позже чем через 1 мс, запас — на вытеснение. */
    private const val MAX_SPIN_NS = 4L * MS_TO_NS

    /** Вилка шире этого значения означает, что поток вытеснили и момент упущен. */
    private const val MAX_BRACKET_NS = 50_000L

    /**
     * Сколько раз пробовать поймать границу, прежде чем откатиться.
     *
     * Замер на реальном устройстве (SM-A525F, API 34): одиночная попытка срывается
     * примерно в 15% случаев — планировщик вытесняет поток посреди спина. Повтор
     * стоит ещё до 1 мс в момент, когда палец экрана не касается, и снижает долю
     * огрублённых точек синхронизации до долей процента.
     */
    private const val MAX_ATTEMPTS = 3

    fun capture(clockSyncId: String, sessionId: String): ClockSyncPoint =
        if (Build.VERSION.SDK_INT >= 35) {
            captureWithUptimeNanos(clockSyncId, sessionId)
        } else {
            captureAtMillisecondBoundary(clockSyncId, sessionId)
        }

    /** API 35+: наносекундный uptime, квантования нет. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun captureWithUptimeNanos(clockSyncId: String, sessionId: String): ClockSyncPoint {
        val beforeNs = SystemClock.elapsedRealtimeNanos()
        val uptimeNs = SystemClock.uptimeNanos()
        val afterNs = SystemClock.elapsedRealtimeNanos()

        val elapsedMid = beforeNs + (afterNs - beforeNs) / 2
        return ClockSyncPoint(
            clockSyncId = clockSyncId,
            sessionId = sessionId,
            uptimeTimestampNs = uptimeNs,
            elapsedRealtimeTimestampNs = elapsedMid,
            offsetNs = elapsedMid - uptimeNs,
            samplingUncertaintyNs = afterNs - beforeNs,
            quantizationUncertaintyNs = 0L,
            syncMethod = SyncMethod.UPTIME_NANOS,
            uptimeMeasurementPrecision = Precision.NANOSECONDS,
        )
    }

    /**
     * API < 35: ловим перещёлкивание uptimeMillis().
     *
     * В момент, когда счётчик сменил значение с N на N+1, истинное uptime равно
     * ровно (N+1) миллисекунде — неизвестной дробной части в этот миг нет. Читаем
     * elapsedRealtimeNanos рядом с этим моментом, и вилка между двумя его чтениями
     * становится единственной погрешностью.
     *
     * Цена — активное ожидание до 1 мс. Точка снимается перед попыткой, когда палец
     * ещё не коснулся экрана, поэтому на запись касаний это не влияет.
     */
    private fun captureAtMillisecondBoundary(
        clockSyncId: String,
        sessionId: String,
    ): ClockSyncPoint {
        repeat(MAX_ATTEMPTS) {
            val point = tryCatchBoundary(clockSyncId, sessionId)
            if (point != null) return point
        }
        return capturePlain(clockSyncId, sessionId)
    }

    /** Одна попытка поймать границу. null означает, что момент упущен. */
    private fun tryCatchBoundary(clockSyncId: String, sessionId: String): ClockSyncPoint? {
        val spinStartNs = SystemClock.elapsedRealtimeNanos()
        val startMs = SystemClock.uptimeMillis()

        var beforeNs = spinStartNs
        var tickMs: Long
        var afterNs: Long

        while (true) {
            tickMs = SystemClock.uptimeMillis()
            afterNs = SystemClock.elapsedRealtimeNanos()
            if (tickMs != startMs) break
            if (afterNs - spinStartNs > MAX_SPIN_NS) return null
            beforeNs = afterNs
        }

        // Щелчок должен быть ровно на одну миллисекунду, а вилка — узкой.
        // Иначе поток вытеснили, момент упущен, и притворяться точным нельзя.
        if (tickMs != startMs + 1) return null
        if (afterNs - beforeNs > MAX_BRACKET_NS) return null

        val elapsedAtTick = beforeNs + (afterNs - beforeNs) / 2
        val uptimeAtTick = tickMs * MS_TO_NS

        return ClockSyncPoint(
            clockSyncId = clockSyncId,
            sessionId = sessionId,
            uptimeTimestampNs = uptimeAtTick,
            elapsedRealtimeTimestampNs = elapsedAtTick,
            offsetNs = elapsedAtTick - uptimeAtTick,
            samplingUncertaintyNs = afterNs - beforeNs,
            quantizationUncertaintyNs = 0L,
            syncMethod = SyncMethod.MS_BOUNDARY,
            uptimeMeasurementPrecision = Precision.MILLISECONDS,
        )
    }

    /**
     * Откат: обычное чтение без ловли границы.
     *
     * Значение uptime усечено вниз, поэтому offset завышен на неизвестную величину
     * в диапазоне [0, 1) мс. Ошибка односторонняя, и она объявлена целиком:
     * quantizationUncertaintyNs = 1 000 000.
     */
    private fun capturePlain(clockSyncId: String, sessionId: String): ClockSyncPoint {
        val beforeNs = SystemClock.elapsedRealtimeNanos()
        val uptimeMs = SystemClock.uptimeMillis()
        val afterNs = SystemClock.elapsedRealtimeNanos()

        val elapsedMid = beforeNs + (afterNs - beforeNs) / 2
        val uptimeNs = uptimeMs * MS_TO_NS

        return ClockSyncPoint(
            clockSyncId = clockSyncId,
            sessionId = sessionId,
            uptimeTimestampNs = uptimeNs,
            elapsedRealtimeTimestampNs = elapsedMid,
            offsetNs = elapsedMid - uptimeNs,
            samplingUncertaintyNs = afterNs - beforeNs,
            quantizationUncertaintyNs = MS_TO_NS,
            syncMethod = SyncMethod.MS_PLAIN,
            uptimeMeasurementPrecision = Precision.MILLISECONDS,
        )
    }
}
