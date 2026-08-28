package com.lime.rawtouchcollector.internal.time

import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import com.lime.rawtouchcollector.Precision

/**
 * Чтение времени с честной маркировкой точности.
 *
 * Два наносекундных API появились на РАЗНЫХ уровнях:
 *
 * - `MotionEvent.getEventTimeNanos()` — API 34;
 * - `SystemClock.uptimeNanos()`       — API 35.
 *
 * Поэтому на API 34 время касания уже наносекундное, а время получения события —
 * ещё миллисекундное. Ниже API 34 миллисекундное значение умножается на 1 000 000
 * только ради единой единицы хранения: это преобразование единиц, а не повышение
 * точности, и признак точности остаётся MILLISECONDS.
 */
internal object TimeSource {

    private const val MS_TO_NS = 1_000_000L

    /** Точность времени касания на этом устройстве. */
    val eventTimePrecision: String =
        if (Build.VERSION.SDK_INT >= 34) Precision.NANOSECONDS else Precision.MILLISECONDS

    /** Точность receipt-метки в шкале uptime на этом устройстве. */
    val receiptUptimePrecision: String =
        if (Build.VERSION.SDK_INT >= 35) Precision.NANOSECONDS else Precision.MILLISECONDS

    /**
     * Время получения родительского MotionEvent приложением, в той же шкале uptime,
     * что и eventTime, §6.4. Снимается один раз на входе в обработку, до чтения полей.
     */
    fun receiptUptimeNs(): Long =
        if (Build.VERSION.SDK_INT >= 35) {
            SystemClock.uptimeNanos()
        } else {
            SystemClock.uptimeMillis() * MS_TO_NS
        }

    /** Время текущего отсчёта в шкале uptime. */
    fun eventTimeNs(event: MotionEvent): Long =
        if (Build.VERSION.SDK_INT >= 34) {
            event.eventTimeNanos
        } else {
            event.eventTime * MS_TO_NS
        }

    /** Время historical sample в шкале uptime. */
    fun historicalEventTimeNs(event: MotionEvent, pos: Int): Long =
        if (Build.VERSION.SDK_INT >= 34) {
            event.getHistoricalEventTimeNanos(pos)
        } else {
            event.getHistoricalEventTime(pos) * MS_TO_NS
        }

    /** То же время в миллисекундах — как его отдаёт исходный API. */
    fun eventTimeMs(event: MotionEvent): Long = event.eventTime

    fun historicalEventTimeMs(event: MotionEvent, pos: Int): Long =
        event.getHistoricalEventTime(pos)
}
