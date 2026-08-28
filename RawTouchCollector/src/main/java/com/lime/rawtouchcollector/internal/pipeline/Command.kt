package com.lime.rawtouchcollector.internal.pipeline

import com.lime.rawtouchcollector.TrialSnapshot
import java.util.concurrent.CountDownLatch

/**
 * Протокол общения с worker-потоком: всё, что может попасть в очередь.
 *
 * Если worker дошёл до [Barrier], значит всё поставленное раньше уже прошло
 * через приёмник и посчитано. Проверка «очередь пуста» такой гарантии не даёт —
 * обработчик мог забрать последний элемент и ещё не зафиксировать его.
 */
internal sealed interface Command {

    /** Завершённая попытка на фоновое сохранение. */
    class Persist(val snapshot: TrialSnapshot) : Command

    /** Снимается, когда всё поставленное раньше обработано. */
    class Barrier(val latch: CountDownLatch) : Command

    /** Остановить worker после разбора всего, что стоит в очереди перед этой командой. */
    object Stop : Command
}
