.DEFAULT_GOAL := help
.PHONY: help build test clean

help: ## показать цели этого Makefile
	@grep -hE '^[a-zA-Z][a-zA-Z_-]*:.*## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN{FS=":.*## "}{printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

build: ## сборка
	./gradlew build

test: ## паритет, конкуренция, юнит-тесты
	./gradlew test

clean: ## удалить артефакты сборки
	./gradlew clean
