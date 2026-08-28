package com.lime.rawtouchcollector

/**
 * Неизменяемый снимок завершённой попытки.
 *
 * Создаётся на главном потоке в момент терминального события и с этого мгновения
 * не меняется: массивы отсчётов скопированы по фактическому размеру, ссылок на
 * переиспользуемые буферы внутри нет. Поэтому снимок можно безопасно держать в
 * фоновой очереди и читать с worker-потока.
 *
 * Это НЕ data class, передача Kotlin data class как внешнего контракта запрещена.
 */
public class TrialSnapshot internal constructor(
    public val trialId: String,
    public val sessionId: String,
    public val participantId: String,
    public val trialIndex: Int,
    public val taskGroup: String,
    public val scenarioType: String,
    public val schemaVersion: String,

    public val displayProfile: DisplayProfile,
    public val clockSync: ClockSyncPoint,

    /** Время ACTION_DOWN в общей монотонной шкале elapsedRealtimeNanos */
    public val touchDownCommonTimestampNs: Long,
    /** Время терминального события в той же шкале */
    public val touchUpCommonTimestampNs: Long,
    /**
     * Длительность контакта по времени событий, а не по времени обработки callback.
     */
    public val contactDurationNs: Long,

    /** Одно из значений [TrialStatus]. */
    public val completionStatus: String,
    public val currentSampleCount: Int,
    public val historicalSampleCount: Int,
    public val secondPointerObserved: Boolean,

    /** Точность времени касания на этом устройстве: [Precision]. */
    public val timestampPrecision: String,
    /** Точность receipt-метки в шкале uptime: [Precision]. */
    public val appReceiptUptimePrecision: String,

    public val samples: TrialSamples,
) {
    public val totalSampleCount: Int get() = samples.count
}
