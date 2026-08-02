#!/usr/bin/env bash
# Получение эталонов паритета в каталог назначения.
#
#   refs.sh local:<path> <dst>    скопировать из чекаута репозитория эталонов
#   refs.sh release:<tag> <dst>   скачать архив релиза и сверить хеш по refs.sha256
#
# Эталоны не коммитятся: расхождение копии с источником было бы невидимым.
set -euo pipefail
cd "$(dirname "$0")/.."

REPO=${REFS_REPO:-danilkiff/lgbm-reference}
FILES="model.txt holdout.csv ref_raw.csv ref_contrib.csv meta.json"

src=${1:?"usage: refs.sh <local:path|release:tag> <dst>"}
dst=${2:?"usage: refs.sh <local:path|release:tag> <dst>"}
kind=${src%%:*}
arg=${src#*:}

case "$kind" in
local)
	[ -d "$arg" ] || { echo "нет каталога эталонов: $arg" >&2; exit 1; }
	mkdir -p "$dst"
	for f in $FILES; do
		[ -f "$arg/$f" ] || { echo "нет файла $f в $arg" >&2; exit 1; }
		cp "$arg/$f" "$dst/$f"
	done
	echo "эталоны из $arg: $(echo $FILES | wc -w | tr -d ' ') файлов"
	;;
release)
	archive="refs-$arg.tar.gz"
	want=$(grep -E "  $archive\$" refs.sha256 2>/dev/null | cut -d' ' -f1 || true)
	[ -n "$want" ] || { echo "тег $arg не описан в refs.sha256" >&2; exit 1; }
	tmp=$(mktemp -d)
	trap 'rm -rf "$tmp"' EXIT
	curl -fsSL -o "$tmp/$archive" "https://github.com/$REPO/releases/download/$arg/$archive"
	got=$(shasum -a 256 "$tmp/$archive" | cut -d' ' -f1)
	[ "$got" = "$want" ] || { echo "sha256 архива $got != $want" >&2; exit 1; }
	mkdir -p "$dst"
	tar -xzf "$tmp/$archive" -C "$dst" --strip-components=1
	echo "эталоны $REPO@$arg"
	;;
*)
	echo "неизвестный источник: $src (ожидалось local:<path> или release:<tag>)" >&2
	exit 1
	;;
esac
