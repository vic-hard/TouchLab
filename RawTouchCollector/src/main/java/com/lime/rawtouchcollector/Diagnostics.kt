package com.lime.rawtouchcollector

/**
 * Счётчики ошибок и потерь.
 *
 * Молчаливая потеря данных не допускается: всё, что было отброшено или не сохранено,
 * обязано быть видно здесь. Приложение не должно показывать попытку как сохранённую,
 * если [confirmedTrials] её не учёл.
 */
public class Diagnostics internal constructor(
    /** Попытки, принятые в очередь фоновой обработки. */
    public val acceptedTrials: Long,
    /** Попытки, фиксацию которых подтвердил приёмник. */
    public val confirmedTrials: Long,
    /** Попытки, потерянные из-за переполнения очереди. Норма — ноль. */
    public val queueOverflows: Long,
    /** Попытки, которые приёмник не смог записать. Норма — ноль. */
    public val writeFailures: Long,
    /** События, пришедшие до ACTION_DOWN текущей попытки. */
    public val eventsBeforeStart: Long,
    /**
     * События прерванного мультитачем жеста, отброшенные после MULTITOUCH_ERROR.
     *
     * Отдельно от [eventsBeforeStart], потому что это не отклонение, а требование:
     * запись первого пальца в рамках прерванной попытки не продолжается.
     * Ненулевое значение здесь — норма при каждом срабатывании мультитача.
     */
    public val eventsDiscardedAfterMultitouch: Long,
    /** События, пришедшие после терминального события попытки. */
    public val eventsAfterEnd: Long,
    /** События, пришедшие после закрытия сессии. */
    public val eventsAfterSessionClose: Long,
    /** Попытки, закрытые как CANCEL из-за startTrial поверх незакрытой. */
    public val implicitCancels: Long,
    /** Попытки, прерванные вторым пальцем. */
    public val multitouchErrors: Long,
    /** Попытки, начатые без обновления профиля дисплея с предыдущей. */
    public val trialsWithStaleDisplayProfile: Long,
    /** Точки синхронизации, для которых не удалось поймать границу миллисекунды. */
    public val clockSyncFallbacks: Long
) {
    /** Попытки, принятые, но ещё не подтверждённые или потерянные. */
    public val pendingTrials: Long get() = acceptedTrials - confirmedTrials - writeFailures

    override fun toString(): String =
        "accepted=" + acceptedTrials +
            " confirmed=" + confirmedTrials +
            " pending=" + pendingTrials +
            " queueOverflows=" + queueOverflows +
            " writeFailures=" + writeFailures +
            " beforeStart=" + eventsBeforeStart +
            " discardedAfterMultitouch=" + eventsDiscardedAfterMultitouch +
            " afterEnd=" + eventsAfterEnd +
            " afterSessionClose=" + eventsAfterSessionClose +
            " implicitCancels=" + implicitCancels +
            " multitouch=" + multitouchErrors +
            " staleDisplayProfile=" + trialsWithStaleDisplayProfile +
            " clockSyncFallbacks=" + clockSyncFallbacks
}
