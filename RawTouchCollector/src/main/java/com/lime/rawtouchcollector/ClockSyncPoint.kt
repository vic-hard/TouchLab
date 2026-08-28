package com.lime.rawtouchcollector

/**
 * Точка синхронизации шкал uptime и elapsedRealtime.
 *
 * Погрешность разложена на два независимых слагаемых, потому что у них разная природа:
 *
 * - [samplingUncertaintyNs] — два таймера нельзя прочитать в один и тот же момент;
 *   это ширина интервала, внутри которого измерение произошло. Есть всегда, на любом API;
 * - [quantizationUncertaintyNs] — сам прочитанный uptime может быть грубым.
 *   `uptimeMillis()` усекает значение до целых миллисекунд, и неизвестная дробная часть
 *   до 1 мс целиком уходит в смещение. На API 35+ и при [SyncMethod.MS_BOUNDARY]
 *   это слагаемое равно нулю.
 *
 * Одно суммарное число здесь не хранится намеренно: по нему нельзя понять, измерение
 * было неточным или шкала была грубой. Складывать слагаемые для получения полной
 * границы ошибки — задача потребителя данных.
 */
public class ClockSyncPoint internal constructor(
    public val clockSyncId: String,
    public val sessionId: String,
    public val uptimeTimestampNs: Long,
    public val elapsedRealtimeTimestampNs: Long,
    public val offsetNs: Long,
    public val samplingUncertaintyNs: Long,
    public val quantizationUncertaintyNs: Long,
    public val syncMethod: String,
    public val uptimeMeasurementPrecision: String,
) {
    /** Перевод времени касания из шкалы uptime в общую шкалу elapsedRealtime. */
    public fun toCommonTimestampNs(uptimeNs: Long): Long = uptimeNs + offsetNs

    override fun toString(): String =
        "ClockSyncPoint(" + clockSyncId + ", method=" + syncMethod +
            ", offset=" + offsetNs + "ns, sampling=" + samplingUncertaintyNs +
            "ns, quantization=" + quantizationUncertaintyNs + "ns)"
}
