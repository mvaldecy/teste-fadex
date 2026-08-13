# Docker e SMTP Base Design

## Objetivo

Preparar o ambiente de desenvolvimento para subir backend, frontend, PostgreSQL e Mailpit com Docker Compose, e adicionar uma base desacoplada de envio de e-mail no backend.

## Escopo

- Criar imagem Docker do backend Spring Boot.
- Criar imagem Docker do frontend Next.js.
- Expandir o `docker-compose.yml` com `backend`, `frontend` e `mailpit`, mantendo o `postgres`.
- Adicionar variaveis de ambiente para SMTP no monorepo e no backend.
- Configurar `spring.mail.*` para usar Mailpit em desenvolvimento.
- Criar um domínio/boundary de e-mail no backend para que services futuros enviem e-mails sem depender diretamente de Mailpit ou `JavaMailSender`.
- Atualizar comandos do `Makefile` para subir, parar, listar e acompanhar logs da stack completa.
- Atualizar documentacao de ambiente com as novas portas e comandos.

Fora deste escopo:

- Templates HTML de e-mail.
- Fila assíncrona de e-mails.
- Retentativas persistidas.
- WebSocket, SSE e serviços locais de IA.
- Envio real por provedor externo de SMTP.

## Arquitetura

O Docker Compose será o orquestrador local da aplicação. A stack padrão de desenvolvimento terá quatro serviços:

- `postgres`: banco PostgreSQL existente.
- `mailpit`: SMTP local e UI para inspeção de mensagens.
- `backend`: aplicação Spring Boot, conectada ao Postgres e ao Mailpit pela rede interna do Compose.
- `frontend`: aplicação Next.js, consumindo a API do backend.

O backend terá um boundary próprio de e-mail, separado dos services globais e dos domínios de chamado/usuário:

- `EmailSender`: contrato interno para envio.
- `EmailMessage`: estrutura de dados da mensagem.
- `SmtpEmailSender`: implementação usando `JavaMailSender`.
- `EmailDeliveryException`: exceção de infraestrutura para falha no envio.

Services de outros domínios deverão depender de `EmailSender`, não de `JavaMailSender`. Isso permite trocar Mailpit por SMTP real, fila ou provider externo sem alterar consumidores.

## Docker

### Backend

O backend usará um `backend/Dockerfile` multi-stage:

- Stage de build com JDK 21 e Gradle wrapper.
- Stage runtime com JRE 21.
- Porta interna `8080`.
- Profile padrão `dev`.
- Banco configurado por `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`.
- SMTP configurado por `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH` e `SMTP_STARTTLS_ENABLE`.

### Frontend

O frontend usará um `frontend/Dockerfile` multi-stage:

- Stage de dependências com `npm ci`.
- Stage de build com `npm run build`.
- Stage runtime com `npm run start`.
- Porta interna `3000`.
- `NEXT_PUBLIC_API_BASE_URL` apontando para `http://localhost:8080/api/v1` no navegador.

### Compose

Portas locais esperadas:

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:3000`
- PostgreSQL: `localhost:5432`
- Mailpit UI: `http://localhost:8025`
- Mailpit SMTP: `localhost:1025`

Na rede interna do Compose, o backend acessará:

- Postgres em `postgres:5432`
- Mailpit em `mailpit:1025`

## SMTP

O backend adicionará `spring-boot-starter-mail`.

Configuração base:

```properties
spring.mail.host=${SMTP_HOST:localhost}
spring.mail.port=${SMTP_PORT:1025}
spring.mail.username=${SMTP_USERNAME:}
spring.mail.password=${SMTP_PASSWORD:}
spring.mail.properties.mail.smtp.auth=${SMTP_AUTH:false}
spring.mail.properties.mail.smtp.starttls.enable=${SMTP_STARTTLS_ENABLE:false}
app.mail.from=${MAIL_FROM:no-reply@fadex.local}
```

No Compose, o backend usará:

```env
SMTP_HOST=mailpit
SMTP_PORT=1025
SMTP_AUTH=false
SMTP_STARTTLS_ENABLE=false
MAIL_FROM=no-reply@fadex.local
```

## Domínio de E-mail

O código de SMTP ficará separado por domínio em `br.org.fadex.helpdesk.mail`, e não em `br.org.fadex.helpdesk.service`.

Estrutura proposta:

```text
backend/src/main/java/br/org/fadex/helpdesk/mail/
├── EmailDeliveryException.java
├── EmailMessage.java
├── EmailSender.java
└── SmtpEmailSender.java
```

Contrato proposto:

```java
package br.org.fadex.helpdesk.mail;

public interface EmailSender {
	void send(EmailMessage message);
}
```

Mensagem proposta:

```java
package br.org.fadex.helpdesk.mail;

public record EmailMessage(
		String to,
		String subject,
		String text
) {
}
```

Implementação inicial:

```java
package br.org.fadex.helpdesk.mail;

@Service
public class SmtpEmailSender implements EmailSender {
	private final JavaMailSender mailSender;
	private final String from;

	public void send(EmailMessage message) {
		SimpleMailMessage email = new SimpleMailMessage();
		email.setFrom(from);
		email.setTo(message.to());
		email.setSubject(message.subject());
		email.setText(message.text());
		mailSender.send(email);
	}
}
```

Validações mínimas:

- `to` obrigatório e com formato de e-mail.
- `subject` obrigatório.
- `text` obrigatório.
- Falhas do `JavaMailSender` serão encapsuladas em `EmailDeliveryException`.

## Makefile

Novos comandos:

- `make stack-up`: sobe `postgres`, `mailpit`, `backend` e `frontend`.
- `make stack-down`: para a stack.
- `make stack-logs`: acompanha logs da stack.
- `make stack-ps`: lista status dos serviços.
- `make stack-build`: constrói imagens do backend e frontend.

Os comandos existentes de banco continuarão funcionando.

## Testes

Backend:

- Teste unitário do `SmtpEmailSender` verificando criação e envio da mensagem por `JavaMailSender`.
- Teste de validação de `EmailMessage` para campos obrigatórios e e-mail inválido.
- Teste de propriedades garantindo que `spring.mail.host`, `spring.mail.port` e `app.mail.from` tenham defaults resolvidos.

Frontend:

- `npm run lint`.
- `npm run build`.

Infra:

- `docker compose config` deve validar a configuração.
- Se Docker estiver disponível, `docker compose up -d --build postgres mailpit backend frontend` deve subir a stack e `docker compose ps` deve mostrar os serviços saudáveis ou em execução.

## Critérios de Aceite

- A worktree permanece isolada na branch `infra-realtime-ai-docker-dev`.
- `docker compose config` passa.
- A stack completa pode ser iniciada com `make stack-up`.
- Backend em container acessa Postgres usando `postgres:5432`.
- Backend em container envia SMTP para `mailpit:1025`.
- UI do Mailpit fica acessível em `http://localhost:8025`.
- Frontend fica acessível em `http://localhost:3000`.
- Backend fica acessível em `http://localhost:8080`.
- Services futuros podem injetar `br.org.fadex.helpdesk.mail.EmailSender` sem conhecer `JavaMailSender` ou Mailpit.
- Nenhuma classe de e-mail será criada em `br.org.fadex.helpdesk.service`; o boundary fica em `br.org.fadex.helpdesk.mail`.
