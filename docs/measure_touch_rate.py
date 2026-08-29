#!/usr/bin/env python3
"""Частота отсчётов касания по выгруженным попыткам.

Считает частоту отсчётов в потоке MotionEvent: сумма отсчётов, делённая на
суммарную длительность контакта. Это НЕ паспортная частота опроса тач-контроллера —
между сенсором и приложением стоят драйвер и InputReader, которые вправе объединять
отсчёты, поэтому результат является нижней оценкой частоты отчётов драйвера.

Замер имеет смысл только на данных живого пальца. Синтетический ввод
(`adb shell input`) инжектится через InputManager.injectInputEvent минуя
тач-контроллер, и измеряет скорость инжекции, а не сенсор.

    python docs/measure_touch_rate.py 'trials/*.json'

Шаблон раскрывает сам скрипт, поэтому в bash его надо брать в кавычки: иначе
оболочка раскроет его первой и до расчёта дойдёт только первый файл.
"""
import glob
import json
import os
import statistics
import sys


def load(paths):
    for path in paths:
        with open(path, encoding="utf-8") as fh:
            trial = json.load(fh)
        if trial["completion_status"] != "UP":
            print(f"пропущена {trial['trial_id']}: статус {trial['completion_status']}")
            continue
        yield trial


def main(pattern):
    paths = sorted(glob.glob(pattern))
    if not paths:
        sys.exit(
            f"""шаблон {pattern!r} не совпал ни с одним файлом.
Путь раскрывается относительно текущей папки: {os.getcwd()}
Приложенный к отчёту образец: docs/sample/last-trial.json"""
        )

    trials = list(load(paths))
    if not trials:
        sys.exit(
            f"""найдено файлов: {len(paths)}, но ни одной попытки со статусом UP.
Частоту отсчётов можно мерить только по завершённому жесту: прерванная
попытка обрывается на середине и даёт заниженный результат."""
        )

    deltas, spans, samples, current = [], 0.0, 0, 0
    delivery = []
    per_trial = []
    for trial in trials:
        times = [s["touch_event_time_uptime_ns"] / 1e6 for s in trial["samples"]]
        span = times[-1] - times[0]
        gaps = [b - a for a, b in zip(times, times[1:])]
        deltas += gaps
        spans += span
        samples += len(times)
        current += trial["current_sample_count"]
        delivered = [
            s["touch_event_time_uptime_ns"] / 1e6
            for s in trial["samples"]
            if not s["is_historical"]
        ]
        delivery += [b - a for a, b in zip(delivered, delivered[1:])]
        per_trial.append((len(times) - 1) / span * 1000)
        print(
            f"{trial['trial_id']:>6}  отсчётов {len(times):4}  контакт {span:7.1f} мс"
            f"  {(len(times) - 1) / span * 1000:6.1f} Гц"
        )

    refresh = {t["display_profile"]["display_refresh_rate_hz"] for t in trials}
    print(f"\nпопыток {len(trials)}, отсчётов {samples}, контакт {spans:.0f} мс")
    print(f"частота отсчётов в потоке MotionEvent: {(samples - len(trials)) / spans * 1000:.1f} Гц")
    print(f"  по попыткам {min(per_trial):.1f}–{max(per_trial):.1f} Гц"
          f", 1/median(dt) {1000 / statistics.median(deltas):.1f} Гц")
    print(f"  dt мс: min {min(deltas):.2f} median {statistics.median(deltas):.2f} max {max(deltas):.2f}")
    print(f"период доставки MotionEvent: {statistics.median(delivery):.2f} мс"
          f" = {1000 / statistics.median(delivery):.1f} Гц; refresh дисплея {refresh}")
    print(f"отсчётов на одну доставку: {samples / current:.2f}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "docs/sample/*.json")
