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

| Arquivo | Responsabilidade |
| --- | --- |
| `controller/NotificationController.java` | `GET /api/v1/notifications/stream`, `produces = text/event-stream`, retorna `SseEmitter` |
| `service/NotificationService.java` | `subscribe` capturando identidade e `dispatch` resolvendo audiência |
| `service/NotificationEmitterRegistry.java` | Guarda conexões abertas e remove nas três condições de término |
| `service/NotificationDispatcher.java` | `@TransactionalEventListener(AFTER_COMMIT)` que aciona o fanout |
| `service/NotificationHeartbeatScheduler.java` | `@Scheduled` enviando comentário de keep-alive |
| `model/notification/SseSubscription.java` | Conexão e identidade capturada: `connectionId`, `userId`, `role`, `emitter` |
| `model/notification/NotificationMessage.java` | `record(String eventId, String eventName, Object data, NotificationAudience audience)` |
| `model/notification/NotificationAudience.java` | `sealed interface` com `Users`, `Roles` e `Everyone` |
| `model/notification/NotificationConnectionDto.java` | Payload do evento inicial de conexão |
| `config/SchedulingConfig.java` | `@EnableScheduling` |

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
- `NotificationServiceTest`: fanout correto para cada tipo de audiência; assinatura que lança `IOException` é removida sem interromper as demais; identidade usada é a capturada, não a do contexto de segurança corrente.
- `NotificationDispatcherTest`: nada é entregue antes do commit; entrega ocorre em `AFTER_COMMIT`.
- `NotificationHeartbeatSchedulerTest`: keep-alive alcança todas as conexões vivas; conexão morta é removida no lugar de propagar exceção.
- `NotificationControllerTest` com `@WebMvcTest`: `200` com `text/event-stream`, `401` sem token, identidade extraída dos claims.

Baseline da worktree verificado antes do início: `make backend-test` conclui com `BUILD SUCCESSFUL`.

## Integração Futura

Depois do merge de `feature(backend)/auth-rbac-historico`, a integração se resume a publicar a mensagem no mesmo ponto em que o `TicketEvent` é gravado, reaproveitando `TicketEventType` como nome do evento e `TicketEventMinDto` como payload. Histórico e notificação passam a ser o mesmo fato de domínio com dois destinos, sem taxonomia paralela. A audiência sai das regras de RBAC daquela branch.
