# Variaveis de Ambiente

## Caminho rapido: o assistente de instalacao

Para subir tudo com um comando, sem preencher `.env` na mao:

```bash
./setup.sh
```

O `setup.sh` e o caminho oficial e funciona em qualquer sistema onde o bash roda:

- Linux e macOS: `./setup.sh` no terminal.
- Windows: `./setup.sh` pelo Git Bash ou pelo WSL. Quem preferir clicar pode usar o `setup.cmd`, que localiza o bash do Git for Windows (ou o WSL) e chama o script.
- `make setup` faz a mesma coisa, mas e apenas conveniencia para quem tem `make` instalado — no Windows normalmente nao tem.

O assistente confere o Docker, escolhe portas livres (subindo de 1 em 1 quando a padrao esta ocupada), gera o `JWT_SECRET`, escreve o `.env`, sobe a stack com `docker compose up -d --build` e verifica se backend e frontend responderam. Ele e idempotente: rodar de novo reaproveita o que ja esta no `.env`.

Opcoes uteis:

- `./setup.sh --dry-run`: percorre o fluxo inteiro imprimindo os comandos, sem tocar no Docker.
- `./setup.sh --yes`: aceita todos os padroes, sem perguntas.
- `ENV_FILE=/caminho/arquivo ./setup.sh`: grava em outro arquivo de ambiente.

Duas variaveis nunca devem ser editadas a mao depois disso, porque dependem das portas escolhidas: `NEXT_PUBLIC_API_BASE_URL` (entra na imagem do frontend como build arg) e `CORS_ALLOWED_ORIGINS` (precisa listar a porta real do frontend). Se mudar uma porta, rode o `setup.sh` de novo em vez de ajustar o `.env` manualmente.

O `make env` continua existindo e e o caminho manual: ele so copia os `.env.example` e nao valida portas nem sobe nada.

## Arquivos

- `.env.example`: variaveis de infraestrutura do monorepo, usadas principalmente pelo Docker Compose.
- `backend/.env.example`: variaveis esperadas pelo Spring Boot.
- `frontend/.env.example`: variaveis esperadas pelo Next.js.

Arquivos reais de ambiente nao devem ser commitados:

- `.env`
- `backend/.env`
- `frontend/.env.local`

## Backend

O projeto usa Java 21. Com SDKMAN, entre na raiz do repositorio e rode:

```bash
sdk env
```

Se o JDK ainda nao estiver instalado:

```bash
sdk install java 21.0.8-tem
sdk env
```

Copie o exemplo:

```bash
cp backend/.env.example backend/.env
```

O Spring Boot le variaveis de ambiente, mas nao carrega `.env` automaticamente. Para rodar pelo terminal usando o arquivo:

```bash
set -a
source backend/.env
set +a
cd backend
./gradlew bootRun
```

Na IDE, configure as mesmas variaveis no run configuration.

Com o `Makefile`, os comandos principais ficam:

```bash
make sdk
make env
make backend-test
make backend-run
```

Aliases disponiveis:

```bash
make test
make build
make run
make clean
```

## Frontend

Copie o exemplo:

```bash
cp frontend/.env.example frontend/.env.local
```

Variaveis com `NEXT_PUBLIC_` ficam disponiveis no navegador. Segredos nunca devem ficar no frontend.

## Infraestrutura

O Docker Compose le variaveis da raiz:

```bash
cp .env.example .env
docker compose up -d postgres
```

Com o `Makefile`, use:

```bash
make env
make db-up
make db-ps
make db-logs
make db-down
```

Para remover tambem o volume local do PostgreSQL:

```bash
make db-reset
```

## Stack Docker Completa

A stack local completa sobe PostgreSQL, Mailpit, backend e frontend:

```bash
make env
make stack-build
make stack-up
make stack-ps
```

Para acompanhar logs:

```bash
make stack-logs
```

Para parar a stack:

```bash
make stack-down
```

Servicos e portas padrao:

```text
Frontend: http://localhost:3000
Backend: http://localhost:8080
Swagger: http://localhost:8080/swagger-ui.html
Mailpit UI: http://localhost:8025
Mailpit SMTP: localhost:1025
PostgreSQL: localhost:5432
```

No Docker Compose, o backend acessa o banco pela rede interna em `postgres:5432` e o SMTP local em `mailpit:1025`.

## SMTP Local

O backend possui configuracao SMTP base por variaveis de ambiente:

```env
SMTP_HOST=localhost
SMTP_PORT=1025
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_AUTH=false
SMTP_STARTTLS_ENABLE=false
MAIL_FROM=no-reply@fadex.local
```

Ao rodar o backend fora do Docker, use `SMTP_HOST=localhost`. Ao rodar dentro do Docker Compose, use `SMTP_HOST=mailpit`.

As mensagens enviadas em desenvolvimento ficam visiveis na UI do Mailpit:

```text
http://localhost:8025
```
