package com.lime.rawtouchcollector.internal.capture

/**
 * Состояние попытки, §5.2 и §8.
 *
 * ```
 * IDLE ──startTrial──► ARMED ──ACTION_DOWN──► ACTIVE ──┬── ACTION_UP ─────► TERMINATED (UP)
 *                                                      ├── ACTION_CANCEL ─► TERMINATED (CANCEL)
 *                                                      └── POINTER_DOWN ──► TERMINATED (MULTITOUCH_ERROR)
 * ```
 *
 * После терминального состояния попытка не возобновляется
 */
internal enum class TrialState {
    /** Попытка не начата. */
    IDLE,

    /** startTrial вызван, ждём ACTION_DOWN. */
    ARMED,

    /** Идёт запись контакта. */
    ACTIVE,

    /** Попытка закрыта, ждём следующего startTrial. */
    TERMINATED,
}

/** Состояние сессии. */
internal enum class SessionState {
    /** Сессия не начата. */
    NONE,

    /** Сессия идёт, события принимаются. */
    ACTIVE,

    /**
     * Сессия закрывается: новые события отвергаются и считаются.
     *
     * Во время перехода к экспорту новые касания не должны незаметно попадать в уже
     * формируемый снимок.
     */
    CLOSING,
}
