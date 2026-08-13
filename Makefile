SHELL := /bin/bash

.DEFAULT_GOAL := help

SDKMAN_INIT := $(HOME)/.sdkman/bin/sdkman-init.sh
BACKEND_DIR := backend

.PHONY: help sdk env backend-test backend-build backend-run backend-clean test build run clean

help: ## Lista os comandos disponiveis
	@awk 'BEGIN {FS = ":.*##"; printf "\nComandos disponiveis:\n"} /^[a-zA-Z0-9_-]+:.*##/ {printf "  make %-18s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@printf "\n"

sdk: ## Ativa o Java definido no .sdkmanrc e exibe as versoes
	@source "$(SDKMAN_INIT)" && sdk env && java -version && javac -version

env: ## Cria arquivos locais de ambiente a partir dos exemplos
	@[ -f .env ] || cp .env.example .env
	@[ -f backend/.env ] || cp backend/.env.example backend/.env
	@[ -f frontend/.env.local ] || cp frontend/.env.example frontend/.env.local
	@printf "Arquivos de ambiente locais prontos.\n"

backend-test: ## Executa os testes do backend
	@source "$(SDKMAN_INIT)" && sdk env >/dev/null && cd "$(BACKEND_DIR)" && ./gradlew test

backend-build: ## Gera o build do backend
	@source "$(SDKMAN_INIT)" && sdk env >/dev/null && cd "$(BACKEND_DIR)" && ./gradlew build

backend-run: ## Executa o backend localmente
	@source "$(SDKMAN_INIT)" && sdk env >/dev/null && \
	if [ -f backend/.env ]; then set -a && source backend/.env && set +a; fi && \
	cd "$(BACKEND_DIR)" && ./gradlew bootRun

backend-clean: ## Remove artefatos de build do backend
	@source "$(SDKMAN_INIT)" && sdk env >/dev/null && cd "$(BACKEND_DIR)" && ./gradlew clean

test: backend-test ## Alias para backend-test

build: backend-build ## Alias para backend-build

run: backend-run ## Alias para backend-run

clean: backend-clean ## Alias para backend-clean
