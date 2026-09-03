# Схема данных этапа 1

`schema_version = stage1-1.1.0`

Единственный источник имён полей в коде — `SchemaFields.kt` (публичная часть API библиотеки). Состав и порядок колонок каждого CSV объявлен один раз в `app/.../export/ExportSchema.kt`: оттуда печатается шапка файла и оттуда же собирается `schema.json` внутри архива. CSV-экспорт и `validate_export.py` обязаны использовать те же имена.

Обозначения обязательности: **О** — всегда присутствует; **Н** — может быть `null`, значение `null` осмысленно и означает «неприменимо».

## Идентификаторы

Четыре идентификатора — `session_id`, `trial_id`, `clock_sync_id`, `display_profile_id` — строковые UUID (уточнение №3). Пятый идентификатор, `participant_id`, — произвольная строка, UUID для него не требуется.

Это требование целостности, а не оформления. Порядковые номера вида `t-1`, `cs-1`, `dp-1` уникальны только внутри одной сессии или одного запуска процесса, а экспортируемый архив объединяет несколько сессий, в том числе записанных до и после перезапуска приложения. Под общим ключом `t-1` отсчёты двух разных попыток слились бы в одну, и валидатор увидел бы попытку с двумя `ACTION_DOWN` и убывающими метками времени.

Порядковый номер попытки внутри сессии хранится отдельным полем `trial_index` и идентификатором не является.

---

## 1. Устройство — `devices`, §6.1

| Поле | Тип | Ед. | Обяз. | Источник |
|---|---|---|---|---|
| `manufacturer` | string | — | О | `Build.MANUFACTURER` |
| `model` | string | — | О | `Build.MODEL` |
| `android_version` | string | — | О | `Build.VERSION.RELEASE` |
| `sdk_int` | int | — | О | `Build.VERSION.SDK_INT` |
| `app_version` | string | — | О | `PackageInfo.versionName`; `"unknown"`, если недоступно |
| `aar_version` | string | — | О | `BuildConfig.AAR_VERSION` |
| `density_dpi` | int | dpi | О | `Configuration.densityDpi` |

## 2. Профиль дисплея — `display_profiles`

Фиксируется при старте сессии и перед каждой попыткой. При неизменных значениях переиспользуется тот же `display_profile_id`.

| Поле | Тип | Ед. | Обяз. | Примечание |
|---|---|---|---|---|
| `display_profile_id` | string | — | О | UUID; заглушка с нулевой геометрией окна, если хост ни разу не вызвал `updateDisplayProfile` |
| `window_width_px` | int | px | О | параметры окна приложения, не экрана |
| `window_height_px` | int | px | О | |
| `display_mode_width_px` | int | px | О | `Display.Mode.physicalWidth` |
| `display_mode_height_px` | int | px | О | |
| `display_refresh_rate_hz` | float | Гц | О | **не** частота тач-контроллера, критерий 22 |
| `density_dpi` | int | dpi | О | |
| `captured_at_elapsed_ns` | long | нс | О | момент фиксации в шкале `elapsedRealtimeNanos` |

## 3. Сессия — `sessions`

| Поле | Тип | Ед. | Обяз. | Примечание |
|---|---|---|---|---|
| `session_id` | string | — | О | UUID, задаёт приложение |
| `participant_id` | string | — | О | |
| `started_at_wall_clock_ms` | long | мс от эпохи Unix | О | `System.currentTimeMillis()`, хранится отдельно от uptime |
| `ended_at_wall_clock_ms` | long | мс от эпохи Unix | Н | `null` только у незавершённой сессии, критерий 18 |
| `phone_support_mode` | string | — | О | этап 1 — всегда `HAND` |
| `session_status` | string | — | О | `COMPLETED` / `INCOMPLETE` / `ACTIVE` |
| `schema_version` | string | — | О | версия, под которой записаны данные этой сессии |

### Счётчики сессии, §9.5 и критерий 29

Двенадцать колонок в той же строке сессии. Снимаются один раз, после возврата из `endSession()`: барьер к этому моменту сомкнут, и все принятые попытки прошли через `persist()`. Снимок раньше барьера занизил бы `trials_confirmed` без всякой ошибки.

Значения — разность с состоянием счётчиков на старте сессии: `Diagnostics` копит за всё время жизни коллектора, а коллектор переживает и экран, и предыдущие сессии.

`null` здесь означает **«неизвестно: сессия не была закрыта штатно»** — это не то же самое, что «неприменимо» у остальных полей схемы. Подставлять нули запрещено: это было бы заявлением, что потерь не было, а это как раз неизвестно.

| Поле | Тип | Обяз. | Норма | Смысл |
|---|---|---|---|---|
| `trials_accepted` | long | Н | — | принято в очередь фоновой обработки |
| `trials_confirmed` | long | Н | `= accepted − write_failures` | подтверждено хранилищем; **равно числу строк в `trials.csv`** |
| `queue_overflows` | long | Н | 0 | потеряно из-за переполнения очереди |
| `write_failures` | long | Н | 0 | хранилище не смогло записать |
| `events_before_start` | long | Н | 0 | события до `ACTION_DOWN` попытки |
| `events_after_end` | long | Н | 0 | события после терминального |
| `events_after_session_close` | long | Н | 0 | события после закрытия сессии |
| `events_discarded_after_multitouch` | long | Н | — | события прерванного жеста; ненулевое — норма, §8 |
| `implicit_cancels` | long | Н | 0 | `startTrial` поверх незакрытой попытки |
| `multitouch_errors` | long | Н | — | попытки, прерванные вторым пальцем |
| `trials_with_stale_display_profile` | long | Н | 0 | профиль не обновлялся перед попыткой |
| `clock_sync_fallbacks` | long | Н | 0 | точки, огрублённые до `MS_PLAIN` |

Попытка, отсутствующая в `trials.csv`, обязана быть объяснена `queue_overflows` или `write_failures`. Сохранённой без подтверждения она не показывается нигде: счётчик подтверждённых растёт только по `true` от `persist()`.

## 4. Точка синхронизации — `clock_sync`

Снимается при `startSession`, при каждом `startTrial` и при `invalidateClockSync` (вызывается хостом из `onResume`).

| Поле | Тип | Ед. | Обяз. | Примечание |
|---|---|---|---|---|
| `clock_sync_id` | string | — | О | UUID |
| `session_id` | string | — | О | |
| `uptime_timestamp_ns` | long | нс | О | точка привязки на шкале uptime |
| `elapsed_realtime_timestamp_ns` | long | нс | О | она же на общей шкале |
| `offset_ns` | long | нс | О | `elapsed − uptime`; `common = uptime + offset` |
| `sync_sampling_uncertainty_ns` | long | нс | О | ширина вилки между двумя чтениями; всегда > 0 |
| `sync_quantization_uncertainty_ns` | long | нс | О | 0 при `UPTIME_NANOS` и `MS_BOUNDARY`; 1 000 000 при `MS_PLAIN` |
| `sync_method` | string | — | О | `UPTIME_NANOS` / `MS_BOUNDARY` / `MS_PLAIN` |
| `uptime_measurement_precision` | string | — | О | `NANOSECONDS` / `MILLISECONDS` |

### Почему погрешность разложена на два слагаемых

У них разная природа, и одно суммарное число скрыло бы, что именно пошло не так.

- **Sampling** — два таймера нельзя прочитать в один момент. Есть всегда.
- **Quantization** — сам прочитанный uptime может быть грубым. `uptimeMillis()` усекает значение до целых миллисекунд, и неизвестная дробная часть до 1 мс целиком уходит в `offset_ns`.

`SystemClock.uptimeNanos()` доступен с API 35, `MotionEvent.getEventTimeNanos()` — с API 34. Поэтому на API 34 время касания уже наносекундное, а смещение всё ещё считалось бы по квантованному uptime, и ошибка становилась бы систематическим сдвигом всей попытки на 0…1 мс.

Метод `MS_BOUNDARY` снимает квантование измерением: спин-цикл ловит момент перещёлкивания `uptimeMillis()`, когда истинное uptime заведомо равно целому числу миллисекунд. Замер на SM-A525F (API 34): sampling 500–950 нс, quantization 0.

### Правило для валидатора

```
sync_quantization_uncertainty_ns == 0  ⟹  sdk_int >= 35 ИЛИ sync_method == "MS_BOUNDARY"
sync_method == "UPTIME_NANOS"          ⟹  sdk_int >= 35
uptime_measurement_precision == "NANOSECONDS" ⟹ sdk_int >= 35
```

## 5. Попытка — `trials`

| Поле | Тип | Ед. | Обяз. | Примечание |
|---|---|---|---|---|
| `trial_id` | string | — | О | UUID, задаёт приложение |
| `session_id` | string | — | О | штампуется при постановке в очередь |
| `participant_id` | string | — | О | |
| `trial_index` | int | — | О | порядковый номер внутри сессии, с 1 |
| `task_group` | string | — | О | этап 1 — `TAP` |
| `scenario_type` | string | — | О | этап 1 — `STAGE1_TAP` |
| `display_profile_id` | string | — | О | профиль, действовавший перед попыткой |
| `clock_sync_id` | string | — | О | точка, снятая перед попыткой |
| `touch_down_common_timestamp_ns` | long | нс | О | шкала `elapsedRealtimeNanos` |
| `touch_up_common_timestamp_ns` | long | нс | О | момент терминального события |
| `contact_duration_ns` | long | нс | О | по времени **события**, не callback |
| `completion_status` | string | — | О | `UP` / `CANCEL` / `MULTITOUCH_ERROR` |
| `current_sample_count` | int | — | О | |
| `historical_sample_count` | int | — | О | |
| `second_pointer_observed` | bool | — | О | |
| `timestamp_precision` | string | — | О | точность времени касания на устройстве |
| `app_receipt_uptime_precision` | string | — | О | точность receipt-метки в шкале uptime |

## 6. Отсчёт — `touch_samples`

25 полей. Значения сохраняются ровно так, как их вернул Android: без прореживания, сглаживания и отбрасывания повторов, включая 0, 1 и совпадающие `touch_major`/`touch_minor` (§5.2).

| Поле | Тип | Ед. | Обяз. | Примечание |
|---|---|---|---|---|
| `sample_index` | int | — | О | порядковый номер внутри попытки, с 0 |
| `event_action` | int | — | О | `MotionEvent.getActionMasked()` |
| `pointer_id` | int | — | О | стабильный внутри контакта |
| `pointer_index` | int | — | О | диагностическое |
| `action_index` | int | — | О | диагностическое |
| `pointer_count` | int | — | О | диагностическое |
| `touch_event_time_uptime_ms` | long | мс | О | исходный `eventTime` / `historicalEventTime` |
| `touch_event_time_uptime_ns` | long | нс | О | API 34+: nanos-методы; ниже — `мс × 1e6` как преобразование единиц |
| `timestamp_precision` | string | — | О | `NANOSECONDS` только при фактическом использовании nanos-методов |
| `common_timestamp_ns` | long | нс | О | `touch_event_time_uptime_ns + offset_ns` |
| `app_receipt_time_uptime_ns` | long | нс | О | API 35+: `uptimeNanos()`; ниже — `uptimeMillis × 1e6` |
| `app_receipt_uptime_precision` | string | — | О | фактическая точность предыдущего поля |
| `app_receipt_time_elapsed_ns` | long | нс | О | шкала `elapsedRealtimeNanos` |
| `clock_sync_id` | string | — | О | точка, применённая к отсчёту |
| `relative_time_ms` | double | мс | О | от `ACTION_DOWN`, по времени события |
| `x` | float | px | О | |
| `y` | float | px | О | |
| `touch_major` | float | px | О | сырое значение Android |
| `touch_minor` | float | px | О | сырое значение Android |
| `size` | float | — | О | сырое значение Android |
| `pressure` | float | — | О | сырой канал; постоянство не ошибка, критерий 33 |
| `orientation` | float | рад | О | |
| `tool_type` | int | — | О | `getToolType`; исторического варианта нет, значение постоянно внутри события |
| `is_historical` | bool | — | О | |
| `history_index` | int | — | Н | индекс historical sample; `null` у текущего отсчёта |

### Порядок отсчётов

Внутри попытки отсчёты идут хронологически. Historical samples родительского события пишутся **раньше** его текущего отсчёта, потому что произошли раньше. Обе receipt-метки у historical samples те же, что у родительского события: они относятся к моменту доставки всего пакета приложению.

### Обе метки времени получения

Снимаются первыми в `processMotionEvent`, до чтения любых полей. `app_receipt_time_uptime_ns` — в той же базе, что `eventTime`, поэтому годится для прямой диагностической разности. `app_receipt_time_elapsed_ns` — в общей шкале, для сопоставления с другими сенсорными каналами. Разность между ними задержкой ввода не является: это разные временные базы.

## 7. Диагностика

Счётчики `Diagnostics` библиотеки — источник колонок счётчиков `sessions` (раздел 3). До версии схемы `stage1-1.1.0` они существовали только на экране и умирали вместе с процессом; §11.1 требует от валидатора проверять «наличие счётчиков ошибок очереди и записи, если такие ошибки происходили», а проверять было нечего.

Соответствие имён: `acceptedTrials` → `trials_accepted`, `confirmedTrials` → `trials_confirmed`, `queueOverflows` → `queue_overflows`, `writeFailures` → `write_failures`, `eventsBeforeStart` → `events_before_start`, `eventsAfterEnd` → `events_after_end`, `eventsAfterSessionClose` → `events_after_session_close`, `eventsDiscardedAfterMultitouch` → `events_discarded_after_multitouch`, `implicitCancels` → `implicit_cancels`, `multitouchErrors` → `multitouch_errors`, `trialsWithStaleDisplayProfile` → `trials_with_stale_display_profile`, `clockSyncFallbacks` → `clock_sync_fallbacks`.

`Diagnostics` считает за всё время жизни коллектора; в архив идёт разность за сессию.

## 8. Состав архива и `schema.json`

Шесть CSV, `schema.json` и `README.md`. Состав и порядок колонок каждого CSV объявлены один раз в `ExportSchema.kt`; шапка печатается оттуда, а строка данных другой длины вызывает исключение и роняет экспорт — испорченный архив хуже отсутствующего.

`schema.json` описывает каждую колонку каждого файла: имя, тип, единицу измерения, допустимость `null` и смысл. Там же — версия схемы, смысл пяти идентификаторов и правила, которые из состава колонок не выводятся (соответствие `timestamp_precision` и `sync_method` уровню API, равенство `trials_confirmed` числу строк `trials.csv`, кодировка).

### Две версии схемы в одном архиве

`schema.json` описывает **структуру архива**, а колонка `schema_version` в `sessions.csv` и `trials.csv` — **версию, под которой записаны данные**. У сессии, записанной прежней сборкой и переэкспортированной после миграции, они законно расходятся: структура `stage1-1.1.0`, данные `stage1-1.0.0`. Валидатор обязан различать эти две величины и не считать расхождение ошибкой.
