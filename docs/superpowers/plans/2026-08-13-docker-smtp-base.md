# Docker e SMTP Base Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Subir backend, frontend, PostgreSQL e Mailpit com Docker Compose e adicionar um boundary de e-mail desacoplado no backend.

**Architecture:** O Compose orquestra a stack local com rede interna entre `backend`, `frontend`, `postgres` e `mailpit`. O backend expõe um boundary `br.org.fadex.helpdesk.mail` com contrato `EmailSender`, mensagem `EmailMessage` e implementação SMTP baseada em Spring Mail.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Gradle Wrapper, Next.js 15, Node 22 Alpine, PostgreSQL 17 Alpine, Mailpit, Docker Compose.

## Global Constraints

- Mensagens de commit, títulos de PR e descrições de PR devem ser escritos em português.
- A branch de trabalho deve seguir o padrão do monorepo com escopo entre parênteses.
- Código de e-mail deve ficar em `br.org.fadex.helpdesk.mail`, nunca em `br.org.fadex.helpdesk.service`.
- Services futuros devem depender de `EmailSender`, não de `JavaMailSender` nem de Mailpit.
- WebSocket, SSE, IA local, templates HTML, fila assíncrona e SMTP real ficam fora deste plano.

---

## File Structure

- Create `backend/Dockerfile`: build multi-stage do backend e runtime Java 21.
- Create `backend/.dockerignore`: excluir caches e envs locais do contexto Docker.
- Create `frontend/Dockerfile`: build multi-stage do frontend e runtime Next.js production.
- Create `frontend/.dockerignore`: excluir `node_modules`, `.next` e envs locais do contexto Docker.
- Modify `docker-compose.yml`: adicionar `mailpit`, `backend` e `frontend`; manter `postgres`.
- Modify `Makefile`: adicionar comandos `stack-*`.
- Modify `.env.example`: adicionar portas e variáveis SMTP/root usadas pelo Compose.
- Modify `backend/.env.example`: adicionar variáveis SMTP locais.
- Modify `backend/build.gradle`: adicionar `spring-boot-starter-mail`.
- Modify `backend/src/main/resources/application.properties`: mapear `spring.mail.*` e `app.mail.from`.
- Modify `backend/src/test/java/br/org/fadex/helpdesk/config/ApplicationPropertiesTest.java`: cobrir defaults de SMTP.
- Create `backend/src/main/java/br/org/fadex/helpdesk/mail/EmailMessage.java`: mensagem validada.
- Create `backend/src/main/java/br/org/fadex/helpdesk/mail/EmailSender.java`: contrato.
- Create `backend/src/main/java/br/org/fadex/helpdesk/mail/SmtpEmailSender.java`: implementação SMTP.
- Create `backend/src/main/java/br/org/fadex/helpdesk/mail/EmailDeliveryException.java`: erro de infraestrutura.
- Create `backend/src/test/java/br/org/fadex/helpdesk/mail/EmailMessageTest.java`: validações da mensagem.
- Create `backend/src/test/java/br/org/fadex/helpdesk/mail/SmtpEmailSenderTest.java`: envio e encapsulamento de falha.
- Modify `docs/configuracao/env.md`: documentar stack, portas e SMTP.

### Task 1: Docker Compose e Imagens

**Files:**
- Create: `backend/Dockerfile`
- Create: `frontend/Dockerfile`
- Modify: `docker-compose.yml`
- Modify: `Makefile`
- Modify: `.env.example`

**Interfaces:**
- Consumes: existing `backend/gradlew`, `frontend/package-lock.json`, `docker-compose.yml`.
- Produces: commands `make stack-up`, `make stack-down`, `make stack-build`, `make stack-logs`, `make stack-ps`.

- [ ] **Step 1: Atualizar `.env.example`**

```env
POSTGRES_DB=fadex_helpdesk
POSTGRES_USER=fadex
POSTGRES_PASSWORD=fadex
POSTGRES_PORT=5432

BACKEND_PORT=8080
FRONTEND_PORT=3000
NEXT_PUBLIC_APP_NAME=Fadex Helpdesk
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1

MAILPIT_SMTP_PORT=1025
MAILPIT_UI_PORT=8025
SMTP_HOST=mailpit
SMTP_PORT=1025
SMTP_AUTH=false
SMTP_STARTTLS_ENABLE=false
MAIL_FROM=no-reply@fadex.local
```

- [ ] **Step 2: Criar `backend/Dockerfile`**

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle gradle.properties ./
COPY src src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 3: Criar `frontend/Dockerfile`**

```dockerfile
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
ARG NEXT_PUBLIC_APP_NAME="Fadex Helpdesk"
ARG NEXT_PUBLIC_API_BASE_URL="http://localhost:8080/api/v1"
ENV NEXT_PUBLIC_APP_NAME=${NEXT_PUBLIC_APP_NAME}
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
COPY --from=deps /app/node_modules node_modules
COPY . .
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app
ARG NEXT_PUBLIC_APP_NAME="Fadex Helpdesk"
ARG NEXT_PUBLIC_API_BASE_URL="http://localhost:8080/api/v1"
ENV NODE_ENV=production
ENV NEXT_PUBLIC_APP_NAME=${NEXT_PUBLIC_APP_NAME}
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
COPY --from=build /app/package.json package.json
COPY --from=build /app/package-lock.json package-lock.json
COPY --from=build /app/.next .next
COPY --from=build /app/node_modules node_modules
EXPOSE 3000
CMD ["npm", "run", "start"]
```

- [ ] **Step 4: Atualizar `docker-compose.yml`**

Add services `mailpit`, `backend`, `frontend`; configure `backend` with `DB_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB:-fadex_helpdesk}` and `SMTP_HOST=${SMTP_HOST:-mailpit}`; configure `frontend` with `NEXT_PUBLIC_API_BASE_URL=http://localhost:${BACKEND_PORT:-8080}/api/v1`.

- [ ] **Step 5: Atualizar `Makefile`**

Add phony targets and commands:

```makefile
stack-build: env ## Constroi imagens da stack completa
	@docker compose build

stack-up: env ## Sobe backend, frontend, PostgreSQL e Mailpit
	@docker compose up -d --build postgres mailpit backend frontend

stack-down: ## Para a stack Docker Compose
	@docker compose down

stack-logs: ## Exibe logs da stack Docker Compose
	@docker compose logs -f

stack-ps: ## Mostra status da stack Docker Compose
	@docker compose ps
```

- [ ] **Step 6: Validar Compose**

Run: `docker compose config`
Expected: exit code `0`.

### Task 2: Configuração SMTP do Backend

**Files:**
- Modify: `backend/build.gradle`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/.env.example`
- Modify: `backend/src/test/java/br/org/fadex/helpdesk/config/ApplicationPropertiesTest.java`

**Interfaces:**
- Consumes: Spring environment and `spring-boot-starter-mail`.
- Produces: properties `spring.mail.host`, `spring.mail.port`, `app.mail.from`.

- [ ] **Step 1: Escrever teste de propriedades**

Add assertions to `deveCarregarProfileDeTesteComBancoEmMemoria`:

```java
assertThat(environment.getProperty("spring.mail.host")).isEqualTo("localhost");
assertThat(environment.getProperty("spring.mail.port")).isEqualTo("1025");
assertThat(environment.getProperty("app.mail.from")).isEqualTo("no-reply@fadex.local");
```

- [ ] **Step 2: Rodar teste e confirmar falha**

Run: `make backend-test`
Expected: FAIL because SMTP properties are not configured.

- [ ] **Step 3: Adicionar dependência e propriedades**

Add to `backend/build.gradle`:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-mail'
```

Add to `application.properties`:

```properties
spring.mail.host=${SMTP_HOST:localhost}
spring.mail.port=${SMTP_PORT:1025}
spring.mail.username=${SMTP_USERNAME:}
spring.mail.password=${SMTP_PASSWORD:}
spring.mail.properties.mail.smtp.auth=${SMTP_AUTH:false}
spring.mail.properties.mail.smtp.starttls.enable=${SMTP_STARTTLS_ENABLE:false}
app.mail.from=${MAIL_FROM:no-reply@fadex.local}
```

- [ ] **Step 4: Atualizar `backend/.env.example`**

```env
SMTP_HOST=localhost
SMTP_PORT=1025
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_AUTH=false
SMTP_STARTTLS_ENABLE=false
MAIL_FROM=no-reply@fadex.local
```

- [ ] **Step 5: Rodar teste e confirmar sucesso**

Run: `make backend-test`
Expected: PASS.

### Task 3: Boundary de E-mail

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/mail/EmailMessage.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/mail/EmailSender.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/mail/SmtpEmailSender.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/mail/EmailDeliveryException.java`
- Create: `backend/src/test/java/br/org/fadex/helpdesk/mail/EmailMessageTest.java`
- Create: `backend/src/test/java/br/org/fadex/helpdesk/mail/SmtpEmailSenderTest.java`

**Interfaces:**
- Produces: `EmailSender#send(EmailMessage message)`.
- Produces: `EmailMessage(String to, String subject, String text)` with constructor validation.
- Produces: `EmailDeliveryException(String message, Throwable cause)`.

- [ ] **Step 1: Escrever testes de `EmailMessage`**

```java
assertThat(new EmailMessage("usuario@fadex.org.br", "Assunto", "Texto").to()).isEqualTo("usuario@fadex.org.br");
assertThatThrownBy(() -> new EmailMessage("", "Assunto", "Texto")).isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> new EmailMessage("email-invalido", "Assunto", "Texto")).isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> new EmailMessage("usuario@fadex.org.br", "", "Texto")).isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> new EmailMessage("usuario@fadex.org.br", "Assunto", "")).isInstanceOf(IllegalArgumentException.class);
```

- [ ] **Step 2: Rodar testes e confirmar falha**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.mail.EmailMessageTest`
Expected: FAIL because `EmailMessage` does not exist.

- [ ] **Step 3: Implementar `EmailMessage` e `EmailSender`**

Implement validation with `Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")`, blank trimming, and `IllegalArgumentException` for invalid input.

- [ ] **Step 4: Rodar testes de `EmailMessage`**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.mail.EmailMessageTest`
Expected: PASS.

- [ ] **Step 5: Escrever testes de `SmtpEmailSender`**

Use `org.springframework.mail.javamail.JavaMailSender` mocked with Mockito. Capture `SimpleMailMessage` and assert `from`, `to`, `subject`, `text`. Add one test where `mailSender.send` throws `MailSendException` and assert `EmailDeliveryException`.

- [ ] **Step 6: Rodar testes e confirmar falha**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.mail.SmtpEmailSenderTest`
Expected: FAIL because `SmtpEmailSender` and `EmailDeliveryException` do not exist.

- [ ] **Step 7: Implementar `SmtpEmailSender` e `EmailDeliveryException`**

Constructor:

```java
public SmtpEmailSender(JavaMailSender mailSender, @Value("${app.mail.from}") String from)
```

Method:

```java
public void send(EmailMessage message)
```

Catch `MailException` and throw `new EmailDeliveryException("Nao foi possivel enviar o e-mail.", exception)`.

- [ ] **Step 8: Rodar testes do boundary**

Run: `cd backend && ./gradlew test --tests br.org.fadex.helpdesk.mail.*`
Expected: PASS.

### Task 4: Documentação e Verificação

**Files:**
- Modify: `docs/configuracao/env.md`

**Interfaces:**
- Consumes: Makefile commands and Compose ports from previous tasks.
- Produces: documentation for local stack and Mailpit.

- [ ] **Step 1: Atualizar documentação**

Document commands:

```bash
make stack-build
make stack-up
make stack-ps
make stack-logs
make stack-down
```

Document URLs:

```text
Frontend: http://localhost:3000
Backend: http://localhost:8080
Swagger: http://localhost:8080/swagger-ui.html
Mailpit: http://localhost:8025
PostgreSQL: localhost:5432
```

- [ ] **Step 2: Rodar validações finais**

Run:

```bash
make backend-test
cd frontend && npm run lint
cd frontend && npm run build
docker compose config
```

Expected: all commands exit `0`.

- [ ] **Step 3: Commit**

```bash
git add .env.example Makefile docker-compose.yml backend frontend docs/configuracao/env.md docs/superpowers/plans/2026-08-13-docker-smtp-base.md
git commit -m "feat: adiciona stack docker e smtp base"
```
