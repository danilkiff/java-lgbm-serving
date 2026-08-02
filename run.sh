#!/usr/bin/env bash
# Bootstrap на чистой машине: нативная библиотека, эталоны, тесты, бенчмарки.
#
# Требует: uv, JDK 25 (см. serving/build.gradle.kts), рантайм OpenMP
# (Linux: libgomp; macOS: brew install libomp).
# Источник эталонов задаётся переменной REFS (по умолчанию - релиз
# lgbm-training); повторный запуск идемпотентен.
set -euo pipefail
cd "$(dirname "$0")"

say() { printf '\n==> %s\n' "$*"; }

say "проверка инструментов"
miss=0
for c in uv java make; do
	command -v "$c" >/dev/null 2>&1 || { echo "  нет в PATH: $c" >&2; miss=1; }
done
[ "$miss" = 0 ] || { echo "установи недостающее (или поправь PATH) и повтори" >&2; exit 1; }
java -version 2>&1 | head -1
uv --version

say "1/4 нативная lib_lightgbm            (make native)"
make native

say "2/4 эталоны паритета                 (make refs)"
make refs

say "3/4 паритет, конкуренция, юнит-тесты (make test)"
make test

say "4/4 бенчмарки                        (make bench)"
make bench

say "готово: библиотека на месте, эталоны получены, тесты и бенчи прошли"
