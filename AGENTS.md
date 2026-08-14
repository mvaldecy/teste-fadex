# Diretrizes do Repositório

## Estrutura do Projeto e Organização

Este é um monorepo da aplicação Fadex Helpdesk. O backend fica em `backend/src/main/java/br/org/fadex/helpdesk`, organizado por camadas: `controller`, `service`, `repository`, `model`, `security`, `config` e `exception`. Testes do backend ficam em `backend/src/test/java/br/org/fadex/helpdesk`. Migrações do banco ficam em `backend/src/main/resources/db/migration`.

O frontend é uma aplicação Next.js em `frontend`. Páginas do App Router ficam em `frontend/app`; código compartilhado fica em `frontend/src`, incluindo `features`, `services`, `stores`, `schemas`, `types`, `routes` e `config`.

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

O backend usa Java 21 e Spring Boot. Mantenha pacotes sob `br.org.fadex.helpdesk`; use PascalCase para classes, camelCase para campos e métodos, e sufixo `Dto` para objetos de transferência. Siga nomes por camada, como `TicketController`, `TicketService` e `TicketRepository`.

O frontend usa TypeScript estrito, Next.js, React, Tailwind CSS e alias `@/*`. Use kebab-case para arquivos de rotas e componentes quando esse padrão já existir, como `login-form.tsx`, e camelCase para variáveis e funções.

## Diretrizes de Testes

Os testes do backend usam JUnit Platform via Gradle. Coloque testes no pacote correspondente em `backend/src/test/java` e nomeie como `*Test.java`, por exemplo `TicketServiceTest.java`. Prefira testes focados em serviços, controllers, configuração e persistência para comportamentos alterados.

No frontend, lint e build são as verificações automatizadas atuais. Rode `make frontend-lint` e `make frontend-build` ao alterar UI, rotas, schemas ou cliente de API.

## Commits e Pull Requests

As convenções estão em `docs/configuracao/convencoes-git.md`. Escreva commits, títulos e descrições de PR em português. Prefira prefixos objetivos como `feat:`, `fix:`, `docs:`, `test:`, `refactor:` e `chore:`.

Branches de trabalho devem partir de `dev` e incluir escopo, por exemplo `feature(backend)/auth-jwt` ou `fix(frontend)/login-validacao`. Mantenha PRs pequenos e inclua objetivo, mudanças, passos de teste e observações. Use squash merge nas branches protegidas.

## Segurança e Configuração

Não versione arquivos reais de ambiente: `.env`, `backend/.env` ou `frontend/.env.local`. Segredos nunca devem usar `NEXT_PUBLIC_`, pois esses valores ficam expostos no navegador.
