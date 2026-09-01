# Сборка

## Требования к окружению

| | Версия |
|---|---|
| JDK | 21 (подойдёт JBR из комплекта Android Studio) |
| Android SDK | Platform 37, Build-Tools 37 |
| Gradle | 9.4.1 — ставить отдельно не нужно, используется wrapper |
| Android Gradle Plugin | 9.2.1 |
| Kotlin | 2.2.10 — входит в AGP, отдельного плагина нет |
| KSP | 2.2.10-2.0.2 — обработка аннотаций Room в `:app` |
| Room | 2.7.1 — только в `:app`; библиотека от него не зависит |

Путь к Android SDK берётся из `local.properties` в корне проекта:

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

Файл в репозиторий не входит — Android Studio создаёт его при первом открытии проекта. Для сборки из командной строки на чистой машине создайте его вручную или задайте переменную окружения `ANDROID_HOME`.

## Сборка библиотеки

```bash
gradlew.bat :RawTouchCollector:assembleRelease      # Windows
./gradlew :RawTouchCollector:assembleRelease        # Linux / macOS
```

Результат:

```
RawTouchCollector/build/outputs/aar/rawtouchcollector-1.0.0-release.aar
```

Debug-вариант — `:RawTouchCollector:assembleDebug`.

## Сборка приложения

```bash
gradlew.bat :app:assembleRelease
```

Результат: `app/build/outputs/apk/release/app-release.apk`.

APK подписан debug-ключом: заказчику нужна устанавливаемая сборка, отдельный keystore для этапа 1 избыточен. Если потребуется подпись собственным ключом — заменить `signingConfig` в `app/build.gradle.kts`.

## Сборка всего с нуля

```bash
gradlew.bat clean :RawTouchCollector:assembleRelease :app:assembleRelease
```

## Версия библиотеки

Задаётся одной строкой в `RawTouchCollector/build.gradle.kts`:

```kotlin
private val aarVersion = "1.1.0"
```

Оттуда она попадает в три места сразу: имя файла AAR, `BuildConfig.AAR_VERSION` и поле `aar_version` в каждой записанной попытке. Менять только здесь.

## Подключение AAR в стороннем проекте

Положить файл в `libs/` и добавить зависимость:

```kotlin
dependencies {
    implementation(files("libs/rawtouchcollector-1.1.0-release.aar"))
}
```

Требования к проекту-потребителю: `minSdk` не ниже 31, `compileSdk` не ниже 35.

Библиотека не тянет транзитивных зависимостей — только `kotlin-stdlib` и `androidx.annotation`. Проверить состав можно так:

```bash
gradlew.bat :RawTouchCollector:dependencies --configuration releaseRuntimeClasspath
```

Ожидаемый вывод:

```
+--- org.jetbrains.kotlin:kotlin-stdlib:2.2.10
|    \--- org.jetbrains:annotations:13.0
\--- androidx.annotation:annotation:1.9.1
```

## Структура проекта

```
TouchLab/
├── RawTouchCollector/     библиотека, поставляемый артефакт
├── app/                   приложение раздела 12: сессии, хранение, экспорт
│   ├── session/           автомат сессии, техсводка, ViewModel-владелец
│   ├── storage/           Room: шесть сущностей, DAO, приёмник попыток
│   └── export/            CSV × 6 + schema.json + README → ZIP
├── docs/                  схема данных, инструкция, отчёт о проверке
├── settings.gradle.kts    include(":app"), include(":RawTouchCollector")
└── gradle/libs.versions.toml   версии зависимостей
```

Модуль `:app` в поставку AAR не входит, но является частью поставки этапа 1: он владеет
сессиями, `participant_id`, хранением в Room и экспортом — по разделению
ответственности из уточнения №1 всё это находится вне библиотеки.

## Что смотреть после установки

| Что | Где |
|---|---|
| Счётчики завершённых и подтверждённых попыток, техсводка | на экране |
| Полная диагностика коллектора и ошибки | logcat, теги `TouchLabSession`, `TouchLabStorage`, `TouchLabExport` |
| База данных | `/data/data/com.lime.touchlab/databases/touchlab.db`, через `adb run-as` |
| Собранные архивы | `cacheDir/exports`, отдаются через меню «Поделиться» |
| `last-trial.json` для `measure_touch_rate.py` | появляется только при включённом флажке «Диагностический дамп», в `getExternalFilesDir(null)` |
