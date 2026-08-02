LIB_DIR := .venv/lib/python3.12/site-packages/lightgbm/lib

.DEFAULT_GOAL := help
.PHONY: help sync print-lib version clean

help: ## показать цели этого Makefile
	@grep -hE '^[a-zA-Z][a-zA-Z_-]*:.*## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN{FS=":.*## "}{printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}'

# --no-deps: нужен только нативный бинарник из колеса, numpy и scipy не участвуют.
sync: ## поставить lightgbm по фиксированным хешам колёс
	uv venv --python 3.12 --quiet
	uv pip install --python .venv/bin/python --no-deps --require-hashes -r requirements.txt

print-lib: ## путь к нативной библиотеке, которую грузит FFM
	@f=$$(ls $(LIB_DIR)/lib_lightgbm.dylib $(LIB_DIR)/lib_lightgbm.so 2>/dev/null | head -1); \
		[ -n "$$f" ] || { echo "нет lib_lightgbm: сперва make -C native sync" >&2; exit 1; }; \
		echo "$$f"

version: ## версия поставленного lightgbm
	@cat .venv/lib/python3.12/site-packages/lightgbm/VERSION.txt

clean: ## удалить окружение
	rm -rf .venv
