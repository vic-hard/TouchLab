package com.lime.touchlab.storage

import com.lime.rawtouchcollector.Diagnostics

/**
 * Счётчики диагностики за одну сессию.
 *
 * [Diagnostics] копит за всё время жизни коллектора, а коллектор переживает и экран, и
 * предыдущие сессии. Поэтому в архив идёт разность между снимком, взятым после того как
 * сомкнулся барьер `endSession`, и снимком, взятым при старте сессии.
 *
 * Раньше барьера снимать нельзя: часть попыток ещё не дошла до `persist()`, и
 * `trials_confirmed` оказался бы занижен без всякой ошибки.
 */
class SessionCounters(
    val trialsAccepted: Long,
    val trialsConfirmed: Long,
    val queueOverflows: Long,
    val writeFailures: Long,
    val eventsBeforeStart: Long,
    val eventsAfterEnd: Long,
    val eventsAfterSessionClose: Long,
    val eventsDiscardedAfterMultitouch: Long,
    val implicitCancels: Long,
    val multitouchErrors: Long,
    val trialsWithStaleDisplayProfile: Long,
    val clockSyncFallbacks: Long,
) {
    companion object {
        /** Разность двух снимков: «сколько случилось за эту сессию». */
        fun between(base: Diagnostics, now: Diagnostics): SessionCounters = SessionCounters(
            trialsAccepted = now.acceptedTrials - base.acceptedTrials,
            trialsConfirmed = now.confirmedTrials - base.confirmedTrials,
            queueOverflows = now.queueOverflows - base.queueOverflows,
            writeFailures = now.writeFailures - base.writeFailures,
            eventsBeforeStart = now.eventsBeforeStart - base.eventsBeforeStart,
            eventsAfterEnd = now.eventsAfterEnd - base.eventsAfterEnd,
            eventsAfterSessionClose = now.eventsAfterSessionClose - base.eventsAfterSessionClose,
            eventsDiscardedAfterMultitouch =
                now.eventsDiscardedAfterMultitouch - base.eventsDiscardedAfterMultitouch,
            implicitCancels = now.implicitCancels - base.implicitCancels,
            multitouchErrors = now.multitouchErrors - base.multitouchErrors,
            trialsWithStaleDisplayProfile =
                now.trialsWithStaleDisplayProfile - base.trialsWithStaleDisplayProfile,
            clockSyncFallbacks = now.clockSyncFallbacks - base.clockSyncFallbacks,
        )
    }
}
