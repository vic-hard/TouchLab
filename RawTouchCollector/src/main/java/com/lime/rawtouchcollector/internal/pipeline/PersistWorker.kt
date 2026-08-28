package com.lime.rawtouchcollector.internal.pipeline

import com.lime.rawtouchcollector.ErrorCode
import com.lime.rawtouchcollector.TrialSink
import com.lime.rawtouchcollector.TrialSnapshot
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Фоновая обработка завершённых попыток.
 *
 * Один поток-потребитель и ограниченная очередь. Главный поток кладёт снимки неблокирующим offer
 *
 * Переполнение - не молчаливая потеря: попытка не принимается, счётчик растёт,
 * слушатель получает ошибку, и подтверждение по этой попытке не придёт.
 */
internal class PersistWorker(
    private val capacity: Int = 64,
    private val onCompleted: (String) -> Unit,
    private val onPersisted: (String) -> Unit,
    private val onError: (Int, String) -> Unit,
    private val serialize: (TrialSnapshot) -> String,
) {

    private val queue = ArrayBlockingQueue<Command>(capacity)

    val acceptedTrials: AtomicLong = AtomicLong()
    val confirmedTrials: AtomicLong = AtomicLong()
    val queueOverflows: AtomicLong = AtomicLong()
    val writeFailures: AtomicLong = AtomicLong()

    @Volatile
    var sink: TrialSink? = null

    @Volatile
    var lastTrialJson: String? = null
        private set

    private var thread: Thread? = null

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "raw-touch-persist").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        queue.offer(Command.Stop)
        thread?.join(2_000)
        thread = null
    }

    /**
     * Принять попытку. Вызывается с главного потока и не блокирует его.
     *
     * @return true, если попытка принята в обработку. false означает потерю
     * из-за переполнения — показывать её пользователю как сохранённую нельзя.
     */
    fun submit(snapshot: TrialSnapshot): Boolean {
        if (queue.offer(Command.Persist(snapshot))) {
            acceptedTrials.incrementAndGet()
            return true
        }
        queueOverflows.incrementAndGet()
        onError(
            ErrorCode.QUEUE_OVERFLOW,
            "Очередь фоновой обработки переполнена (" + capacity +
                "), попытка " + snapshot.trialId + " не сохранена",
        )
        return false
    }

    /**
     * Дождаться, пока всё принятое до этого момента будет обработано и зафиксировано.
     * Барьер проходит через ту же очередь, поэтому не может обогнать ранее поставленные попытки.
     */
    fun awaitQuiescence(timeoutMs: Long): Boolean {
        if (!running) return true
        val latch = CountDownLatch(1)
        if (!queue.offer(Command.Barrier(latch))) {
            // Очередь забита: сам барьер поставить не удалось, гарантию дать нельзя.
            return false
        }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private fun loop() {
        while (true) {
            val command = try {
                queue.take()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }

            when (command) {
                is Command.Persist -> handle(command.snapshot)
                is Command.Barrier -> command.latch.countDown()
                Command.Stop -> return
            }
        }
    }

    private fun handle(snapshot: TrialSnapshot) {
        val json = try {
            serialize(snapshot)
        } catch (e: RuntimeException) {
            writeFailures.incrementAndGet()
            onError(
                ErrorCode.WRITE_FAILURE,
                "Сериализация попытки " + snapshot.trialId + " не удалась: " + e,
            )
            return
        }
        lastTrialJson = json
        onCompleted(json)

        val target = sink
        val ok = try {
            target?.persist(snapshot) ?: true
        } catch (e: RuntimeException) {
            onError(
                ErrorCode.WRITE_FAILURE,
                "Приёмник бросил исключение на попытке " + snapshot.trialId + ": " + e,
            )
            false
        }

        if (ok) {
            confirmedTrials.incrementAndGet()
            onPersisted(snapshot.trialId)
        } else {
            writeFailures.incrementAndGet()
            onError(
                ErrorCode.WRITE_FAILURE,
                "Приёмник не подтвердил фиксацию попытки " + snapshot.trialId,
            )
        }
    }
}
