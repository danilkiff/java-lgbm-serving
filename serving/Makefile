.DEFAULT_GOAL := help
.PHONY: help build test run clean

help: ## показать цели этого Makefile
	@grep -hE '^[a-zA-Z][a-zA-Z_-]*:.*## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN{FS=":.*## "}{printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

build: ## сборка
	./gradlew build

test: ## паритет, конкуренция, юнит-тесты
	./gradlew test

run: ## запустить scorer на фикстуре (доп. флаги через ARGS=)
	./gradlew run --console=plain --args="-model serving/fixtures/model.txt -codes serving/fixtures/codes.json $(ARGS)"

clean: ## удалить артефакты сборки
	./gradlew clean
