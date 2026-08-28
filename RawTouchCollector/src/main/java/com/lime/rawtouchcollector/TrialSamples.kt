package com.lime.rawtouchcollector

/**
 * Отсчёты одной попытки.
 *
 * Хранятся в параллельных примитивных массивах, а не списком объектов: горячий путь
 * в onTouchEvent не должен аллоцировать по объекту на отсчёт при частоте в сотни
 * событий в секунду. Наружу отдаются поштучные геттеры, чтобы копия массивов
 * не создавалась на каждое чтение.
 *
 * Значения сохранены ровно так, как их вернул Android: без прореживания, сглаживания
 * и отбрасывания повторов, включая 0, 1 и совпадающие touchMajor/touchMinor.
 */
public class TrialSamples internal constructor(
    private val eventAction: IntArray,
    private val pointerId: IntArray,
    private val pointerIndex: IntArray,
    private val actionIndex: IntArray,
    private val pointerCount: IntArray,
    private val toolType: IntArray,
    private val historyIndex: IntArray,
    private val eventTimeUptimeMs: LongArray,
    private val eventTimeUptimeNs: LongArray,
    private val commonTimestampNs: LongArray,
    private val appReceiptTimeUptimeNs: LongArray,
    private val appReceiptTimeElapsedNs: LongArray,
    private val x: FloatArray,
    private val y: FloatArray,
    private val touchMajor: FloatArray,
    private val touchMinor: FloatArray,
    private val size: FloatArray,
    private val pressure: FloatArray,
    private val orientation: FloatArray,
    /** Время ACTION_DOWN этой попытки; база для relativeTimeMs. */
    private val downEventTimeUptimeNs: Long,
) {
    public val count: Int get() = eventAction.size

    public fun eventAction(i: Int): Int = eventAction[i]
    public fun pointerId(i: Int): Int = pointerId[i]
    public fun pointerIndex(i: Int): Int = pointerIndex[i]
    public fun actionIndex(i: Int): Int = actionIndex[i]
    public fun pointerCount(i: Int): Int = pointerCount[i]
    public fun toolType(i: Int): Int = toolType[i]

    /** Индекс historical sample внутри родительского MotionEvent; -1 у текущего отсчёта. */
    public fun historyIndex(i: Int): Int = historyIndex[i]
    public fun isHistorical(i: Int): Boolean = historyIndex[i] >= 0

    public fun eventTimeUptimeMs(i: Int): Long = eventTimeUptimeMs[i]
    public fun eventTimeUptimeNs(i: Int): Long = eventTimeUptimeNs[i]
    public fun commonTimestampNs(i: Int): Long = commonTimestampNs[i]
    public fun appReceiptTimeUptimeNs(i: Int): Long = appReceiptTimeUptimeNs[i]
    public fun appReceiptTimeElapsedNs(i: Int): Long = appReceiptTimeElapsedNs[i]

    /** Время относительно ACTION_DOWN, §6.4. Считается по времени события, не callback. */
    public fun relativeTimeMs(i: Int): Double =
        (eventTimeUptimeNs[i] - downEventTimeUptimeNs) / 1_000_000.0

    public fun x(i: Int): Float = x[i]
    public fun y(i: Int): Float = y[i]
    public fun touchMajor(i: Int): Float = touchMajor[i]
    public fun touchMinor(i: Int): Float = touchMinor[i]
    public fun size(i: Int): Float = size[i]
    public fun pressure(i: Int): Float = pressure[i]
    public fun orientation(i: Int): Float = orientation[i]
}
