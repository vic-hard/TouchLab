package com.lime.rawtouchcollector

/**
 * JNI-безопасный уровень выдачи: только String и примитивы.
 *
 * Через этот интерфейс данные уходят наружу так, чтобы их можно было
 * забрать из Unity через AndroidJavaObject без передачи Kotlin-типов.
 * Структурный доступ к тем же данным даёт [TrialSink].
 *
 * Все методы вызываются на фоновом потоке коллектора, не на главном.
 */
public interface TrialListener {

    /** Попытка завершена и сериализована. Это ещё НЕ значит, что она сохранена. */
    public fun onTrialCompleted(trialJson: String)

    /**
     * Приёмник подтвердил фиксацию попытки. Только после этого попытку можно
     * показывать пользователю как сохранённую.
     */
    public fun onTrialPersisted(trialId: String)

    /** Ошибка или потеря данных. Коды — в [ErrorCode]. */
    public fun onCollectorError(code: Int, message: String)
}
