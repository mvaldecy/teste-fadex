# Notificações em Tempo Real com SSE Design

## Objetivo

Construir o motor de notificações em tempo real do backend usando Server-Sent Events, com um único ponto de extensão para que os subdomínios publiquem notificações sem conhecer o transporte.

Esta entrega é o motor. As integrações de domínio (chamado criado, comentário adicionado, status alterado) ficam para depois do merge de `feature(backend)/auth-rbac-historico`, em worktree própria.

## Escopo

- Endpoint autenticado `GET /api/v1/notifications/stream` devolvendo `text/event-stream`.
- Registry em memória de conexões abertas, com identidade capturada no momento da assinatura.
- Entrega apenas após o commit da transação que originou a notificação.
- Resolução de audiência por usuário, por role ou broadcast.
- Heartbeat periódico para manter conexões ociosas vivas.
- Limpeza de conexões em conclusão, timeout e erro.
- Testes de registry, fanout, controller e barreira transacional.
- Atualização de `docs/backend/api.md` com o contrato do stream, feita na fase de implementação junto com o endpoint, conforme exige `AGENTS.md`. Este documento de design não altera contrato publicado.

Fora deste escopo:

- Publicação de eventos a partir de `TicketService` e `TicketCommentService`.
- Regras de RBAC sobre quem pode ver qual chamado.
- Persistência de notificações e histórico de leitura.
- Replay de eventos perdidos por `Last-Event-ID`.
- Propagação entre múltiplas instâncias da aplicação.
- Consumo no frontend.

## Contexto Técnico Verificado

O backend usa Spring Boot `4.1.0` com `spring-boot-starter-webmvc`, ou seja, stack Servlet e não WebFlux. A dependência resolvida é Spring Framework `7.0.8`, onde `SseEmitter` vive em `org.springframework.web.servlet.mvc.method.annotation` e expõe `send(SseEventBuilder)`, além de `event()` com `id`, `name`, `comment`, `reconnectTime` e `data`. Os callbacks `onCompletion`, `onTimeout` e `onError` vêm de `ResponseBodyEmitter`.

Autenticação é stateless por `Bearer` no header, com claims `userId` e `role` (`SecurityConfig`, `AuthenticatedUserService`). O `EventSource` nativo do navegador não envia headers customizados, então o cliente consumirá o stream via `fetch` com `ReadableStream`. A consequência para o backend é a melhor possível: nenhuma superfície nova de autenticação, `SecurityConfig` permanece inalterada e `anyRequest().authenticated()` já protege o endpoint.

## Arquitetura

Nenhum subdomínio chama SSE diretamente. Quem precisa notificar publica um evento de aplicação; o motor entrega depois do commit.

```
Service de domínio (futuro)          Motor (esta entrega)
  publishEvent(NotificationMessage)
              |
              v
       NotificationDispatcher     @TransactionalEventListener(phase = AFTER_COMMIT)
              |
              v
       NotificationService        resolve audiência
              |
              v
       NotificationEmitterRegistry  Map<UUID, Set<SseSubscription>>
              |
              v
       SseEmitter.send(...)
```

Duas decisões estruturais sustentam o desenho.

A identidade do assinante é capturada no momento da assinatura e guardada junto do emitter. `AuthenticatedUserService` lê o `SecurityContextHolder`, que é thread-local: na thread que faz o fanout, o contexto pertence a outro usuário ou está vazio. Resolver identidade no envio produziria `UnauthorizedException` ou, pior, vazamento entre usuários.

A entrega acontece somente em `AFTER_COMMIT`. `TicketService.create` e `TicketCommentService.create` são `@Transactional`; emitir dentro da transação permitiria que o cliente recebesse notificação de um registro que sofreu rollback, ou que recarregasse a lista antes do commit ficar visível.

## Componentes

Todo o motor vive em `br.org.fadex.helpdesk.sse`, com subpastas por camada. O módulo segue o precedente já estabelecido no backend por `mail` e pelo módulo `ai` da branch de triagem: uma capacidade técnica autocontida fica no próprio pacote em vez de se espalhar pelas camadas globais. As camadas internas mantêm o vocabulário de `backend/AGENTS.md`.


| Arquivo | Responsabilidade |
| --- | --- |
| `sse/controller/NotificationController.java` | `GET /api/v1/notifications/stream`, `produces = text/event-stream`, retorna `SseEmitter` |
| `sse/service/NotificationService.java` | `subscribe` capturando identidade e `dispatch` resolvendo audiência |
| `sse/service/NotificationEmitterRegistry.java` | Guarda conexões abertas e remove nas três condições de término |
| `sse/service/NotificationDispatcher.java` | `@TransactionalEventListener(AFTER_COMMIT)` que aciona o fanout |
| `sse/service/NotificationHeartbeatScheduler.java` | `@Scheduled` enviando comentário de keep-alive |
| `sse/model/SseSubscription.java` | Conexão e identidade capturada: `connectionId`, `userId`, `role`, `emitter` |
| `sse/model/NotificationMessage.java` | `record(String eventId, String eventName, Object data, NotificationAudience audience)` |
| `sse/model/NotificationAudience.java` | `sealed interface` com `Users`, `Roles` e `Everyone` |
| `sse/model/NotificationConnectionDto.java` | Payload do evento inicial de conexão |
| `sse/config/SchedulingConfig.java` | `@EnableScheduling` |
| `sse/config/AsyncConfig.java` | `@EnableAsync` e o bean `sseNotificationExecutor`, executor dedicado do fanout de notificações |

O fanout (`NotificationDispatcher.onNotificationMessage`) roda em `@Async("sseNotificationExecutor")` em vez de na própria thread que fez o commit. `sseNotificationExecutor` é um `ThreadPoolTaskExecutor` dedicado e nomeado (core 2, máximo 4, fila 500, prefixo de thread `sse-notification-`), declarado em `sse/config/AsyncConfig.java`, para não competir com nem depender do executor default do Spring. Qualquer exceção do fanout é capturada e logada em `NotificationDispatcher`, sem propagar para o publicador do evento — a transação já commitou, então uma falha no envio não pode virar um `500` numa requisição de domínio que já persistiu com sucesso.

`NotificationAudience` é o que mantém o motor independente da branch de RBAC. O filtro por role usa o claim já capturado no JWT, sem depender de `AccessControlService`. Quando o histórico mergear, o service de domínio publica `new NotificationMessage(..., new Users(destinatários))` e nenhuma linha do motor muda.

## Contrato HTTP

```
GET /api/v1/notifications/stream
Accept: text/event-stream
Authorization: Bearer <jwt>
```

Resposta `200 OK` com `Content-Type: text/event-stream`:

```
event: CONEXAO_ESTABELECIDA
id: 4f1c8b2a-...
retry: 5000
data: {"connectionId":"4f1c8b2a-...","serverTime":"2026-08-14T15:54:58"}

: ping

event: CHAMADO_CRIADO
id: 9a2e...
data: {...}
```

Sem token válido a resposta é `401` no formato padrão de erro da API, produzido por `RestAuthenticationEntryPoint`.

O comentário `: ping` é o heartbeat. Linhas iniciadas por dois-pontos são ignoradas pelo parser SSE e servem apenas para manter a conexão viva.

## Ciclo de Vida da Conexão

Na assinatura, o controller resolve `userId` e `role` pelo JWT, cria o `SseEmitter` com timeout configurado, registra os três callbacks de limpeza e envia o evento inicial de conexão. O evento inicial confirma ao cliente que o stream está ativo, algo que o navegador não sinaliza sozinho.

`onCompletion`, `onTimeout` e `onError` removem a assinatura do registry. Os três são obrigatórios: se faltar um, cada reconexão do cliente deixa um emitter órfão acumulando memória.

Falha de envio a uma conexão remove aquela assinatura e não interrompe o fanout das demais. Cliente desconectado é situação esperada, não erro de aplicação.

## Concorrência de Escrita

A thread do heartbeat e a thread de despacho podem escrever no mesmo emitter ao mesmo tempo. Verificado no código-fonte do Spring Framework 7.0.8: `ResponseBodyEmitter.send` adquire um `writeLock` interno antes de qualquer escrita, então a serialização já é garantida pelo framework e o motor não precisa de sincronização própria.

O mesmo método revela um detalhe que muda o tratamento de erro: antes de escrever, ele executa `Assert.state(!this.complete, ...)`, ou seja, enviar para um emitter já concluído lança `IllegalStateException`, não `IOException`. A limpeza de conexões precisa capturar as duas, sob pena de o heartbeat derrubar o agendador ao encontrar uma conexão encerrada em corrida.

## Configuração

| Propriedade | Padrão | Função |
| --- | --- | --- |
| `notifications.sse.timeout` | `1800000` | Timeout do emitter em milissegundos |
| `notifications.sse.heartbeat-interval` | `20000` | Intervalo do keep-alive em milissegundos |
| `notifications.sse.reconnect-time` | `5000` | Valor de `retry` sugerido ao cliente |
| `spring.mvc.async.request-timeout` | não definida | Deixada em branco de propósito: o timeout do próprio emitter é aplicado à requisição assíncrona |

A propriedade `spring.mvc.async.request-timeout` é do tipo `java.time.Duration` e não tem valor padrão no Boot 4.1: sem ela, vale o timeout do container. Como o valor definido no `SseEmitter` é aplicado à requisição assíncrona, o controle fica em `notifications.sse.timeout` e a propriedade global permanece intocada.

## Decisões e Limites

Sem replay por `Last-Event-ID`. O registry é volátil e manter buffer por conexão adiciona política de retenção e memória sem benefício imediato. O cliente que reconecta refaz a busca da lista pelo endpoint REST correspondente. Decisão explícita, revisável quando houver notificação persistida.

Registry em memória significa instância única. Com múltiplas réplicas, cada uma só alcança quem está conectado nela. O caminho de evolução é `LISTEN/NOTIFY` do PostgreSQL, que já está na stack, mas não faz parte desta entrega.

Sobre custo por conexão: com `SseEmitter` o servlet entra em modo assíncrono e a thread do Tomcat é liberada entre os envios. O custo de uma conexão ociosa é socket e memória, não uma thread bloqueada. O teto prático é `server.tomcat.max-connections`, não `max-threads`.

Não há proxy reverso em `infra/` nesta branch. Quando houver, o servidor à frente do backend precisa desabilitar buffering de resposta para este endpoint, ou o stream chega ao cliente em blocos.

## Testes

- `NotificationEmitterRegistryTest`: múltiplas conexões do mesmo usuário; remoção em conclusão, timeout e erro; acesso concorrente.
- `NotificationServiceTest`: fanout correto para cada tipo de audiência; assinatura que lança `IOException` é removida sem interromper as demais e sem chamar `completeWithError` (desconexão é esperada, o container já dispara o error dispatch sozinho); assinatura que lança `IllegalStateException` é removida e tem o emitter encerrado via `completeWithError` (tanto no fanout quanto no heartbeat), porque cobre tanto o emitter já concluído quanto uma falha de serialização num emitter vivo, e sem isso o socket ficaria mudo até o timeout; identidade usada é a capturada, não a do contexto de segurança corrente.
- `NotificationDispatcherTest`: nada é entregue antes do commit; entrega ocorre em `AFTER_COMMIT`; o listener carrega `@Async("sseNotificationExecutor")`.
- `NotificationDispatcherIntegrationTest`: verificação de entrega pós-commit usa `verify(..., timeout(2000))` do Mockito em vez de `verify` síncrono, porque o fanout agora roda em `@Async` e a entrega não é mais garantida na mesma thread nem no mesmo instante do commit; a asserção de não entrega antes do commit continua síncrona, sem timeout, porque o `AFTER_COMMIT` só aciona o listener depois do commit independente do `@Async`.
- `NotificationHeartbeatSchedulerTest`: keep-alive alcança todas as conexões vivas; conexão morta é removida no lugar de propagar exceção.
- `NotificationControllerTest` com `@WebMvcTest`: `200` com `text/event-stream`, `401` sem token, identidade extraída dos claims.

Baseline da worktree verificado antes do início: `make backend-test` conclui com `BUILD SUCCESSFUL`.

## Integração Futura

Depois do merge de `feature(backend)/auth-rbac-historico`, a integração se resume a publicar a mensagem no mesmo ponto em que o `TicketEvent` é gravado, reaproveitando `TicketEventType` como nome do evento e `TicketEventMinDto` como payload. Histórico e notificação passam a ser o mesmo fato de domínio com dois destinos, sem taxonomia paralela. A audiência sai das regras de RBAC daquela branch.

## Follow-ups Conhecidos

Itens identificados na revisão final do motor e conscientemente adiados — não fazem parte desta entrega:

- **Identidade capturada sobrevive ao token.** O timeout do emitter (`notifications.sse.timeout`, 30 min) é independente da expiração do JWT (1 h). Mudança de papel, logout ou expiração do token não encerram um stream já aberto: a assinatura continua ativa com a identidade capturada no momento da conexão até o emitter estourar o próprio timeout. Correção sugerida: derivar o timeout do emitter do tempo restante do token no momento da assinatura.
- **Sem limite de streams simultâneos por usuário.** Nada impede que o mesmo usuário acumule assinaturas indefinidamente (várias abas, várias reconexões sem limpeza do lado do cliente), cada uma consumindo um lugar no registry e uma conexão no servidor.
- **Heartbeat roda no scheduler default de thread única.** `NotificationHeartbeatScheduler` usa o `TaskScheduler` padrão do Spring Boot, dimensionado para uma única thread. Um socket travado durante o envio do keep-alive atrasa o keep-alive dos demais assinantes, porque `@EnableScheduling` é global e não há pool dedicado para essa tarefa.
- **`fallbackExecution = true` suaviza a garantia transacional.** A garantia de entrega do motor não é "entrega apenas após o commit", e sim "entrega após o commit, quando houver uma transação ativa". Quem publicar `NotificationMessage` fora de um contexto `@Transactional` recebe entrega imediata, sem a barreira do `AFTER_COMMIT`.
- **Regra de uso da audiência para a integração futura.** Eventos de escopo de chamado — criação, comentário, mudança de status — devem sempre usar `Users` com os destinatários explícitos daquele chamado. `Roles` serve para anúncios amplos por papel, sem vínculo com um registro específico. `Everyone` é broadcast puro. Um evento de chamado publicado com `Everyone` (ou com `Roles` além dos papéis que deveriam ter acesso àquele chamado específico) vazaria a existência do chamado, e potencialmente metadado sensível do payload, para todo usuário conectado sem relação com o registro. O motor não valida essa regra porque não conhece RBAC; a responsabilidade é de quem publicar na branch de integração.
