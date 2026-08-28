# Сборка

## Требования к окружению

| | Версия |
|---|---|
| JDK | 21 (подойдёт JBR из комплекта Android Studio) |
| Android SDK | Platform 37, Build-Tools 37 |
| Gradle | 9.4.1 — ставить отдельно не нужно, используется wrapper |
| Android Gradle Plugin | 9.2.1 |
| Kotlin | 2.2.10 — входит в AGP, отдельного плагина нет |

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
private val aarVersion = "1.0.0"
```

Оттуда она попадает в три места сразу: имя файла AAR, `BuildConfig.AAR_VERSION` и поле `aar_version` в каждой записанной попытке. Менять только здесь.

## Подключение AAR в стороннем проекте

Положить файл в `libs/` и добавить зависимость:

```kotlin
dependencies {
    implementation(files("libs/rawtouchcollector-1.0.0-release.aar"))
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
├── app/                   минимальный хост для технической проверки
├── docs/                  схема данных, инструкция, отчёт о проверке
├── settings.gradle.kts    include(":app"), include(":RawTouchCollector")
└── gradle/libs.versions.toml   версии зависимостей
```

Модуль `:app` в поставку AAR не входит: это оболочка, через которую библиотеке отдаётся поток MotionEvent с реального устройства. Полноценное приложение по разделу 12 ТЗ — предмет следующего блока.
