# Notificacoes por E-mail e Alerta de Chamado ALTA — Plano

Design: `docs/backend/2026-08-15-notificacoes-email-design.md`.
Branch: `feature(backend)/notificacoes-email-e-alerta`, a partir de `dev`.

Regra de cada etapa: teste primeiro, `make backend-test` verde, commit pequeno em portugues.

## Etapa 1 — Defeito: criacao de chamado nao notifica ninguem

Entra sozinha e primeiro, porque e requisito obrigatorio do desafio.

1. `NotificationAudience.UsersAndRoles(Set<UUID>, Set<Role>)` + teste em
   `NotificationAudienceTest`.
2. Teste em `TicketServiceTest`:
   - criacao publica `CHAMADO_ATUALIZADO` com audiencia solicitante + `Role.ADMIN`;
   - criacao de chamado nascido ALTA publica tambem `CHAMADO_ALTA_PRIORIDADE`;
   - criacao de chamado MEDIA nao publica o alerta.
3. `TicketService.create()` publica apos o `save`, reusando os metodos privados de publicacao.
4. Delta em `docs/backend/api.md`: `CHAMADO_ATUALIZADO` tambem na criacao; audiencia nova.

Criterio: suite verde; alerta de ALTA coberto no caminho de criacao.

## Etapa 2 — Infra de e-mail HTML

1. `spring-boot-starter-thymeleaf` no `backend/build.gradle`. Se o build quebrar de forma nao
   trivial no Boot 4.1, abandonar o Thymeleaf e montar o HTML em text blocks (timebox).
2. `EmailMessage` ganha `html`; `text` continua obrigatorio (fallback do multipart).
3. `SmtpEmailSender` passa a usar `MimeMessage` + `MimeMessageHelper(msg, true, "UTF-8")` com
   `setText(text, html)`. Falha continua virando `EmailDeliveryException` — quem trata agora e o
   listener.
4. `templates/email/layout.html` (fragmento: cabecalho, conteudo, rodape, CSS inline) e um template
   por notificacao.
5. `EmailTemplateRenderer` sobre `TemplateEngine`, com teste que renderiza e verifica escape de
   `<` em titulo de chamado.

Criterio: `EmailMessageTest` e `SmtpEmailSenderTest` atualizados e verdes; renderer testado.

## Etapa 3 — Eventos de dominio e listeners

1. `notification/event/`: `TicketNotificationEvent`, `TicketNotificationType`,
   `NotificationRecipient`, `UserCreatedNotificationEvent`.
2. `TicketSseNotificationListener`: evento de dominio -> `NotificationMessage` -> `dispatch`,
   com try/catch e log. Pos-commit e assincrono.
3. `EmailNotificationListener`: evento de dominio -> lista de `EmailMessage`, um `send` por
   destinatario, cada um em try/catch. Escuta tambem `NotificationMessage` para
   `JOB_IA_FALHOU`.
4. `TicketEmailComposer`: assunto, texto puro e HTML por tipo de evento; aplica a regra "nunca
   notifique quem causou a acao" e resolve os ADMIN via `UserRepository.findByRole`.
5. `TicketService` e `TicketCommentService` passam a publicar o evento de dominio no lugar do
   `NotificationMessage`. Diff minimo no `TicketService` — so o corpo dos metodos privados de
   publicacao e a assinatura da chamada.
6. Testes: um por linha da matriz, incluindo os casos negativos (autor da acao nao recebe;
   chamado normal criado nao gera e-mail).

Criterio: matriz coberta por teste; nenhum `emailSender` injetado em service de dominio.

## Etapa 4 — Senha provisoria fora da transacao

1. Teste: `UserService.create()` publica `UserCreatedNotificationEvent` e **nao** chama
   `EmailSender`; falha de e-mail nao impede a criacao.
2. `UserService` perde a dependencia de `EmailSender`.
3. `UserServiceTest` atualizado.

Criterio: com o Mailpit fora do ar, `POST /api/v1/users` responde `201`.

## Etapa 5 — Cobertura de camada web

`TicketControllerTest` com `@WebMvcTest(TicketController.class)`, `@MockitoBean TicketService`,
`SecurityMockMvcRequestPostProcessors.jwt()`:

- `PATCH /status`: `200`; `400` com corpo invalido (bean validation); `409` de `ConflictException`;
  `401` sem token.
- `PATCH /assignee`: `200`; `400` sem `assigneeId`; `409` quando ja ha responsavel.
- `DELETE /assignee`: `200`; `409` quando nao ha responsavel; `404` quando o chamado nao existe.

Criterio: primeiro `@WebMvcTest` do projeto verde, cobrindo status, validacao e mapeamento de erro.

## Etapa 6 — Documentacao e verificacao real

1. Secao de e-mails no `docs/backend/api.md`: matriz de gatilho x destinatario, formato
   multipart, regra do autor, comportamento em falha de SMTP.
2. Stack de pe (`make db-up`, backend local) e verificacao no Mailpit (`http://localhost:8025`,
   API em `/api/v1/messages`): disparar criacao de usuario, atribuicao de responsavel, troca de
   status, resolucao/fechamento e comentario, conferindo destinatario, assunto e renderizacao
   HTML **e** texto.
3. Registrar no relatorio o que foi verificado ponta a ponta e o que ficou so em teste.

## Riscos

- **Thymeleaf no Boot 4.1**: timebox declarado; fallback e text block.
- **`TicketService` e ponto de merge com a frente de IA**: manter o diff restrito aos metodos de
  publicacao.
- **Testes de `@SpringBootTest` com SMTP real**: o profile de teste nao sobrescreve
  `spring.mail.*`, entao aponta para `localhost:1025`. Os testes de notificacao usam mock de
  `EmailSender`, sem tocar a rede.
- **`JOB_IA_FALHOU`**: verificavel so por teste nesta frente (nada o publica aqui).
