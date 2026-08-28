package com.lime.rawtouchcollector.internal.capture

import com.lime.rawtouchcollector.TrialSamples

/**
 * Оперативный буфер отсчётов активной попытки.
 *
 * Параллельные примитивные массивы вместо списка объектов.
 * В onTouchEvent разрешены только лёгкие операции - до 25 полей на каждый
 * из сотен отсчётов в секунду. Объект на отсчёт дал бы аллокацию и GC-паузы прямо
 * на главном потоке.
 *
 * Буфер живёт весь сеанс и переиспользуется между попытками: [reset] обнуляет только
 * размер, массивы остаются выделенными. Владелец - только главный поток.
 * Наружу уходит [toSamples] — копии по фактическому размеру, поэтому снимок не
 * ссылается на переиспользуемую память и безопасен на worker-потоке.
 */
internal class SampleBuffer(initialCapacity: Int = 256) {

    private var capacity = initialCapacity

    private var eventAction = IntArray(capacity)
    private var pointerId = IntArray(capacity)
    private var pointerIndex = IntArray(capacity)
    private var actionIndex = IntArray(capacity)
    private var pointerCount = IntArray(capacity)
    private var toolType = IntArray(capacity)
    private var historyIndex = IntArray(capacity)

    private var eventTimeUptimeMs = LongArray(capacity)
    private var eventTimeUptimeNs = LongArray(capacity)
    private var commonTimestampNs = LongArray(capacity)
    private var appReceiptTimeUptimeNs = LongArray(capacity)
    private var appReceiptTimeElapsedNs = LongArray(capacity)

    private var x = FloatArray(capacity)
    private var y = FloatArray(capacity)
    private var touchMajor = FloatArray(capacity)
    private var touchMinor = FloatArray(capacity)
    private var size = FloatArray(capacity)
    private var pressure = FloatArray(capacity)
    private var orientation = FloatArray(capacity)

    var count: Int = 0
        private set

    var currentSampleCount: Int = 0
        private set

    var historicalSampleCount: Int = 0
        private set

    @Suppress("LongParameterList")
    fun append(
        eventAction: Int,
        pointerId: Int,
        pointerIndex: Int,
        actionIndex: Int,
        pointerCount: Int,
        toolType: Int,
        historyIndex: Int,
        eventTimeUptimeMs: Long,
        eventTimeUptimeNs: Long,
        commonTimestampNs: Long,
        appReceiptTimeUptimeNs: Long,
        appReceiptTimeElapsedNs: Long,
        x: Float,
        y: Float,
        touchMajor: Float,
        touchMinor: Float,
        size: Float,
        pressure: Float,
        orientation: Float,
    ) {
        if (count == capacity) grow()

        val i = count
        this.eventAction[i] = eventAction
        this.pointerId[i] = pointerId
        this.pointerIndex[i] = pointerIndex
        this.actionIndex[i] = actionIndex
        this.pointerCount[i] = pointerCount
        this.toolType[i] = toolType
        this.historyIndex[i] = historyIndex
        this.eventTimeUptimeMs[i] = eventTimeUptimeMs
        this.eventTimeUptimeNs[i] = eventTimeUptimeNs
        this.commonTimestampNs[i] = commonTimestampNs
        this.appReceiptTimeUptimeNs[i] = appReceiptTimeUptimeNs
        this.appReceiptTimeElapsedNs[i] = appReceiptTimeElapsedNs
        this.x[i] = x
        this.y[i] = y
        this.touchMajor[i] = touchMajor
        this.touchMinor[i] = touchMinor
        this.size[i] = size
        this.pressure[i] = pressure
        this.orientation[i] = orientation

        count = i + 1
        if (historyIndex >= 0) historicalSampleCount++ else currentSampleCount++
    }

    fun eventTimeUptimeNsAt(i: Int): Long = eventTimeUptimeNs[i]

    /**
     * Копия по фактическому размеру. Одна на попытку, не на отсчёт: горячий путь
     * остаётся без аллокаций, а снимок получает собственную память и переживает
     * переиспользование буфера следующей попыткой.
     */
    fun toSamples(downEventTimeUptimeNs: Long): TrialSamples = TrialSamples(
        eventAction = eventAction.copyOf(count),
        pointerId = pointerId.copyOf(count),
        pointerIndex = pointerIndex.copyOf(count),
        actionIndex = actionIndex.copyOf(count),
        pointerCount = pointerCount.copyOf(count),
        toolType = toolType.copyOf(count),
        historyIndex = historyIndex.copyOf(count),
        eventTimeUptimeMs = eventTimeUptimeMs.copyOf(count),
        eventTimeUptimeNs = eventTimeUptimeNs.copyOf(count),
        commonTimestampNs = commonTimestampNs.copyOf(count),
        appReceiptTimeUptimeNs = appReceiptTimeUptimeNs.copyOf(count),
        appReceiptTimeElapsedNs = appReceiptTimeElapsedNs.copyOf(count),
        x = x.copyOf(count),
        y = y.copyOf(count),
        touchMajor = touchMajor.copyOf(count),
        touchMinor = touchMinor.copyOf(count),
        size = size.copyOf(count),
        pressure = pressure.copyOf(count),
        orientation = orientation.copyOf(count),
        downEventTimeUptimeNs = downEventTimeUptimeNs,
    )

    fun reset() {
        count = 0
        currentSampleCount = 0
        historicalSampleCount = 0
    }

    private fun grow() {
        val newCapacity = capacity * 2

        eventAction = eventAction.copyOf(newCapacity)
        pointerId = pointerId.copyOf(newCapacity)
        pointerIndex = pointerIndex.copyOf(newCapacity)
        actionIndex = actionIndex.copyOf(newCapacity)
        pointerCount = pointerCount.copyOf(newCapacity)
        toolType = toolType.copyOf(newCapacity)
        historyIndex = historyIndex.copyOf(newCapacity)

        eventTimeUptimeMs = eventTimeUptimeMs.copyOf(newCapacity)
        eventTimeUptimeNs = eventTimeUptimeNs.copyOf(newCapacity)
        commonTimestampNs = commonTimestampNs.copyOf(newCapacity)
        appReceiptTimeUptimeNs = appReceiptTimeUptimeNs.copyOf(newCapacity)
        appReceiptTimeElapsedNs = appReceiptTimeElapsedNs.copyOf(newCapacity)

        x = x.copyOf(newCapacity)
        y = y.copyOf(newCapacity)
        touchMajor = touchMajor.copyOf(newCapacity)
        touchMinor = touchMinor.copyOf(newCapacity)
        size = size.copyOf(newCapacity)
        pressure = pressure.copyOf(newCapacity)
        orientation = orientation.copyOf(newCapacity)

        capacity = newCapacity
    }
}
