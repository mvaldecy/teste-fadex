SHELL := /bin/bash

.DEFAULT_GOAL := help

SDKMAN_INIT := $(HOME)/.sdkman/bin/sdkman-init.sh
BACKEND_DIR := backend
FRONTEND_DIR := frontend

.PHONY: help sdk env db-up db-down db-logs db-ps db-reset backend-test backend-build backend-run backend-clean frontend-install frontend-dev frontend-lint frontend-build test build run clean


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

db-up: env ## Sobe o PostgreSQL local com Docker Compose
	@docker compose up -d postgres

db-down: ## Para o PostgreSQL local
	@docker compose down

db-logs: ## Exibe logs do PostgreSQL local
	@docker compose logs -f postgres

db-ps: ## Mostra status dos servicos Docker Compose
	@docker compose ps

db-reset: ## Remove containers e volume local do PostgreSQL
	@docker compose down -v

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

frontend-install: ## Instala dependencias do frontend
	@cd "$(FRONTEND_DIR)" && npm install

frontend-dev: ## Executa o frontend localmente
	@cd "$(FRONTEND_DIR)" && npm run dev

frontend-lint: ## Executa o lint do frontend
	@cd "$(FRONTEND_DIR)" && npm run lint

frontend-build: ## Gera o build do frontend
	@cd "$(FRONTEND_DIR)" && npm run build

test: backend-test ## Alias para backend-test

build: backend-build frontend-build ## Gera builds do backend e frontend

run: backend-run ## Alias para backend-run

clean: backend-clean ## Alias para backend-clean
