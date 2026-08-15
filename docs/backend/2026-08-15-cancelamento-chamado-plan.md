# Cancelamento de chamado — plano de implementação

> **Para agentes executores:** use `superpowers:executing-plans` ou
> `superpowers:subagent-driven-development`. Os passos usam `- [ ]` para acompanhamento.

**Objetivo:** fechar a última lacuna de requisito obrigatório do desafio — cancelamento de chamado —
como exclusão lógica por status, preservando histórico e sem distorcer nenhum indicador.

**Arquitetura:** novo `TicketStatus.CANCELADO` entra na matriz `TicketStatusTransition` como estado
terminal; um único método de domínio `TicketService.cancel` concentra autorização, transição, evento
e notificação; `DELETE /api/v1/tickets/{id}` é a porta HTTP. Os indicadores passam a excluir o
cancelado do SLA explicitamente e ganham testes que prendem as demais exclusões, hoje corretas por
acidente.

**Stack:** Java 21, Spring Boot, JPA/Hibernate, Flyway (Postgres em produção, H2 nos testes), JUnit 5
+ Mockito + AssertJ; Next.js 15 / React / TypeScript estrito / Tailwind no frontend.

**Spec:** `docs/backend/2026-08-15-cancelamento-chamado-design.md`

## Restrições globais

- Migração **V7**. Nenhuma alteração em V1–V6.
- Proibido tocar: `backend/src/main/java/br/org/fadex/helpdesk/ai/client/**`,
  `.../ai/job/**`, `backend/src/main/resources/application.properties`, `docker-compose.yml`.
- Piso da suíte: **292 testes, 57 classes, 0 falhas** (medido nesta branch antes da primeira
  alteração). `make backend-test` verde antes de **cada** commit.
- Frontend: `make frontend-lint` e `make frontend-build` antes de cada commit que toque `frontend/`.
- Commits pequenos, em português, sem `Co-Authored-By:` e sem `Claude-Session:`.
- A stack está de pé (backend `:8080`, frontend `:3001`). Não derrubar. Rebuild só via
  `docker compose up -d --build backend`.
- Rótulo do status novo: exatamente `"Cancelado"`. Rótulo do evento novo: exatamente
  `"Chamado cancelado"`.

---

### Task 1: Status `CANCELADO`, matriz de transições e migração V7

**Arquivos:**
- Modificar: `backend/src/main/java/br/org/fadex/helpdesk/model/enums/TicketStatus.java`
- Modificar: `backend/src/main/java/br/org/fadex/helpdesk/model/enums/TicketEventType.java`
- Modificar: `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketStatusTransition.java`
- Criar: `backend/src/main/resources/db/migration/V7__add_ticket_cancellation.sql`
- Teste: `backend/src/test/java/br/org/fadex/helpdesk/model/ticket/TicketStatusTransitionTest.java`
  (existente), `backend/src/test/java/br/org/fadex/helpdesk/repository/TicketPersistenceTest.java`
  (existente)

**Interfaces:**
- Produz: `TicketStatus.CANCELADO`, `TicketEventType.CHAMADO_CANCELADO`,
  `TicketStatusTransition.allowedFrom(CANCELADO) == Set.of()`.

- [ ] **Passo 1: teste da matriz (falha)**

Em `TicketStatusTransitionTest`:

```java
@Test
void deveExporCancelamentoComoTransicaoDeAbertoEEmAndamento() {
	assertThat(TicketStatusTransition.isAllowed(TicketStatus.ABERTO, TicketStatus.CANCELADO)).isTrue();
	assertThat(TicketStatusTransition.isAllowed(TicketStatus.EM_ANDAMENTO, TicketStatus.CANCELADO)).isTrue();
}

@Test
void naoDeveCancelarChamadoResolvido() {
	assertThat(TicketStatusTransition.isAllowed(TicketStatus.RESOLVIDO, TicketStatus.CANCELADO)).isFalse();
}

@Test
void deveTratarCanceladoComoEstadoTerminal() {
	assertThat(TicketStatusTransition.allowedFrom(TicketStatus.CANCELADO)).isEmpty();
}
```

- [ ] **Passo 2: teste de persistência (falha por constraint)**

Em `TicketPersistenceTest`, chamado salvo em `CANCELADO` com evento `CHAMADO_CANCELADO`. Sem a V7 o
H2 recusa com `DataIntegrityViolationException` — é exatamente o erro que a V7 elimina:

```java
@Test
void devePersistirChamadoCanceladoComEventoDeCancelamento() {
	User requester = userRepository.save(new User(
			"Paulo Solicitante", "paulo@fadex.org.br", "senha-com-hash", Role.SOLICITANTE));
	Ticket ticket = new Ticket("Chamado aberto por engano", "Abri no lugar errado.", requester);
	ticket.changeStatus(TicketStatus.CANCELADO);
	Ticket saved = ticketRepository.save(ticket);
	ticketEventRepository.save(new TicketEvent(
			saved, requester, TicketEventType.CHAMADO_CANCELADO, "Chamado cancelado."));

	assertThat(ticketRepository.findById(saved.getId()))
			.get()
			.extracting(Ticket::getStatus)
			.isEqualTo(TicketStatus.CANCELADO);
	assertThat(ticketEventRepository.findAll())
			.extracting(TicketEvent::getType)
			.contains(TicketEventType.CHAMADO_CANCELADO);
}
```

Conferir antes as assinaturas reais dos construtores de `Ticket` e `TicketEvent` no próprio arquivo
de teste e ajustar a chamada — o teste vizinho já constrói os dois.

- [ ] **Passo 3: rodar e ver falhar**

`cd backend && ./gradlew test --tests '*TicketStatusTransitionTest' --tests '*TicketPersistenceTest'`
Esperado: falha de compilação em `TicketStatus.CANCELADO` / `TicketEventType.CHAMADO_CANCELADO`.

- [ ] **Passo 4: enums**

`TicketStatus`: acrescentar `CANCELADO("Cancelado")` após `FECHADO`.
`TicketEventType`: acrescentar `CHAMADO_CANCELADO("Chamado cancelado")` ao fim.

- [ ] **Passo 5: matriz**

Em `buildAllowed()`, incluir `TicketStatus.CANCELADO` nos conjuntos de `ABERTO` e `EM_ANDAMENTO`, e
acrescentar `allowed.put(TicketStatus.CANCELADO, Set.of());`. Atualizar o javadoc da classe, que hoje
diz que só `FECHADO` mapeia para conjunto vazio.

- [ ] **Passo 6: V7**

`V7__add_ticket_cancellation.sql`:

```sql
-- Cancelamento de chamado: exclusao logica por status, preservando historico.
alter table tickets drop constraint ck_tickets_status;

alter table tickets add constraint ck_tickets_status
    check (status in ('ABERTO', 'EM_ANDAMENTO', 'RESOLVIDO', 'FECHADO', 'CANCELADO'));

-- O evento novo tambem e barrado por check, redefinido pela V4. Sem este bloco o cancelamento
-- falharia em runtime, na gravacao do historico, e nao no build.
alter table ticket_events drop constraint ck_ticket_events_type;

alter table ticket_events add constraint ck_ticket_events_type check (type in (
    'CHAMADO_CRIADO',
    'COMENTARIO_ADICIONADO',
    'STATUS_ALTERADO',
    'RESPONSAVEL_ATRIBUIDO',
    'RESPONSAVEL_REMOVIDO',
    'PRIORIDADE_ALTERADA',
    'CATEGORIA_ALTERADA',
    'CLASSIFICACAO_ATUALIZADA',
    'CHAMADO_CANCELADO'
));
```

- [ ] **Passo 7: suíte inteira verde**

`make backend-test` → contar testes no XML de resultado; esperado ≥ 292 + 4, 0 falhas.

- [ ] **Passo 8: commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/model backend/src/main/resources/db/migration/V7__add_ticket_cancellation.sql backend/src/test
git commit -m "feat(backend): adiciona status cancelado e a transicao terminal"
```

---

### Task 2: `TicketService.cancel` — autorização, evento e notificação

**Arquivos:**
- Modificar: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java`
- Teste: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java`

**Interfaces:**
- Consome: `TicketStatusTransition.isAllowed`, `TicketEventType.CHAMADO_CANCELADO` (Task 1).
- Produz: `public TicketDto cancel(UUID id)`; método privado
  `applyStatusChange(Ticket ticket, TicketStatus newStatus, TicketEventType eventType, String description)`
  reaproveitado por `updateStatus`.

- [ ] **Passo 1: testes (falham)**

Seguir o padrão do arquivo (mocks de `TicketRepository`, `UserService`, `AuthenticatedUserService`,
`TicketEventService`, `ApplicationEventPublisher`; `ReflectionTestUtils.setField(entidade, "id", ...)`).

```java
@Test
void deveCancelarChamadoAbertoQuandoAdmin()            // status CANCELADO, evento CHAMADO_CANCELADO
@Test
void deveCancelarChamadoEmAndamentoQuandoAdmin()
@Test
void deveManterResolvedAtEClosedAtNulosAoCancelar()    // o que protege os indicadores de fechamento
@Test
void deveRegistrarEventoDeCancelamentoComAutor()       // ArgumentCaptor no ticketEventService
@Test
void devePublicarNotificacaoDeStatusAoCancelar()       // TicketNotificationType.STATUS_ALTERADO
@Test
void deveCancelarProprioChamadoAbertoQuandoSolicitante()
@Test
void naoDeveCancelarChamadoDeTerceiroQuandoSolicitante()      // ForbiddenException
@Test
void naoDeveCancelarProprioChamadoEmAndamentoQuandoSolicitante() // ConflictException
@Test
void naoDeveCancelarChamadoJaCancelado()                       // ConflictException
@Test
void naoDeveCancelarChamadoFechadoOuResolvido()                // ConflictException
```

Exemplo completo do caso central, para servir de molde aos demais:

```java
@Test
void deveCancelarProprioChamadoAbertoQuandoSolicitante() {
	UUID requesterId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
	User requester = new User("Maria", "maria@fadex.org.br", "hash", Role.SOLICITANTE);
	ReflectionTestUtils.setField(requester, "id", requesterId);
	Ticket ticket = new Ticket("Erro", "Descricao do erro.", requester);
	ReflectionTestUtils.setField(ticket, "id", TICKET_ID);

	when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
	when(authenticatedUserService.getUserId()).thenReturn(requesterId);
	when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
	when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));
	when(userService.findEntityById(requesterId)).thenReturn(requester);

	TicketDto response = ticketService.cancel(TICKET_ID);

	assertThat(response.status()).isEqualTo(TicketStatus.CANCELADO);
	assertThat(ticket.getResolvedAt()).isNull();
	assertThat(ticket.getClosedAt()).isNull();
	verify(ticketEventService).record(
			eq(ticket), eq(requester), eq(TicketEventType.CHAMADO_CANCELADO), anyString());
}
```

- [ ] **Passo 2: rodar e ver falhar**

`cd backend && ./gradlew test --tests '*TicketServiceTest'` → `cancel` não existe.

- [ ] **Passo 3: implementar**

Extrair de `updateStatus` o miolo comum e acrescentar `cancel`:

```java
/**
 * Cancelamento e exclusao logica: o chamado sai do fluxo e o rastro fica.
 *
 * ADMIN cancela qualquer chamado; SOLICITANTE cancela o proprio e apenas enquanto ABERTO — a partir
 * de EM_ANDAMENTO existe trabalho de outra pessoa em curso. Papel indevido e 403; estado que nao
 * aceita cancelamento e 409, e quem decide isso e a matriz, nao um if aqui.
 */
@Transactional
public TicketDto cancel(UUID id) {
	Ticket ticket = findEntityById(id);

	assertCanCancel(ticket);
	assertTransitionAllowed(ticket.getStatus(), TicketStatus.CANCELADO);

	return applyStatusChange(
			ticket,
			TicketStatus.CANCELADO,
			TicketEventType.CHAMADO_CANCELADO,
			"Chamado cancelado."
	);
}

private void assertCanCancel(Ticket ticket) {
	if (accessControlService.isAdmin()) {
		return;
	}

	accessControlService.assertCanAccessTicket(ticket);

	if (ticket.getStatus() != TicketStatus.ABERTO) {
		throw new ConflictException(
				"Chamado ja em atendimento so pode ser cancelado por um administrador."
		);
	}
}
```

`applyStatusChange` recebe o corpo que hoje está no fim de `updateStatus` (carimbos de
`RESOLVIDO`/`FECHADO`, `save`, `ticketEventService.record`, `publishTicketNotification`,
`TicketMapper.toResponseDto`), parametrizado pelo tipo de evento e pela descrição.
`updateStatus` continua chamando `assertAdmin()` e passa `TicketEventType.STATUS_ALTERADO` com a
descrição "Status alterado de X para Y.". Nenhum carimbo é escrito para `CANCELADO`.

- [ ] **Passo 4: verde**

`./gradlew test --tests '*TicketServiceTest'` → PASS. Depois `make backend-test` inteiro.

- [ ] **Passo 5: commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java
git commit -m "feat(backend): cancela chamado com regra de papel e evento de historico"
```

---

### Task 3: `DELETE /api/v1/tickets/{id}` e bloqueio de estado terminal

**Arquivos:**
- Modificar: `backend/src/main/java/br/org/fadex/helpdesk/controller/TicketController.java`
- Modificar: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java`
  (`assertTicketIsNotClosed`)
- Teste: `backend/src/test/java/br/org/fadex/helpdesk/controller/TicketControllerTest.java`,
  `backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java`

**Interfaces:**
- Consome: `TicketService.cancel(UUID)` (Task 2).
- Produz: `DELETE /api/v1/tickets/{id}` → `200` + `TicketDto`.

- [ ] **Passo 1: testes (falham)**

No `TicketControllerTest` (o arquivo já importa `delete` do MockMvc):

```java
@Test
void deveCancelarChamadoERetornarStatusCancelado() throws Exception {
	when(ticketService.cancel(TICKET_ID)).thenReturn(ticketDto(TicketStatus.CANCELADO));

	mockMvc.perform(delete("/api/v1/tickets/{id}", TICKET_ID).with(jwt()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELADO"));
}

@Test
void deveResponder409AoCancelarChamadoEmEstadoTerminal() throws Exception {
	when(ticketService.cancel(TICKET_ID)).thenThrow(new ConflictException("Transicao nao permitida."));

	mockMvc.perform(delete("/api/v1/tickets/{id}", TICKET_ID).with(jwt()))
			.andExpect(status().isConflict());
}

@Test
void deveResponder404AoCancelarChamadoInexistente() throws Exception { /* NotFoundException → 404 */ }
```

No `TicketServiceTest`, o bloqueio derivado:

```java
@Test
void naoDeveAtribuirResponsavelEmChamadoCancelado()  // ConflictException
@Test
void naoDeveRemoverResponsavelEmChamadoCancelado()   // ConflictException
```

- [ ] **Passo 2: rodar e ver falhar**

`./gradlew test --tests '*TicketControllerTest' --tests '*TicketServiceTest'`

- [ ] **Passo 3: implementar o endpoint**

```java
/**
 * Exclusao logica do chamado: cancela e devolve o retrato novo.
 *
 * DELETE porque e onde o cliente procura "remover o chamado", e {@code 200} com corpo porque o
 * chamado continua existindo em CANCELADO — um 204 mudo sugeriria que o registro sumiu, e ele nao
 * some: historico, comentarios e metricas ficam.
 */
@DeleteMapping("/{id}")
public ResponseEntity<TicketDto> cancel(@PathVariable UUID id) {
	TicketDto ticket = ticketService.cancel(id);

	return ResponseEntity.ok(ticket);
}
```

- [ ] **Passo 4: generalizar o bloqueio terminal**

```java
/**
 * Estado terminal nao aceita mais mexer em responsavel. Deriva da matriz em vez de listar FECHADO e
 * CANCELADO: um terceiro estado terminal futuro ja entra coberto.
 */
private void assertTicketIsNotClosed(Ticket ticket) {
	if (TicketStatusTransition.allowedFrom(ticket.getStatus()).isEmpty()) {
		throw new ConflictException(
				"Chamado " + ticket.getStatus().getLabel().toLowerCase() + " nao pode ser alterado."
		);
	}
}
```

Conferir se algum teste existente afirma a mensagem literal "Chamado fechado nao pode ser alterado.";
se afirmar, manter a mensagem específica para `FECHADO` em vez de afrouxar o teste.

- [ ] **Passo 5: verde**

`make backend-test` inteiro, 0 falhas.

- [ ] **Passo 6: commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/controller/TicketController.java backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java backend/src/test
git commit -m "feat(backend): expoe cancelamento de chamado em delete tickets id"
```

---

### Task 4: Indicadores — cancelado fora do SLA, e o resto prendido por teste

**Arquivos:**
- Modificar: `backend/src/main/java/br/org/fadex/helpdesk/ai/indicator/TicketIndicatorProjection.java`
- Modificar: `backend/src/main/java/br/org/fadex/helpdesk/ai/indicator/IndicatorService.java`
- Teste: `backend/src/test/java/br/org/fadex/helpdesk/ai/indicator/IndicatorServiceTest.java`

**Interfaces:**
- Produz: `TicketIndicatorProjection.isCanceled()`.

- [ ] **Passo 1: helper de teste + testes (falham)**

No `IndicatorServiceTest`, junto dos helpers `aberto`/`fechado`/`resolvido`:

```java
private TicketIndicatorProjection cancelado(
		TicketPriority priority,
		TicketCategory category,
		LocalDateTime createdAt
) {
	// Cancelado nao tem resolvedAt nem closedAt — e o que mantem os indicadores de fechamento
	// intactos. O helper `projection` deriva resolvedAt de closedAt, entao nulo aqui e proposital.
	return projection(TicketStatus.CANCELADO, priority, category, ClassificationOrigin.PENDENTE,
			null, null, null, null, null, createdAt, null, null, null, null);
}
```

Testes:

```java
@Test
void naoDeveContarChamadoCanceladoComoViolacaoDeSla() {
	givenProjections(
			fechado(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(10), NOW.minusHours(9)),
			cancelado(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(100))
	);

	SlaIndicatorsDto sla = service().getIndicators().durations().sla();

	assertThat(sla.overall().evaluated()).isEqualTo(1);
	assertThat(sla.overall().withinTarget()).isEqualTo(1);
	assertThat(sla.byPriority().get(TicketPriority.ALTA).evaluated()).isEqualTo(1);
}

@Test
void naoDeveContarChamadoCanceladoNoEnvelhecimentoDaFila() {
	givenProjections(cancelado(TicketPriority.MEDIA, TicketCategory.ACESSO, NOW.minusHours(100)));

	BacklogAgingDto aging = service().getIndicators().durations().backlogAging();

	assertThat(aging.upToOneDay() + aging.oneToThreeDays() + aging.overThreeDays()).isZero();
	assertThat(service().getIndicators().durations().oldestOpenTicketHours()).isNull();
}

@Test
void naoDeveContarChamadoCanceladoComoAltaPrioridadeEmAberto() {
	givenProjections(cancelado(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(100)));

	assertThat(service().getIndicators().overview().openHighPriority()).isZero();
}

@Test
void naoDeveContarChamadoCanceladoNaMediaDeFechamento() {
	givenProjections(cancelado(TicketPriority.MEDIA, TicketCategory.ACESSO, NOW.minusHours(100)));

	OverviewIndicatorsDto overview = service().getIndicators().overview();

	assertThat(service().getIndicators().durations().closure().overall().sampleSize()).isZero();
	assertThat(overview.closedToday()).isZero();
	assertThat(overview.closedThisWeek()).isZero();
}

@Test
void deveContarChamadoCanceladoNoTotalENaFatiaDeStatus() {
	givenProjections(
			aberto(TicketPriority.MEDIA, TicketCategory.ACESSO, NOW.minusHours(2)),
			cancelado(TicketPriority.MEDIA, TicketCategory.ACESSO, NOW.minusHours(100))
	);

	OverviewIndicatorsDto overview = service().getIndicators().overview();

	assertThat(overview.total()).isEqualTo(2);
	assertThat(overview.byStatus()).containsEntry(TicketStatus.CANCELADO, 1L);
}
```

Mais um teste de carga por responsável, usando o helper de responsável já existente adaptado para
`CANCELADO` (`projection(TicketStatus.CANCELADO, ..., assigneeId, assigneeName, null, null)`):
`naoDeveContarChamadoCanceladoNaCargaDoResponsavel` → `workload.openByAssignee()` vazio.

- [ ] **Passo 2: rodar e ver falhar**

`./gradlew test --tests '*IndicatorServiceTest'`
Esperado: **só** `naoDeveContarChamadoCanceladoComoViolacaoDeSla` falha (`evaluated` = 2). Os demais
já passam — é esse o ponto: eles prendem comportamento hoje correto por acidente. Se algum outro
falhar, o diagnóstico do design §8.1 está errado e precisa ser revisto antes de seguir.

- [ ] **Passo 3: `isCanceled()` na projeção**

```java
public boolean isCanceled() {
	return status == TicketStatus.CANCELADO;
}
```

Atualizar o javadoc de `settledAt()`, que passa a valer só para chamado não cancelado.

- [ ] **Passo 4: excluir do SLA**

No topo do laço de `buildSla`:

```java
// Chamado cancelado sai do numerador e do denominador: nao foi resolvido, mas tambem nao esta
// pendente de ninguem. Sem este corte ele viraria violacao permanente, piorando sozinho com o
// tempo — o mesmo erro que a regra do chamado recem-criado ja evita.
if (projection.isCanceled()) {
	continue;
}
```

- [ ] **Passo 5: verde**

`make backend-test` inteiro.

- [ ] **Passo 6: commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/ai/indicator backend/src/test/java/br/org/fadex/helpdesk/ai/indicator
git commit -m "fix(backend): tira chamado cancelado do sla e prende as demais exclusoes"
```

---

### Task 5: Notificação de cancelamento por e-mail

**Arquivos:**
- Modificar: `backend/src/main/java/br/org/fadex/helpdesk/notification/TicketEmailComposer.java`
- Teste: `backend/src/test/java/br/org/fadex/helpdesk/notification/` (arquivo de teste do composer
  já existente — localizar com `ls`)

- [ ] **Passo 1: testes (falham)**

```java
@Test
void deveAvisarSolicitanteEResponsavelQuandoChamadoECancelado()
// evento STATUS_ALTERADO com ticket em CANCELADO, ator = ADMIN diferente dos dois:
// 2 mensagens, assunto comecando por "Seu chamado foi cancelado"

@Test
void naoDeveAvisarQuemCancelouOProprioChamado()
// ator = solicitante: so o responsavel recebe
```

- [ ] **Passo 2: rodar e ver falhar**

- [ ] **Passo 3: implementar**

Em `composeStatusChanged`: acrescentar `case CANCELADO -> "Seu chamado foi cancelado";` ao `switch` de
título e montar a lista de destinatários em vez de devolver uma única mensagem:

```java
// O responsavel entra na lista so no cancelamento: quem esta atendendo precisa saber que o
// chamado morreu, sob pena de continuar trabalhando nele. Nas demais mudancas de status o
// contrato publicado continua sendo so o solicitante.
List<NotificationRecipient> recipients = new ArrayList<>();
recipients.add(event.requester());

if (ticket.status() == TicketStatus.CANCELADO && event.assignee() != null) {
	recipients.add(event.assignee());
}
```

e filtrar por `recipient.isNot(event.actorId())` dentro do laço, preservando a regra "nunca notificar
quem causou a ação". O corpo do texto e o template `status-alterado` são reaproveitados, variando
apenas `destinatarioNome`.

- [ ] **Passo 4: verde** — `make backend-test`

- [ ] **Passo 5: commit**

```bash
git commit -m "feat(backend): avisa solicitante e responsavel no cancelamento"
```

---

### Task 6: `api.md`

**Arquivos:**
- Modificar: `docs/backend/api.md`

- [ ] **Passo 1: editar**

1. Lista de valores de `status` (linha ~406): acrescentar `CANCELADO`.
2. Seção "Transições de status" (~492): tabela nova com `CANCELADO` como destino de `ABERTO` e
   `EM_ANDAMENTO`, `CANCELADO` terminal, e a frase de que cancelado não reabre.
3. Tabela de carimbos (~486): linha dizendo que `CANCELADO` **não** escreve `resolvedAt` nem
   `closedAt`, e por quê (indicadores).
4. Seção nova `### DELETE /api/v1/tickets/{id}` depois da de status: exclusão lógica, resposta `200`
   com `TicketDto`, tabela de erros (`403` papel/dono, `409` estado terminal ou solicitante com
   chamado em atendimento, `404` inexistente).
5. `GET /api/v1/ticket-status-transitions` (~640): exemplo de resposta atualizado com `CANCELADO`, e
   a observação de que a matriz é do domínio e independe de papel.
6. Seção de eventos de histórico: `CHAMADO_CANCELADO`.
7. Seção de e-mails (~800): linha "Chamado cancelado | solicitante e responsável".
8. Seção de indicadores (~963): nota de que chamado cancelado fica fora de SLA, aging, oldest-open e
   fechamento, e dentro de total/`byStatus`/top solicitantes.
9. Linha ~1170 ("Atualização de status, atribuição e remoção de responsável"): incluir cancelamento.

- [ ] **Passo 2: commit**

```bash
git add docs/backend/api.md
git commit -m "docs(backend): documenta cancelamento de chamado no contrato da api"
```

---

### Task 7: Frontend — matriz vinda da API

**Arquivos:**
- Modificar: `frontend/src/types/choice.ts` (`TicketStatusValue` ganha `"CANCELADO"`)
- Modificar: `frontend/src/services/tickets.service.ts` (`cancel`)
- Criar: `frontend/src/services/ticket-status-transitions.service.ts`
- Modificar: `frontend/src/features/tickets/ticket-status-transitions.ts` (passa a consumir a API)

- [ ] **Passo 1: tipo e serviço**

```ts
// ticket-status-transitions.service.ts
import type { TicketStatusValue } from "@/src/types/api";
import { api } from "./api";

export type TicketStatusTransitions = Record<TicketStatusValue, TicketStatusValue[]>;

async function get() {
  const response = await api.get<TicketStatusTransitions>("/ticket-status-transitions");
  return response.data;
}

export const ticketStatusTransitionsService = { get };
```

Em `tickets.service.ts`:

```ts
/**
 * Cancelamento e exclusao logica: o backend responde 200 com o chamado ja em CANCELADO.
 */
async function cancel(id: string) {
  const response = await api.delete<TicketDto>(`/tickets/${id}`);
  return response.data;
}
```

- [ ] **Passo 2: matriz vinda do servidor**

`ticket-status-transitions.ts` perde a constante local e passa a expor funções puras sobre a matriz
recebida, mais um hook que a busca uma vez por sessão:

```ts
export function allowedStatusesFrom(
  transitions: TicketStatusTransitions | null,
  from: TicketStatusValue
) {
  return transitions?.[from] ?? [];
}

export function canCancel(
  transitions: TicketStatusTransitions | null,
  from: TicketStatusValue
) {
  return allowedStatusesFrom(transitions, from).includes("CANCELADO");
}
```

`selectableStatusesFrom` continua existindo, agora recebendo a matriz, e **filtra `CANCELADO` para
fora** — cancelar tem botão próprio (design §10.2). Substituir o comentário de topo do arquivo: a
dívida que ele registra está paga.

- [ ] **Passo 3: `make frontend-lint` e `make frontend-build`**

- [ ] **Passo 4: commit**

```bash
git commit -m "refactor(frontend): consome a matriz de transicoes da api"
```

---

### Task 8: Frontend — ação de cancelar e rótulos

**Arquivos:**
- Modificar: `frontend/src/features/tickets/ticket-lifecycle-actions.tsx`
- Modificar: `frontend/src/features/tickets/use-ticket-actions.ts`
- Modificar: `frontend/src/features/tickets/ticket-detail-page.tsx` (fiação do hook e da matriz)
- Modificar: onde o badge de status é estilizado (localizar com
  `grep -rn "RESOLVIDO" frontend/src --include='*.tsx' --include='*.ts'`)

- [ ] **Passo 1: ação no hook**

`use-ticket-actions.ts` ganha `cancel`, no mesmo padrão de `unassign` (chamada, toast de erro,
recarga do detalhe). Sem otimismo: cancelamento é irreversível, a tela só muda depois do `200`.

- [ ] **Passo 2: botão**

Em `ticket-lifecycle-actions.tsx`, botão destrutivo "Cancelar chamado", com confirmação
(`window.confirm` só se o projeto não tiver `AlertDialog` — verificar `frontend/src/components/ui/`
antes e preferir o componente existente). Visível quando:

```ts
const podeCancelar =
  canCancel(transitions, ticket.status) &&
  (isAdmin || (isOwner && ticket.status === "ABERTO"));
```

`isAdmin`/`isOwner` saem do store de sessão já usado nas outras telas (`grep -rn "useSession\|authStore" frontend/src | head`).
As demais ações de ciclo de vida ficam desabilitadas quando o chamado está em estado terminal —
generalizar a constante `isClosed` do arquivo para `isTerminal`, derivada de
`allowedStatusesFrom(transitions, ticket.status).length === 0`, e ajustar o texto do `CardDescription`
para dizer "Chamado cancelado não reabre." quando for o caso.

- [ ] **Passo 3: rótulo e badge**

`CANCELADO` já chega do `/choices` para o filtro e para os rótulos. Acrescentar o estilo do badge em
tom neutro/apagado (`slate`), não vermelho: cancelado não é falha do sistema.

- [ ] **Passo 4: `make frontend-lint` e `make frontend-build`**

- [ ] **Passo 5: commit**

```bash
git commit -m "feat(frontend): cancela chamado no detalhe respeitando o papel"
```

---

### Task 9: Verificação de ponta a ponta contra o Postgres real

O H2 valida a V7, mas quem manda em produção é o Postgres, e é a stack de pé que prova a migração.

- [ ] **Passo 1: indicadores ANTES**

Já capturado em `scratchpad/indicadores-antes.json` no início da frente. Não recapturar depois do
rebuild sem antes ter o "antes" salvo.

- [ ] **Passo 2: escolher o chamado da prova**

Um chamado `ABERTO` **já em violação de SLA** (ALTA >4h, MEDIA >24h, BAIXA >72h desde `createdAt`) —
cancelar chamado recém-criado não move número nenhum e não prova nada. Listar candidatos com
`GET /api/v1/tickets?status=ABERTO&size=50` como ADMIN e escolher pelo par `priority`/`createdAt`.

- [ ] **Passo 3: rebuild**

```bash
docker compose up -d --build backend
```

Acompanhar o log até `Started HelpdeskApplication` e confirmar `Successfully applied 1 migration`
(V7) no log do Flyway. **Avisar o rebuild no relatório final.**

- [ ] **Passo 4: cancelar via API**

`DELETE /api/v1/tickets/{id}` como ADMIN → `200`, corpo com `"status": "CANCELADO"`.
Depois: `DELETE` de novo no mesmo chamado → `409`. `DELETE` como SOLICITANTE em chamado alheio →
`403`. `GET /api/v1/tickets/{id}/events` → linha `CHAMADO_CANCELADO`.

- [ ] **Passo 5: indicadores DEPOIS e comparação**

`GET /api/v1/indicators` como ADMIN. Confirmar contra o "antes":

| Métrica | Esperado |
|---|---|
| `sla.overall.evaluated` | −1 |
| `sla.byPriority[<prioridade do chamado>].evaluated` | −1 |
| balde de `backlogAging` correspondente à idade | −1 |
| `overview.byStatus.CANCELADO` | +1 (aparece) |
| `overview.byStatus.ABERTO` | −1 |
| `overview.total` | inalterado |
| `closure.overall.sampleSize` | inalterado |
| `workload.topRequesters` | inalterado |

Qualquer divergência é achado, não ruído: investigar antes de declarar pronto.

- [ ] **Passo 6: suíte e front, uma última vez**

`make backend-test` (0 falhas), `make frontend-lint`, `make frontend-build`.

- [ ] **Passo 7: registrar a evidência**

Anexar o antes/depois ao relatório final da frente. Não versionar os JSON.

---

## Autorrevisão do plano

- **Cobertura do design:** D1 §2 → Task 3 (exclusão lógica); D2 → Task 1; D3 → Task 1; D4 (sem
  `canceled_at`) → Task 2 passo 3 e Task 4 helper; D5 → Task 2; D6 → Task 3 e Task 7; D7 → Tasks 1 e
  5; D8 → Task 1 passo 6; D9 → Task 4; D10 → Task 3 passo 4; D11 → Tasks 7 e 8; §11 (testes) →
  distribuído; §8.5 (evidência) → Task 9. Sem lacunas.
- **Consistência de nomes:** `cancel` (service, controller, service do front),
  `TicketStatus.CANCELADO`, `TicketEventType.CHAMADO_CANCELADO`, `isCanceled()`,
  `allowedStatusesFrom(transitions, from)`, `canCancel(transitions, from)` — usados com a mesma
  assinatura em todas as tasks.
- **Ordem:** 1 → 2 → 3 são encadeadas; 4 e 5 dependem só da 1; 7 → 8 dependem da 3 estar de pé; 9 é
  final por construção.
