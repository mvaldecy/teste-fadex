# Notificacoes por E-mail e Alerta de Chamado ALTA — Design

Frente API/Notificacoes. Base: `dev` apos o merge das tres frentes. Branch:
`feature(backend)/notificacoes-email-e-alerta`.

## Problema

1. **Defeito de requisito.** `TicketService.create()` grava o chamado, registra o evento de
   historico e enfileira os jobs de IA, mas nao publica nada no SSE. Consequencias:
   - um chamado que **nasce** ALTA nunca dispara `CHAMADO_ALTA_PRIORIDADE`, porque o unico ponto
     que avalia a prioridade e `applyClassification`, e o guard exige `previousPriority != ALTA`;
   - a lista dos outros usuarios nao atualiza em tempo real quando alguem abre chamado.
2. **Nao existe notificacao por e-mail.** O SMTP existe (`mail/EmailSender`, `SmtpEmailSender`,
   `EmailMessage`) e e usado uma unica vez, no `UserService.create()`.
3. **O unico e-mail existente e transacional e sincrono.** `UserService.create()` chama
   `emailSender.send(...)` dentro do `@Transactional`, e o `SmtpEmailSender` lanca
   `EmailDeliveryException`. Com o Mailpit fora do ar, criar usuario falha e o usuario nao e criado.
4. **Camada web sem cobertura.** Nao existe nenhum `@WebMvcTest` no projeto.

## Decisoes

### D1 — Um gatilho de dominio, dois transportes

Em vez de espalhar `emailSender.send()` pelos services, cada mutacao publica **um** evento de
dominio; dois listeners pos-commit derivam os transportes:

```
TicketService / TicketCommentService / UserService
        |  applicationEventPublisher.publishEvent(evento de dominio)
        v
  @TransactionalEventListener(AFTER_COMMIT) + @Async
        |                                   |
        v                                   v
 TicketSseNotificationListener      EmailNotificationListener
   -> NotificationService.dispatch    -> EmailSender.send
```

O padrao e o mesmo ja usado pelo `NotificationDispatcher`. O `NotificationDispatcher` **nao e
alterado**: ele continua atendendo quem publica `NotificationMessage` direto — hoje, a frente de IA.

### D2 — O evento carrega um retrato imutavel, nao a entidade

Os listeners rodam em outra thread, depois do commit, sem `EntityManager` aberto
(`spring.jpa.open-in-view=false`). Passar a entidade `Ticket` significaria lazy loading em sessao
fechada. O evento carrega:

- o `TicketMinDto` ja montado (payload do SSE e fonte do texto do e-mail);
- `NotificationRecipient(id, name, email)` do solicitante e do responsavel (este, opcional);
- o id de quem causou a acao;
- a prioridade anterior, para o alerta de ALTA;
- um texto de detalhe (descricao da troca de status, trecho do comentario).

### D3 — Nunca notificar quem causou a acao (regra de e-mail, nao de SSE)

O filtro por autor vale **so para o e-mail**. No SSE, o solicitante continua recebendo
`CHAMADO_ATUALIZADO` do proprio chamado — e contrato publicado e e o que atualiza a tela de quem
esta com o chamado aberto. Sem esse filtro no e-mail, o ADMIN recebe e-mail do proprio comentario.

### D4 — Audiencia da criacao: solicitante + ADMIN

`publishTicketUpdated` hoje mira `Users{solicitante, responsavel}`. Na criacao nao ha responsavel,
entao a unica destinataria seria a propria pessoa que acabou de criar o chamado — o que nao corrige
"a lista dos outros usuarios nao atualiza".

Como `resolveFilterByRole` da a todo ADMIN visao de todos os chamados, a criacao precisa alcancar o
papel ADMIN. Solucao: um variante nova no sealed `NotificationAudience`:

```java
record UsersAndRoles(Set<UUID> userIds, Set<Role> roles) implements NotificationAudience
```

Aditiva (o contrato do SSE e `includes(userId, role)`, nao ha switch exaustivo sobre o sealed) e
evita o frame duplicado que a alternativa — publicar duas mensagens — entregaria a um ADMIN
solicitante.

### D5 — Matriz de e-mails

| Gatilho | Destinatario | Template |
| --- | --- | --- |
| Chamado criado com prioridade ALTA | todos os ADMIN | `chamado-alta-prioridade` |
| Responsavel atribuido | o responsavel | `responsavel-atribuido` |
| Status alterado | solicitante | `status-alterado` |
| Comentario adicionado | a contraparte (solicitante se quem comentou foi ADMIN; responsavel se foi o solicitante) | `comentario-adicionado` |
| Chamado resolvido ou fechado | solicitante | `status-alterado` (assunto proprio) |
| Job de IA falhou | todos os ADMIN | `job-ia-falhou` |
| Usuario criado (senha provisoria) | o usuario criado | `senha-provisoria` |

Criacao de chamado **normal** nao gera e-mail — so SSE. Sao poucos admins, e todo chamado virando
e-mail treina as pessoas a ignorar a caixa.

Comentario do solicitante em chamado **sem responsavel** nao gera e-mail: a matriz nomeia o
responsavel como contraparte. Fica registrado como lacuna consciente.

### D6 — E-mail HTML com alternativa em texto

`EmailMessage` passa a carregar `text` e `html`. O `SmtpEmailSender` troca `SimpleMailMessage` por
`MimeMessage` + `MimeMessageHelper(message, true, "UTF-8")` e `helper.setText(text, html)`, que
produz `multipart/alternative`: cliente que bloqueia HTML cai no texto.

O HTML e renderizado por Thymeleaf (`spring-boot-starter-thymeleaf`), com um fragmento de layout
(`email/layout.html`: cabecalho, bloco de conteudo, rodape) e um template por notificacao. CSS
**inline**, sem `<style>`, sem imagem externa e sem fonte remota — limitacao de cliente de e-mail.

Escape: `th:text` escapa por padrao. `th:utext` nao e usado em nada vindo do banco — titulo de
chamado e texto de comentario sao texto livre digitado por gente.

Risco assumido: o starter do Thymeleaf tambem liga resolucao de views no MVC. Numa API REST isso e
inocuo, mas e o primeiro suspeito se alguma rota que devolve JSON mudar de comportamento.
Timebox: se o Thymeleaf conflitar com o Spring Boot 4.1, o fallback e montar o HTML em text blocks.

### D7 — Falha de e-mail nao desfaz operacao de negocio

Todo envio acontece **depois do commit** e **fora da thread da requisicao**. O listener captura a
excecao e registra em log. Consequencia direta: com o Mailpit fora do ar, criar usuario passa a
funcionar — o usuario e criado, e so o e-mail se perde, com o erro no log.

Contrapartida aceita: a senha provisoria fica so no log de erro se o SMTP estiver fora. Para o
escopo do desafio, isso e melhor do que a criacao falhar; o ADMIN reenvia recriando o usuario.

### D8 — `JOB_IA_FALHOU` sem tocar em `ai/**`

A fronteira proibe editar `ai/**`, e o gatilho de falha de job vive la. O e-mail para os ADMIN e
derivado do proprio `NotificationMessage` que a frente de IA publica: o
`EmailNotificationListener` escuta `NotificationMessage` e reage apenas a
`NotificationEventName.JOB_IA_FALHOU`. Zero acoplamento com o codigo da outra frente e zero
conflito de merge.

Limite honesto: no worktree desta frente nada publica esse evento ainda, entao ele so pode ser
verificado por teste com mensagem sintetica, nao ponta a ponta no Mailpit.

### D9 — `@WebMvcTest` com `jwt()` em vez de `JwtDecoder` mockado

A fatia web nao carrega os `@Component` de seguranca (`RestAuthenticationEntryPoint`,
`RestAccessDeniedHandler`, `PasswordChangeRequiredFilter`) nem a propriedade de CORS. Em vez de
reconstruir a cadeia inteira, os testes usam `SecurityMockMvcRequestPostProcessors.jwt()`, que
dispensa `JwtDecoder` real. `@MockBean` nao existe mais no Boot 4: usar `@MockitoBean`.

### D10 — Os dois transportes dividem o executor do SSE

Nenhum pool novo: o e-mail roda no `sseNotificationExecutor` (2–4 threads, fila de 500). Um SMTP
lento atrasa o despacho SSE que estiver na fila. Aceito no escopo do desafio, com pool proprio para
e-mail como primeiro ajuste se a latencia aparecer.

## Escopo negativo

- Nao tocar em `ai/**`, `frontend/**`.
- Nao criar migration `V5` (reservada a frente de IA). Nao ha migration nesta entrega.
- Nao alterar `NotificationDispatcher`.
- Preferencia de notificacao por usuario (opt-out), digest e reenvio ficam para o proximo ciclo.

## Observacao sobre "chamado aberto com prioridade ALTA"

`TicketCreationDto` tem apenas `title` e `description`; `TicketMapper.toEntity` fixa
`TicketPriority.MEDIA` e `ClassificationOrigin.PENDENTE`. Ou seja: **pela API, hoje, nenhum chamado
nasce ALTA** — a prioridade ALTA so aparece depois, quando a triagem chama `applyClassification`.

O ramo de criacao e implementado mesmo assim, porque:

- e o requisito literal do desafio ("alerta quando um chamado ALTA for aberto");
- passa a valer no instante em que o DTO ganhar `priority` ou a classificacao passar a rodar de
  forma sincrona na criacao;
- o construtor de `Ticket` e o `toEntity` de cinco argumentos ja aceitam prioridade.

O caminho pelo qual o alerta realmente dispara hoje continua sendo `applyClassification`, e esse
caminho ganha e-mail junto com o SSE.
