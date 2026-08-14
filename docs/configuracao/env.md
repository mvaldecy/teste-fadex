# Variaveis de Ambiente

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
