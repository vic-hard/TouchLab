package com.lime.touchlab.session

import java.io.File

/**
 * Состояние сессии.
 *
 * Переходы разрешены только по кругу IDLE → ACTIVE → CLOSING → CLOSED → ACTIVE.
 * Смысл именно в том, что состояние — поле, а не набор флагов у кнопок: раньше
 * «сессия идёт» выводилось из `isEnabled` двух кнопок, и любое неучтённое место
 * могло начать вторую сессию поверх незакрытой.
 */
enum class SessionState {
    /** Ни одной сессии в этом запуске ещё не начиналось. */
    IDLE,

    /** Сессия идёт, касания принимаются. */
    ACTIVE,

    /** Идёт `endSession`: барьер ещё не сомкнулся, начинать новую нельзя. */
    CLOSING,

    /** Сессия зафиксирована. Экспорт разрешён только в этом состоянии. */
    CLOSED,
}

/** Что происходит с архивом. */
enum class ExportState {
    NONE,
    RUNNING,
    READY,
    FAILED,
}

/**
 * Липкая ошибка.
 *
 * Не затирается следующей удачной попыткой: переполнение очереди
 * и отказ записи видны явно, а не мигают на экране между двумя тапами.
 */
class SessionError(
    val code: Int,
    val message: String,
    val occurrences: Int,
)

/**
 * Снимок для интерфейса. Пересобирается целиком и отдаётся на главный поток —
 * частичных обновлений нет, поэтому UI не может показать смесь двух состояний.
 */
class SessionUiState(
    val state: SessionState,
    val participantId: String,
    val sessionId: String?,

    /** Завершённых попыток: принятые в очередь плюс потерянные на переполнении. */
    val completedTrials: Long,

    /** Подтверждённо сохранённых: только те, по которым приёмник вернул true. */
    val confirmedTrials: Long,

    val queueOverflows: Long,
    val writeFailures: Long,
    val summary: TrialSummary?,
    val error: SessionError?,

    /** Сколько сессий прошлого запуска помечено незавершёнными. */
    val recoveredIncompleteSessions: Int,

    val exportState: ExportState,
    val exportFile: File?,
    val exportMessage: String?,

    /** Есть ли зафиксированная сессия, которую можно выгрузить. */
    val exportableSessionId: String?,
) {
    val canStart: Boolean get() = state == SessionState.IDLE || state == SessionState.CLOSED
    val canFinish: Boolean get() = state == SessionState.ACTIVE
    val canExport: Boolean
        get() = state != SessionState.ACTIVE &&
                state != SessionState.CLOSING &&
                exportableSessionId != null &&
                exportState != ExportState.RUNNING
}
