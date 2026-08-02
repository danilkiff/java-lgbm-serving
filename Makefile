REFS ?= release:v0.2.0

.DEFAULT_GOAL := help
.PHONY: help native refs print-env build test bench bench-smoke run dump clean

help: ## показать цели этого Makefile
	@grep -hE '^[a-zA-Z][a-zA-Z_-]*:.*## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN{FS=":.*## "}{printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

native: ## поставить нативную lib_lightgbm (uv, версия из native/requirements.txt)
	@$(MAKE) -C native sync

refs: ## получить эталоны паритета: REFS=release:<tag> или REFS=local:<path>
	@scripts/refs.sh "$(REFS)" refs

print-env: ## разрешённые пути нативной библиотеки и эталонов (отладка окружения)
	@printf 'lib_lightgbm: native/%s (%s)\n' \
		"$$($(MAKE) -s -C native print-lib)" "$$($(MAKE) -s -C native version)"
	@printf 'эталоны:      %s\n' "$$(ls refs 2>/dev/null | tr '\n' ' ' || echo 'нет - make refs')"

build: ## сборка
	@$(MAKE) -C serving build

test: ## паритет, конкуренция, юнит-тесты
	@$(MAKE) -C serving test

bench: ## бенчмарки JMH
	@$(MAKE) -C serving bench

bench-smoke: ## быстрый прогон бенчей для CI
	@$(MAKE) -C serving bench-smoke

run: ## запустить scorer на фикстуре (доп. флаги через ARGS=)
	@$(MAKE) -C serving run ARGS="$(ARGS)"

dump: ## дамп предсказаний этой платформы в refs/
	@$(MAKE) -C serving dump

clean: ## удалить артефакты сборки и эталоны
	@$(MAKE) -C serving clean
	rm -rf refs
