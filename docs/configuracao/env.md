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

## Frontend

Copie o exemplo:

```bash
cp frontend/.env.example frontend/.env.local
```

Variaveis com `NEXT_PUBLIC_` ficam disponiveis no navegador. Segredos nunca devem ficar no frontend.

## Infraestrutura

Quando o Docker Compose for criado, ele devera ler variaveis da raiz:

```bash
cp .env.example .env
docker compose up -d postgres
```
