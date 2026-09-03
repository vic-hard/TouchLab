package com.lime.rawtouchcollector

/** Версия схемы данных. Меняется вместе с любым изменением состава или смысла полей. */
public object Schema {
    public const val VERSION: String = "stage1-1.1.0"
}

/** Статус завершения попытки */
public object TrialStatus {
    public const val UP: String = "UP"
    public const val CANCEL: String = "CANCEL"
    public const val MULTITOUCH_ERROR: String = "MULTITOUCH_ERROR"
}

/** Группа задания. На этапе 1 приложение передаёт только TAP */
public object TaskGroup {
    public const val TAP: String = "TAP"
}

/**
 * Тип сценария. На этапе 1 — одна фиксированная константа; параметр существует,
 * чтобы переход к сценариям этапа 2 не ломал публичную сигнатуру.
 */
public object ScenarioType {
    public const val STAGE1_TAP: String = "STAGE1_TAP"
}

/** Фактическая точность измерения времени. Не заявляется выше, чем есть на деле. */
public object Precision {
    public const val NANOSECONDS: String = "NANOSECONDS"
    public const val MILLISECONDS: String = "MILLISECONDS"
}

/**
 * Способ, которым получена точка синхронизации шкал.
 *
 * - [UPTIME_NANOS] — API 35+, SystemClock.uptimeNanos(), квантования нет;
 * - [MS_BOUNDARY]  — API < 35, пойман момент перещёлкивания uptimeMillis(),
 *   квантование снято измерением;
 * - [MS_PLAIN]     — API < 35, обычное чтение; квантование до 1 мс остаётся
 */
public object SyncMethod {
    public const val UPTIME_NANOS: String = "UPTIME_NANOS"
    public const val MS_BOUNDARY: String = "MS_BOUNDARY"
    public const val MS_PLAIN: String = "MS_PLAIN"
}

/** Режим удержания телефона. Этап 1 — только HAND. */
/**
 * Состояние сессии в экспорте.
 *
 * `INCOMPLETE` ставится приложением на холодном старте сессии, оставшейся `ACTIVE`:
 * процесс был убит, а молча продолжать такую сессию запрещено. У неё
 * `ended_at_wall_clock_ms` остаётся `null` — единственный разрешённый критерием 18 случай.
 */
public object SessionStatus {
    public const val ACTIVE: String = "ACTIVE"
    public const val COMPLETED: String = "COMPLETED"
    public const val INCOMPLETE: String = "INCOMPLETE"
}

public object PhoneSupportMode {
    public const val HAND: String = "HAND"
}

/** Коды ошибок, приходящие в [TrialListener.onCollectorError]. */
public object ErrorCode {
    /** Очередь фоновой обработки переполнена, попытка НЕ сохранена */
    public const val QUEUE_OVERFLOW: Int = 1

    /** Приёмник не смог зафиксировать попытку */
    public const val WRITE_FAILURE: Int = 2

    /** Событие пришло вне активной попытки и отброшено. */
    public const val EVENT_DROPPED: Int = 3

    /** startTrial поверх незакрытой попытки: предыдущая закрыта как CANCEL. */
    public const val IMPLICIT_CANCEL: Int = 4

    /** Профиль дисплея не обновлялся с прошлой попытки */
    public const val STALE_DISPLAY_PROFILE: Int = 5

    /** Не удалось поймать границу миллисекунды, точка синхронизации огрублена до MS_PLAIN. */
    public const val CLOCK_SYNC_FALLBACK: Int = 6

    /**
     * Барьер не сомкнулся за отведённое время: часть принятых попыток ещё не
     * зафиксирована. Экспортировать сессию как полную нельзя.
     */
    public const val QUIESCENCE_TIMEOUT: Int = 7
}
