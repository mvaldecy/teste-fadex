# Diretrizes do Repositório

## Estrutura do Projeto e Organização

Este é um monorepo da aplicação Fadex Helpdesk. O backend fica em `backend/src/main/java/br/org/fadex/helpdesk`, organizado por camadas: `controller`, `service`, `repository`, `model`, `security`, `config` e `exception`. Testes do backend ficam em `backend/src/test/java/br/org/fadex/helpdesk`. Migrações do banco ficam em `backend/src/main/resources/db/migration`.

O frontend é uma aplicação Next.js em `frontend`. Páginas do App Router ficam em `frontend/app`; código compartilhado fica em `frontend/src`, incluindo `features`, `services`, `stores`, `schemas`, `types`, `routes` e `config`.

Use também as diretrizes específicas de cada área:

- `backend/AGENTS.md` para arquitetura e convenções do Spring Boot.
- `frontend/AGENTS.md` para arquitetura e convenções do Next.js/React.

## Comandos de Build, Teste e Desenvolvimento

Use o `Makefile` da raiz como interface principal:

- `make env`: cria arquivos locais de ambiente a partir dos exemplos.
- `make db-up`: sobe o PostgreSQL local com Docker Compose.
- `make backend-run`: executa o backend Spring Boot com o Java da `.sdkmanrc`.
- `make frontend-dev`: executa o servidor de desenvolvimento do Next.js.
- `make backend-test` ou `make test`: roda os testes do backend com Gradle.
- `make frontend-lint`: executa o ESLint do frontend.
- `make build`: gera build do backend e do frontend.

## Estilo de Código e Nomenclatura

O backend usa Java 21 e Spring Boot. O frontend usa TypeScript estrito, Next.js, React, Tailwind CSS e alias `@/*`. Siga as convenções específicas nos `AGENTS.md` internos antes de alterar cada área.

## Diretrizes de Testes

Os testes do backend usam JUnit Platform via Gradle. Coloque testes no pacote correspondente em `backend/src/test/java` e nomeie como `*Test.java`, por exemplo `TicketServiceTest.java`. Prefira testes focados em serviços, controllers, configuração e persistência para comportamentos alterados.

No frontend, lint e build são as verificações automatizadas atuais. Rode `make frontend-lint` e `make frontend-build` ao alterar UI, rotas, schemas ou cliente de API.

## Commits e Pull Requests

As convenções estão em `docs/configuracao/convencoes-git.md`. Escreva commits, títulos e descrições de PR em português. Prefira prefixos objetivos com escopo do monorepo, como `feat(backend):`, `fix(frontend):`, `docs(configuracao):`, `test(backend):`, `refactor(backend):` e `chore(infra):`.

Branches de trabalho devem partir de `dev` e incluir escopo, por exemplo `feature(backend)/auth-jwt` ou `fix(frontend)/login-validacao`. Mantenha PRs pequenos e inclua objetivo, mudanças, passos de teste e observações. Use squash merge nas branches protegidas.

PRs devem ser abertos como draft quando ainda estiverem em validação. Use PR stacks quando uma entrega depender de outra e deixe a base/dependência clara na descrição.

## Documentação

Mantenha documentação em `docs` separada por subdomínio, como `docs/configuracao`, `docs/backend` e `docs/frontend`. Atualize `docs/backend/api.md` quando alterar contrato de endpoint para que o frontend não precise inferir comportamento pelo código.

## Segurança e Configuração

Não versione arquivos reais de ambiente: `.env`, `backend/.env` ou `frontend/.env.local`. Segredos nunca devem usar `NEXT_PUBLIC_`, pois esses valores ficam expostos no navegador.

O backend deve rodar localmente fora do Docker durante desenvolvimento. O banco local deve ser preferencialmente dockerizado via `make db-up`. Integrações de IA local podem ter services preparados, mas implementação pendente deve ficar isolada e sem bloquear CRUDs básicos.
