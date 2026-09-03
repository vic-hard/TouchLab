# RawTouchCollector

Переиспользуемая AAR-библиотека сбора сырых данных касания.

Не содержит UI и не зависит от Activity. Снаружи в неё отдают `MotionEvent`, она отдаёт завершённые попытки — как JSON-строку и как структурный снимок.

- `minSdk 31`, `compileSdk 37`, Kotlin, `explicitApi()`
- Зависимости: только `androidx.annotation`. Транзитивных в потребителя не приносит.
- Артефакт: `rawtouchcollector-1.2.0-release.aar`

---

## Публичный API

Контракт Java-совместим: без suspend, Flow, корутин, обобщений в сигнатурах и Kotlin-функциональных типов. Проверяется компиляцией `app/src/main/java/com/lime/touchlab/ApiCompatCheck.java` — если Kotlin-специфика просочится в публичный API, сборка `:app` упадёт там.

```kotlin
RawTouchCollector(appContext: Context)

// сессия
fun startSession(sessionId: String, participantId: String)
fun endSession()                                    // блокирующий, не с главного потока
fun currentSessionInfo(): SessionInfo?
fun sessionClockSync(): ClockSyncPoint?

// попытка
fun startTrial(trialId: String, taskGroup: String, scenarioType: String)
fun processMotionEvent(event: MotionEvent)          // с главного потока

// окружение
fun updateDisplayProfile(windowWidthPx: Int, windowHeightPx: Int,
                         modeWidthPx: Int, modeHeightPx: Int,
                         refreshRateHz: Float, densityDpi: Int)
fun invalidateClockSync()                           // из Activity.onResume

// выдача
fun setTrialListener(listener: TrialListener?)
fun setTrialSink(sink: TrialSink?)
fun getLastTrialJson(): String?
fun getSchemaVersion(): String
fun getDiagnostics(): Diagnostics
fun awaitQuiescence(timeoutMs: Long): Boolean       // блокирующий, не с главного потока

// управление
fun reset()
fun clearBuffer()
fun shutdown()

// словарь схемы — им пользуется CSV-экспорт приложения
object SchemaFields   // имена всех полей схемы
object SessionStatus  // ACTIVE / COMPLETED / INCOMPLETE
```

`SchemaFields` публичен намеренно: имена полей должны существовать в одном месте, иначе
CSV-экспорт заводит вторую копию и схема начинает расходиться сама с собой. Из Java
доступен как `SchemaFields.TRIAL_ID`.

### Порядок вызовов

```
startSession(sessionId, participantId)
  updateDisplayProfile(...)                 // перед каждой попыткой
  startTrial(trialId, "TAP", "STAGE1_TAP")  // снимает свежую clock_sync
    processMotionEvent(...) × N
  ...
endSession()                                 // ждёт фиксации всех принятых попыток
```

`startTrial` вызывается **до** касания. Хост арминует следующую попытку синхронно, сразу после терминального события предыдущей, — иначе события успеют прийти в состояние `TERMINATED` и будут отброшены со счётчиком.

### Два уровня выдачи данных

Разделение осознанное, а не дублирование:

| | `TrialListener` | `TrialSink` |
|---|---|---|
| Отдаёт | JSON-строку | `TrialSnapshot` |
| Для кого | будущий Unity через JNI | слой хранения на Room |
| Почему | §4.1 запрещает передавать через JNI Kotlin data class | Room не должен парсить JSON обратно |

`TrialSink.persist()` обязан вернуть `true` только после фактической фиксации — транзакции или эквивалентной принудительной записи. `false` и исключение считаются ошибкой записи, попытка не будет подтверждена.

---

## Потоковая модель

```
ГЛАВНЫЙ ПОТОК                        WORKER (один, daemon)
─────────────                        ─────────────────────
processMotionEvent()
  ├ 2 receipt-метки — первым делом
  ├ history → SampleBuffer
  └ current → SampleBuffer

терминальное событие
  ├ TrialSnapshot (копии массивов)
  ├ queue.offer() ───────────────────►  take()
  │   переполнение → счётчик,            ├ TrialJsonWriter.write()
  │   ошибка, НЕ «сохранено»             ├ onTrialCompleted(json)
  └ buffer.reset()                       ├ sink.persist()
                                         ├ ok   → confirmed++, onTrialPersisted
                                         └ fail → writeFailures++, onCollectorError
endSession()
  ├ state = CLOSING
  └ Barrier → queue ─────────────────►  дошёл до Barrier ⇒ всё до него
     latch.await() ◄─────────────────────  зафиксировано, latch.countDown()
```

На главном потоке — только чтение `MotionEvent`, копирование в примитивные массивы и постановка в очередь. Сериализация, запись и вызовы `TrialSink` — на worker.

Сам объект `MotionEvent` на отложенную обработку не передаётся: система его переиспользует, и после возврата из обработчика он недействителен.

**Колбэки `TrialListener` приходят на worker-потоке.** Для обновления UI переключайтесь на главный.

### Три инварианта

1. **Главный поток не блокируется.** `offer()`, не `put()`. Очередь ограничена 64 элементами, переполнение — явный счётчик и ошибка, а не задержка ввода и не молчаливая потеря.
2. **`session_id` штампуется при постановке в очередь**, на главном потоке. Попытки сессии A, ещё лежащие в очереди в момент старта сессии B, физически не могут получить идентификатор B — гарантия структурная, а не дисциплинарная (критерий 28).
3. **Барьер — не «очередь пуста».** Worker единственный и FIFO: если он дошёл до `Barrier`, всё поставленное раньше уже прошло `sink.persist()` и посчитано. Проверки размера очереди недостаточно — обработчик мог забрать последний элемент и ещё не зафиксировать его.

### Аллокации на горячем пути

`SampleBuffer` держит 19 параллельных примитивных массивов. Отсчёт не создаёт объектов: поля забираются одним `getPointerCoords` / `getHistoricalPointerCoords` в переиспользуемый `PointerCoords`. Копия делается один раз на попытку, в момент закрытия, — снимок получает собственную память и не ссылается на переиспользуемый буфер.

---

## Границы сессии

Порядок операций в `endSession()`:

1. `sessionState = CLOSING` — новые события отвергаются и считаются в `eventsAfterSessionClose`. Это то самое детерминированное поведение, которого требует: во время перехода к экспорту касания не попадают в уже формируемый снимок.
2. Незакрытая активная попытка закрывается со статусом `CANCEL`.
3. В очередь ставится барьер, поток ждёт его прохождения.
4. Если барьер не сомкнулся за 5 с — `onCollectorError(QUIESCENCE_TIMEOUT, …)`. экспортировать сессию как полную нельзя.
5. Проставляется `ended_at_wall_clock_ms`, состояние переходит в `NONE`.

`startSession` при активной сессии бросает `IllegalStateException`. Неявное закрытие было бы удобнее, но скрыло бы от приложения потерю границы.

---

## Временные шкалы

Три времени хранятся раздельно и никогда не выводятся друг из друга:

| | Шкала | Поле |
|---|---|---|
| Календарное время сессии | Unix epoch | `started_at_wall_clock_ms`, `ended_at_wall_clock_ms` |
| Время касания | `uptimeMillis` | `touch_event_time_uptime_ms` / `_ns` |
| Время получения события | `uptimeMillis` и `elapsedRealtime` | `app_receipt_time_uptime_ns`, `app_receipt_time_elapsed_ns` |
| Общая монотонная шкала | `elapsedRealtimeNanos` | `common_timestamp_ns` |

Наносекундные API появились на разных уровнях, и библиотека это учитывает раздельно:

| | API 31–33 | API 34 | API 35+ |
|---|---|---|---|
| Время события | `eventTime × 1e6` | `getEventTimeNanos()` | `getEventTimeNanos()` |
| `timestamp_precision` | MILLISECONDS | NANOSECONDS | NANOSECONDS |
| Receipt uptime | `uptimeMillis × 1e6` | `uptimeMillis × 1e6` | `uptimeNanos()` |
| `app_receipt_uptime_precision` | MILLISECONDS | MILLISECONDS | NANOSECONDS |
| Метод clock_sync | `MS_BOUNDARY` | `MS_BOUNDARY` | `UPTIME_NANOS` |

Умножение миллисекунд на 1 000 000 — преобразование единиц, а не повышение точности; признак точности при этом остаётся `MILLISECONDS`.

Подробности схемы и разложение погрешности синхронизации — `docs/schema-stage1.md`.

---

## Проверено на устройстве

Samsung SM-A525F, Android 14, API 34, дисплей 1080×2400 @ 90 Гц.

Сценарии целостности прогонялись синтетическим вводом через `adb shell input`. Частота отсчётов
и характер каналов давления и площади измерены **живым пальцем**: синтетический ввод инжектится
через `InputManager.injectInputEvent` минуя тач-контроллер и его драйвер, и для таких замеров
непригоден.

| Сценарий | Результат |
|---|---|
| 100 последовательных TAP | `accepted=100 confirmed=100`, все счётчики ошибок 0 |
| Свайп 250–310 мс | 24–29 текущих отсчётов + 22–27 historical |
| Частота отсчётов, живой палец | 2317 отсчётов за 8914 мс контакта = **≈259 Гц** в потоке MotionEvent при refresh 90 Гц |
| TAP → немедленный `endSession` | `accepted=102 confirmed=102 pending=0`, последняя попытка на месте |
| Сессия A → сразу сессия B, по 50 TAP | `{s-b504dda3=50, s-eacac472=50}` — смешивания нет |
| 150 TAP против приёмника с задержкой 200 мс | `accepted=128 queueOverflows=22`; 128 + 22 = 150, потерь без учёта нет |
| Точка синхронизации | `MS_BOUNDARY`, sampling 500–950 нс, quantization 0, откатов 0 |