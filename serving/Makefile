PLATFORM := $(shell uname -s | tr 'A-Z' 'a-z')-$(shell uname -m | sed 's/x86_64/amd64/')

.DEFAULT_GOAL := help
.PHONY: help build test bench bench-smoke run dump clean

help: ## показать цели этого Makefile
	@grep -hE '^[a-zA-Z][a-zA-Z_-]*:.*## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN{FS=":.*## "}{printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

build: ## сборка
	./gradlew build

test: ## паритет, конкуренция, юнит-тесты
	./gradlew test

bench: ## бенчмарки JMH (профиль аллокаций: ARGS="-prof gc")
	./gradlew jmh -PjmhArgs="-f 1 -wi 5 -i 5 -w 2s -r 3s $(ARGS)"

bench-smoke: ## быстрый прогон бенчей для CI (компиляция плюс короткий прогон)
	./gradlew jmh -PjmhArgs="-f 1 -wi 1 -i 2 -w 1s -r 1s"

run: ## запустить scorer на фикстуре (доп. флаги через ARGS=)
	./gradlew run --console=plain --args="-model serving/fixtures/model.txt -codes serving/fixtures/codes.json $(ARGS)"

dump: ## дамп предсказаний этой платформы в refs/pred.java.<os>-<arch>.csv
	./gradlew dump -PdumpArgs="dump refs/model.txt refs/holdout.csv refs/pred.java.$(PLATFORM).csv"

clean: ## удалить артефакты сборки
	./gradlew clean
