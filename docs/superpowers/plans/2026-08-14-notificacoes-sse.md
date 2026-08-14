# Motor de Notificações SSE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Construir o motor de notificações em tempo real por Server-Sent Events, com um único ponto de extensão para que subdomínios publiquem notificações sem conhecer o transporte.

**Architecture:** Um endpoint autenticado devolve um `SseEmitter` por conexão, guardado em um registry em memória junto da identidade capturada do JWT. Quem quiser notificar publica um `NotificationMessage` pelo `ApplicationEventPublisher`; um listener `AFTER_COMMIT` faz o fanout filtrando por audiência. Nenhum service de domínio é alterado nesta entrega.

**Tech Stack:** Java 21, Spring Boot 4.1.0 (Spring Framework 7.0.8), stack Servlet via `spring-boot-starter-webmvc`, JUnit 5, Mockito, AssertJ, Gradle.

**Spec:** `docs/superpowers/specs/2026-08-14-notificacoes-sse-design.md`

## Global Constraints

- Indentação com tab, seguindo o restante do backend.
- Todo o motor vive no módulo `br.org.fadex.helpdesk.sse`, com subpastas por camada: `sse/controller`, `sse/service`, `sse/model`, `sse/config`. Nada de SSE é adicionado aos pacotes globais `controller`, `service`, `model` ou `config`.
- Testes espelham a mesma árvore em `backend/src/test/java/br/org/fadex/helpdesk/sse/...`.
- Controllers retornam `ResponseEntity`, conforme `backend/AGENTS.md`.
- Testes em `backend/src/test/java/br/org/fadex/helpdesk`, nomeados `*Test.java`, com métodos em português sem acento, como `deveRemoverConexaoEmErro`.
- Comando de teste: `make backend-test` na raiz do repositório.
- Commits em português com escopo, no formato `feat(backend):`, `test(backend):`, `docs(backend):`.
- Nenhum arquivo de `service/TicketService.java`, `service/TicketCommentService.java` ou do pacote `security` é modificado neste plano.
- `SseEmitter` vive em `org.springframework.web.servlet.mvc.method.annotation`, verificado no Spring Framework 7.0.8.

## Estrutura de Arquivos

| Arquivo | Responsabilidade |
| --- | --- |
| `sse/model/NotificationAudience.java` | Decide se uma assinatura recebe a mensagem |
| `sse/model/NotificationMessage.java` | Mensagem publicada por quem notifica |
| `sse/model/SseSubscription.java` | Conexão aberta e identidade capturada |
| `sse/model/NotificationConnectionDto.java` | Payload do evento inicial |
| `sse/service/NotificationEmitterRegistry.java` | Guarda e remove conexões abertas |
| `sse/service/NotificationService.java` | Assinatura, envio e fanout |
| `sse/service/NotificationDispatcher.java` | Barreira transacional `AFTER_COMMIT` |
| `sse/service/NotificationHeartbeatScheduler.java` | Keep-alive periódico |
| `sse/controller/NotificationController.java` | `GET /api/v1/notifications/stream` |
| `sse/config/SchedulingConfig.java` | Habilita `@Scheduled` |

---

### Task 1: Modelo de Mensagem e Audiência

Value objects puros, sem dependência de Spring. É o vocabulário que todas as outras tasks usam.

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/sse/model/NotificationAudience.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/sse/model/NotificationMessage.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/sse/model/NotificationConnectionDto.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/sse/model/NotificationAudienceTest.java`

**Interfaces:**
- Consumes: `br.org.fadex.helpdesk.model.enums.Role`, que já existe.
- Produces: `NotificationAudience.includes(UUID userId, Role role) -> boolean`; implementações `NotificationAudience.Users(Set<UUID>)`, `NotificationAudience.Roles(Set<Role>)` e `NotificationAudience.Everyone()`; `NotificationMessage(String eventId, String eventName, Object data, NotificationAudience audience)` com fábrica `NotificationMessage.of(String eventName, Object data, NotificationAudience audience)`; `NotificationConnectionDto(String connectionId, LocalDateTime serverTime)`.

- [ ] **Step 1: Escrever o teste que falha**

Crie `NotificationAudienceTest.java`:

```java
package br.org.fadex.helpdesk.sse.model;

import br.org.fadex.helpdesk.model.enums.Role;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationAudienceTest {

	private static final UUID DESTINATARIO = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
	private static final UUID OUTRO_USUARIO = UUID.fromString("2f5b1c77-9e4a-4a1e-9c8e-2b1d3f4a5c6d");

	@Test
	void deveIncluirApenasUsuariosListados() {
		NotificationAudience audience = new NotificationAudience.Users(Set.of(DESTINATARIO));

		assertThat(audience.includes(DESTINATARIO, Role.SOLICITANTE)).isTrue();
		assertThat(audience.includes(OUTRO_USUARIO, Role.SOLICITANTE)).isFalse();
	}

	@Test
	void deveIncluirApenasRolesListadas() {
		NotificationAudience audience = new NotificationAudience.Roles(Set.of(Role.ADMIN));

		assertThat(audience.includes(OUTRO_USUARIO, Role.ADMIN)).isTrue();
		assertThat(audience.includes(DESTINATARIO, Role.SOLICITANTE)).isFalse();
	}

	@Test
	void deveIncluirTodosNoBroadcast() {
		NotificationAudience audience = new NotificationAudience.Everyone();

		assertThat(audience.includes(DESTINATARIO, Role.SOLICITANTE)).isTrue();
		assertThat(audience.includes(OUTRO_USUARIO, Role.ADMIN)).isTrue();
	}

	@Test
	void deveGerarIdentificadorUnicoParaCadaMensagem() {
		NotificationMessage primeira = NotificationMessage.of("CHAMADO_CRIADO", "dado", new NotificationAudience.Everyone());
		NotificationMessage segunda = NotificationMessage.of("CHAMADO_CRIADO", "dado", new NotificationAudience.Everyone());

		assertThat(primeira.eventId()).isNotBlank();
		assertThat(primeira.eventId()).isNotEqualTo(segunda.eventId());
	}
}
```

- [ ] **Step 2: Rodar o teste para confirmar que falha**

Run: `make backend-test`
Expected: falha de compilação, porque `NotificationAudience` e `NotificationMessage` não existem.

- [ ] **Step 3: Implementar `NotificationAudience`**

```java
package br.org.fadex.helpdesk.sse.model;

import br.org.fadex.helpdesk.model.enums.Role;

import java.util.Set;
import java.util.UUID;

public sealed interface NotificationAudience {

	boolean includes(UUID userId, Role role);

	record Users(Set<UUID> userIds) implements NotificationAudience {

		public Users {
			userIds = Set.copyOf(userIds);
		}

		@Override
		public boolean includes(UUID userId, Role role) {
			return userIds.contains(userId);
		}
	}

	record Roles(Set<Role> roles) implements NotificationAudience {

		public Roles {
			roles = Set.copyOf(roles);
		}

		@Override
		public boolean includes(UUID userId, Role role) {
			return roles.contains(role);
		}
	}

	record Everyone() implements NotificationAudience {

		@Override
		public boolean includes(UUID userId, Role role) {
			return true;
		}
	}
}
```

`sealed` sem cláusula `permits` é válido aqui porque as implementações são tipos aninhados no mesmo arquivo. `Set.copyOf` no construtor compacto congela a coleção, impedindo que quem publicou a mensagem altere a audiência depois de ela entrar no fanout.

- [ ] **Step 4: Implementar `NotificationMessage` e `NotificationConnectionDto`**

`NotificationMessage.java`:

```java
package br.org.fadex.helpdesk.sse.model;

import java.util.UUID;

public record NotificationMessage(
		String eventId,
		String eventName,
		Object data,
		NotificationAudience audience
) {

	public static NotificationMessage of(String eventName, Object data, NotificationAudience audience) {
		return new NotificationMessage(UUID.randomUUID().toString(), eventName, data, audience);
	}
}
```

`NotificationConnectionDto.java`:

```java
package br.org.fadex.helpdesk.sse.model;

import java.time.LocalDateTime;

public record NotificationConnectionDto(String connectionId, LocalDateTime serverTime) {
}
```

- [ ] **Step 5: Rodar o teste para confirmar que passa**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL, com os quatro testes novos passando.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/sse/model backend/src/test/java/br/org/fadex/helpdesk/sse/model
git commit -m "feat(backend): adiciona modelo de mensagem e audiencia de notificacao"
```

---

### Task 2: Registry de Conexões

O mapa `userId -> conexões abertas`. É a única estrutura mutável do motor e por isso a que mais exige cuidado com concorrência.

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/sse/model/SseSubscription.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/sse/service/NotificationEmitterRegistry.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/sse/service/NotificationEmitterRegistryTest.java`

**Interfaces:**
- Consumes: `Role` e o pacote `sse/model` da Task 1.
- Produces: `SseSubscription(String connectionId, UUID userId, Role role, SseEmitter emitter)` com fábrica `SseSubscription.create(UUID userId, Role role, SseEmitter emitter)`; `NotificationEmitterRegistry.add(SseSubscription)`, `.remove(SseSubscription)`, `.findAll() -> List<SseSubscription>` e `.countConnections() -> int`.

- [ ] **Step 1: Escrever o teste que falha**

Crie `NotificationEmitterRegistryTest.java`:

```java
package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.sse.model.SseSubscription;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEmitterRegistryTest {

	private static final UUID USUARIO = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
	private static final UUID OUTRO_USUARIO = UUID.fromString("2f5b1c77-9e4a-4a1e-9c8e-2b1d3f4a5c6d");

	private final NotificationEmitterRegistry registry = new NotificationEmitterRegistry();

	@Test
	void deveGuardarVariasConexoesDoMesmoUsuario() {
		SseSubscription primeiraAba = SseSubscription.create(USUARIO, Role.ADMIN, new SseEmitter());
		SseSubscription segundaAba = SseSubscription.create(USUARIO, Role.ADMIN, new SseEmitter());

		registry.add(primeiraAba);
		registry.add(segundaAba);

		assertThat(registry.countConnections()).isEqualTo(2);
		assertThat(registry.findAll()).containsExactlyInAnyOrder(primeiraAba, segundaAba);
	}

	@Test
	void deveRemoverApenasAConexaoInformada() {
		SseSubscription primeiraAba = SseSubscription.create(USUARIO, Role.ADMIN, new SseEmitter());
		SseSubscription segundaAba = SseSubscription.create(USUARIO, Role.ADMIN, new SseEmitter());

		registry.add(primeiraAba);
		registry.add(segundaAba);
		registry.remove(primeiraAba);

		assertThat(registry.findAll()).containsExactly(segundaAba);
	}

	@Test
	void deveIgnorarRemocaoRepetida() {
		SseSubscription conexao = SseSubscription.create(USUARIO, Role.SOLICITANTE, new SseEmitter());

		registry.add(conexao);
		registry.remove(conexao);
		registry.remove(conexao);

		assertThat(registry.countConnections()).isZero();
	}

	@Test
	void deveIsolarConexoesPorUsuario() {
		SseSubscription conexaoDoUsuario = SseSubscription.create(USUARIO, Role.ADMIN, new SseEmitter());
		SseSubscription conexaoDeOutroUsuario = SseSubscription.create(OUTRO_USUARIO, Role.SOLICITANTE, new SseEmitter());

		registry.add(conexaoDoUsuario);
		registry.add(conexaoDeOutroUsuario);
		registry.remove(conexaoDoUsuario);

		assertThat(registry.findAll()).containsExactly(conexaoDeOutroUsuario);
	}
}
```

- [ ] **Step 2: Rodar o teste para confirmar que falha**

Run: `make backend-test`
Expected: falha de compilação, porque `SseSubscription` e `NotificationEmitterRegistry` não existem.

- [ ] **Step 3: Implementar `SseSubscription`**

```java
package br.org.fadex.helpdesk.sse.model;

import br.org.fadex.helpdesk.model.enums.Role;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

public record SseSubscription(String connectionId, UUID userId, Role role, SseEmitter emitter) {

	public static SseSubscription create(UUID userId, Role role, SseEmitter emitter) {
		return new SseSubscription(UUID.randomUUID().toString(), userId, role, emitter);
	}
}
```

`userId` e `role` ficam guardados aqui de propósito. `AuthenticatedUserService` lê o `SecurityContextHolder`, que é thread-local: na thread que faz o envio, o contexto pertence a outro usuário ou está vazio. Resolver identidade na hora do envio produziria erro de autenticação ou, pior, entrega ao usuário errado.

- [ ] **Step 4: Implementar `NotificationEmitterRegistry`**

```java
package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.sse.model.SseSubscription;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationEmitterRegistry {

	private final Map<UUID, Set<SseSubscription>> subscriptionsByUser = new ConcurrentHashMap<>();

	public void add(SseSubscription subscription) {
		subscriptionsByUser.compute(subscription.userId(), (userId, subscriptions) -> {
			Set<SseSubscription> currentSubscriptions = subscriptions == null
					? ConcurrentHashMap.newKeySet()
					: subscriptions;
			currentSubscriptions.add(subscription);

			return currentSubscriptions;
		});
	}

	public void remove(SseSubscription subscription) {
		subscriptionsByUser.computeIfPresent(subscription.userId(), (userId, subscriptions) -> {
			subscriptions.remove(subscription);

			return subscriptions.isEmpty() ? null : subscriptions;
		});
	}

	public List<SseSubscription> findAll() {
		List<SseSubscription> subscriptions = subscriptionsByUser.values().stream()
				.flatMap(Set::stream)
				.toList();

		return subscriptions;
	}

	public int countConnections() {
		return findAll().size();
	}
}
```

A inserção acontece **dentro** do `compute`, não depois dele. Com `computeIfAbsent(...).add(...)`, uma remoção concorrente poderia descartar a chave entre as duas operações e a conexão recém-aberta ficaria órfã, nunca recebendo evento algum. `compute` e `computeIfPresent` sobre a mesma chave são atômicos entre si no `ConcurrentHashMap`.

- [ ] **Step 5: Rodar o teste para confirmar que passa**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/sse/model/SseSubscription.java backend/src/main/java/br/org/fadex/helpdesk/sse/service/NotificationEmitterRegistry.java backend/src/test/java/br/org/fadex/helpdesk/sse/service/NotificationEmitterRegistryTest.java
git commit -m "feat(backend): adiciona registry de conexoes sse"
```

---

### Task 3: Assinatura e Endpoint do Stream

Ao final desta task o stream já funciona de verdade: dá para abrir com `curl -N` e ver o evento inicial e a conexão pendurada. Ainda não há fanout.

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/sse/service/NotificationService.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/sse/controller/NotificationController.java`
- Modify: `backend/src/main/resources/application.properties`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/sse/service/NotificationServiceTest.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/sse/controller/NotificationControllerTest.java`

**Interfaces:**
- Consumes: `NotificationEmitterRegistry.add/remove/findAll` da Task 2; `SseSubscription.create`; `NotificationConnectionDto`; `AuthenticatedUserService.getUserId()` e `.getRole()`, que já existem.
- Produces: `NotificationService.subscribe() -> SseEmitter`; constante pública `NotificationService.CONNECTION_EVENT_NAME = "CONEXAO_ESTABELECIDA"`; endpoint `GET /api/v1/notifications/stream`.

- [ ] **Step 1: Escrever o teste de service que falha**

Crie `NotificationServiceTest.java`:

```java
package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.sse.model.SseSubscription;
import br.org.fadex.helpdesk.security.AuthenticatedUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	private static final UUID USUARIO = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
	private static final long TIMEOUT = 1800000L;
	private static final long RECONNECT_TIME = 5000L;

	@Mock
	private NotificationEmitterRegistry registry;

	@Mock
	private AuthenticatedUserService authenticatedUserService;

	@Test
	void deveRegistrarAssinaturaComIdentidadeCapturadaDoToken() {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		ArgumentCaptor<SseSubscription> subscriptionCaptor = ArgumentCaptor.forClass(SseSubscription.class);

		when(authenticatedUserService.getUserId()).thenReturn(USUARIO);
		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);

		SseEmitter emitter = notificationService.subscribe();

		verify(registry).add(subscriptionCaptor.capture());
		SseSubscription subscription = subscriptionCaptor.getValue();

		assertThat(subscription.userId()).isEqualTo(USUARIO);
		assertThat(subscription.role()).isEqualTo(Role.ADMIN);
		assertThat(subscription.connectionId()).isNotBlank();
		assertThat(subscription.emitter()).isSameAs(emitter);
		assertThat(emitter.getTimeout()).isEqualTo(TIMEOUT);
	}
}
```

- [ ] **Step 2: Rodar o teste para confirmar que falha**

Run: `make backend-test`
Expected: falha de compilação, porque `NotificationService` não existe.

- [ ] **Step 3: Adicionar as propriedades de configuração**

Em `backend/src/main/resources/application.properties`, após o bloco `security.cors.allowed-origins`, acrescente:

```properties
notifications.sse.timeout=${SSE_TIMEOUT_MS:1800000}
notifications.sse.heartbeat-interval=${SSE_HEARTBEAT_INTERVAL_MS:20000}
notifications.sse.reconnect-time=${SSE_RECONNECT_TIME_MS:5000}
```

Este passo vem **antes** de criar o service, e a ordem é obrigatória. `NotificationService` injeta as três propriedades por `@Value` sem valor padrão; enquanto elas não existirem, todo teste `@SpringBootTest` do projeto falha ao subir o contexto, não apenas os testes novos.

`spring.mvc.async.request-timeout` permanece sem valor de propósito. Verificado no fonte do Spring Framework 7.0.8: `ResponseBodyEmitterReturnValueHandler` constrói `new DeferredResult<>(emitter.getTimeout())`, ou seja, o timeout informado ao `SseEmitter` é exatamente o timeout da requisição assíncrona. Sem isso valeria o padrão do Tomcat, que derrubaria as conexões em trinta segundos.

- [ ] **Step 4: Implementar `NotificationService`**

```java
package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.sse.model.NotificationConnectionDto;
import br.org.fadex.helpdesk.sse.model.SseSubscription;
import br.org.fadex.helpdesk.security.AuthenticatedUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class NotificationService {

	public static final String CONNECTION_EVENT_NAME = "CONEXAO_ESTABELECIDA";

	private final NotificationEmitterRegistry registry;
	private final AuthenticatedUserService authenticatedUserService;
	private final long timeout;
	private final long reconnectTime;

	public NotificationService(
			NotificationEmitterRegistry registry,
			AuthenticatedUserService authenticatedUserService,
			@Value("${notifications.sse.timeout}") long timeout,
			@Value("${notifications.sse.reconnect-time}") long reconnectTime
	) {
		this.registry = registry;
		this.authenticatedUserService = authenticatedUserService;
		this.timeout = timeout;
		this.reconnectTime = reconnectTime;
	}

	public SseEmitter subscribe() {
		UUID userId = authenticatedUserService.getUserId();
		Role role = authenticatedUserService.getRole();
		SseEmitter emitter = new SseEmitter(timeout);
		SseSubscription subscription = SseSubscription.create(userId, role, emitter);

		registry.add(subscription);
		emitter.onCompletion(() -> registry.remove(subscription));
		emitter.onTimeout(() -> registry.remove(subscription));
		emitter.onError(throwable -> registry.remove(subscription));

		sendConnectionEvent(subscription);

		return emitter;
	}

	private void sendConnectionEvent(SseSubscription subscription) {
		NotificationConnectionDto connection = new NotificationConnectionDto(
				subscription.connectionId(),
				LocalDateTime.now()
		);

		send(subscription, subscription.connectionId(), CONNECTION_EVENT_NAME, connection);
	}

	private void send(SseSubscription subscription, String eventId, String eventName, Object data) {
		try {
			subscription.emitter().send(SseEmitter.event()
					.id(eventId)
					.name(eventName)
					.reconnectTime(reconnectTime)
					.data(data, MediaType.APPLICATION_JSON));
		} catch (IOException | IllegalStateException exception) {
			registry.remove(subscription);
		}
	}
}
```

Os três callbacks são obrigatórios. Se faltar um, cada reconexão do cliente deixa um emitter órfão no registry, acumulando memória e desperdiçando envios.

O `catch` cobre `IllegalStateException` além de `IOException`. Isso foi verificado no fonte do Spring Framework 7.0.8: `ResponseBodyEmitter.send` executa `Assert.state(!this.complete, ...)` antes de escrever, então enviar para uma conexão já encerrada lança `IllegalStateException`, não `IOException`. Sem esse `catch`, uma conexão que fecha em corrida quebraria o fanout.

O envio dentro de `subscribe()` acontece antes de o Spring inicializar o emitter. Isso é suportado: `ResponseBodyEmitter` guarda os envios antecipados e os despacha assim que a resposta é inicializada.

- [ ] **Step 5: Rodar o teste de service para confirmar que passa**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Escrever o teste de controller que falha**

Crie `NotificationControllerTest.java`:

```java
package br.org.fadex.helpdesk.sse.controller;

import br.org.fadex.helpdesk.sse.service.NotificationEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private NotificationEmitterRegistry registry;

	@Test
	void deveAbrirStreamParaUsuarioAutenticado() throws Exception {
		int conexoesAntes = registry.countConnections();

		mockMvc.perform(get("/api/v1/notifications/stream")
						.accept(MediaType.TEXT_EVENT_STREAM)
						.with(jwt().jwt(builder -> builder
								.claim("userId", "71e9c3d9-53b2-4c4e-9803-c504754dbb45")
								.claim("role", "ADMIN"))))
				.andExpect(status().isOk())
				.andExpect(request().asyncStarted());

		assertThat(registry.countConnections()).isGreaterThan(conexoesAntes);
	}

	@Test
	void deveRecusarStreamSemAutenticacao() throws Exception {
		mockMvc.perform(get("/api/v1/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(status().isUnauthorized());
	}
}
```

`request().asyncStarted()` é a asserção que prova o comportamento assíncrono: o método do controller retornou, mas a resposta continua aberta. É exatamente a mecânica que sustenta o SSE.

A contagem é comparada por diferença, não por valor absoluto, por dois motivos concretos. O `@SpringBootTest` reaproveita o contexto entre classes de teste, e o registry é um singleton: a requisição assíncrona do MockMvc nunca completa, então a conexão criada aqui permanece registrada pelo resto da execução da suíte. Além disso, a partir da Task 5 o `@EnableScheduling` fica ativo também nos testes, e o keep-alive pode rodar entre a requisição e a asserção, removendo a conexão do MockMvc e zerando a contagem. A asserção por delta é imune aos dois casos.

- [ ] **Step 7: Rodar o teste para confirmar que falha**

Run: `make backend-test`
Expected: falha de compilação, porque `NotificationController` não existe.

- [ ] **Step 8: Implementar `NotificationController`**

```java
package br.org.fadex.helpdesk.sse.controller;

import br.org.fadex.helpdesk.sse.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<SseEmitter> stream() {
		SseEmitter emitter = notificationService.subscribe();

		return ResponseEntity.ok(emitter);
	}
}
```

`ResponseEntity<SseEmitter>` mantém a convenção de `backend/AGENTS.md` sem quebrar o streaming. Verificado no fonte de `ResponseBodyEmitterReturnValueHandler`: `supportsReturnType` desembrulha `ResponseEntity` para descobrir o tipo do corpo, e o Javadoc da classe lista explicitamente os emitters "wrapped with ResponseEntity".

Nenhuma mudança em `SecurityConfig` é necessária: `anyRequest().authenticated()` já protege o endpoint, e o cliente envia o mesmo `Bearer` usado no resto da API.

- [ ] **Step 9: Rodar os testes para confirmar que passam**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Verificação manual com `curl`**

Suba o banco e a aplicação:

```bash
make db-up
make backend-run
```

Em outro terminal, obtenha um token e abra o stream:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@fadex.org.br","password":"admin123"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')

curl -N -H "Authorization: Bearer $TOKEN" -H 'Accept: text/event-stream' \
  http://localhost:8080/api/v1/notifications/stream
```

Expected: o `curl` imprime o evento inicial e **não retorna ao prompt** — a conexão fica aberta:

```
event: CONEXAO_ESTABELECIDA
id: 4f1c8b2a-...
retry: 5000
data: {"connectionId":"4f1c8b2a-...","serverTime":"2026-08-14T15:54:58"}
```

O `-N` desliga o buffering do `curl`; sem ele a saída só apareceria em blocos. Encerre com `Ctrl+C` e confirme no log da aplicação que nada quebrou.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/sse/service/NotificationService.java backend/src/main/java/br/org/fadex/helpdesk/sse/controller/NotificationController.java backend/src/main/resources/application.properties backend/src/test/java/br/org/fadex/helpdesk/sse/service/NotificationServiceTest.java backend/src/test/java/br/org/fadex/helpdesk/sse/controller/NotificationControllerTest.java
git commit -m "feat(backend): abre stream sse autenticado de notificacoes"
```

---

### Task 4: Fanout e Barreira Transacional

O ponto de extensão do motor. Depois desta task, qualquer service publica uma notificação com uma linha, e a entrega só acontece depois do commit.

**Files:**
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/sse/service/NotificationService.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/sse/service/NotificationDispatcher.java`
- Modify: `backend/src/test/java/br/org/fadex/helpdesk/sse/service/NotificationServiceTest.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/sse/service/NotificationDispatcherTest.java`

**Interfaces:**
- Consumes: `NotificationService.subscribe()` e o método privado `send` da Task 3; `NotificationMessage` e `NotificationAudience` da Task 1.
- Produces: `NotificationService.dispatch(NotificationMessage message)`; `NotificationDispatcher.onNotificationMessage(NotificationMessage message)`.

- [ ] **Step 1: Escrever os testes de fanout que falham**

Acrescente a `NotificationServiceTest.java` os imports e testes abaixo.

Imports adicionais:

```java
import br.org.fadex.helpdesk.sse.model.NotificationAudience;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
```

Testes:

```java
	@Test
	void deveEnviarApenasParaAssinaturasDaAudiencia() throws Exception {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		SseEmitter emitterDoDestinatario = mock(SseEmitter.class);
		SseEmitter emitterDeOutroUsuario = mock(SseEmitter.class);
		SseSubscription destinatario = new SseSubscription("conexao-1", USUARIO, Role.ADMIN, emitterDoDestinatario);
		SseSubscription outroUsuario = new SseSubscription(
				"conexao-2",
				UUID.fromString("2f5b1c77-9e4a-4a1e-9c8e-2b1d3f4a5c6d"),
				Role.SOLICITANTE,
				emitterDeOutroUsuario
		);
		NotificationMessage message = NotificationMessage.of(
				"CHAMADO_CRIADO",
				"conteudo",
				new NotificationAudience.Users(Set.of(USUARIO))
		);

		when(registry.findAll()).thenReturn(List.of(destinatario, outroUsuario));

		notificationService.dispatch(message);

		verify(emitterDoDestinatario).send(any(SseEmitter.SseEventBuilder.class));
		verify(emitterDeOutroUsuario, never()).send(any(SseEmitter.SseEventBuilder.class));
	}

	@Test
	void deveRemoverConexaoQuebradaSemInterromperAsDemais() throws Exception {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		SseEmitter emitterQuebrado = mock(SseEmitter.class);
		SseEmitter emitterSaudavel = mock(SseEmitter.class);
		SseSubscription conexaoQuebrada = new SseSubscription("conexao-1", USUARIO, Role.ADMIN, emitterQuebrado);
		SseSubscription conexaoSaudavel = new SseSubscription("conexao-2", USUARIO, Role.ADMIN, emitterSaudavel);
		NotificationMessage message = NotificationMessage.of(
				"CHAMADO_CRIADO",
				"conteudo",
				new NotificationAudience.Everyone()
		);

		when(registry.findAll()).thenReturn(List.of(conexaoQuebrada, conexaoSaudavel));
		doThrow(new IOException("conexao fechada")).when(emitterQuebrado).send(any(SseEmitter.SseEventBuilder.class));

		notificationService.dispatch(message);

		verify(registry).remove(conexaoQuebrada);
		verify(emitterSaudavel).send(any(SseEmitter.SseEventBuilder.class));
	}

	@Test
	void deveRemoverConexaoJaEncerradaQueLancaIllegalState() throws Exception {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		SseEmitter emitterEncerrado = mock(SseEmitter.class);
		SseSubscription conexaoEncerrada = new SseSubscription("conexao-1", USUARIO, Role.ADMIN, emitterEncerrado);
		NotificationMessage message = NotificationMessage.of(
				"CHAMADO_CRIADO",
				"conteudo",
				new NotificationAudience.Everyone()
		);

		when(registry.findAll()).thenReturn(List.of(conexaoEncerrada));
		doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
				.when(emitterEncerrado).send(any(SseEmitter.SseEventBuilder.class));

		notificationService.dispatch(message);

		verify(registry).remove(conexaoEncerrada);
	}
```

- [ ] **Step 2: Rodar os testes para confirmar que falham**

Run: `make backend-test`
Expected: falha de compilação, porque `NotificationService.dispatch` não existe.

- [ ] **Step 3: Implementar `dispatch` em `NotificationService`**

Acrescente o import `br.org.fadex.helpdesk.sse.model.NotificationMessage;`, `java.util.List;` e o método público abaixo, logo após `subscribe()`:

```java
	public void dispatch(NotificationMessage message) {
		List<SseSubscription> subscriptions = registry.findAll();

		for (SseSubscription subscription : subscriptions) {
			boolean shouldReceive = message.audience().includes(subscription.userId(), subscription.role());

			if (shouldReceive) {
				send(subscription, message.eventId(), message.eventName(), message.data());
			}
		}
	}
```

- [ ] **Step 4: Rodar os testes para confirmar que passam**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Escrever o teste do dispatcher que falha**

Crie `NotificationDispatcherTest.java`:

```java
package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.sse.model.NotificationAudience;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

	@Mock
	private NotificationService notificationService;

	@InjectMocks
	private NotificationDispatcher notificationDispatcher;

	@Test
	void deveDelegarMensagemParaOFanout() {
		NotificationMessage message = NotificationMessage.of(
				"CHAMADO_CRIADO",
				"conteudo",
				new NotificationAudience.Everyone()
		);

		notificationDispatcher.onNotificationMessage(message);

		verify(notificationService).dispatch(message);
	}

	@Test
	void deveEntregarSomenteDepoisDoCommit() throws Exception {
		Method listener = NotificationDispatcher.class.getMethod("onNotificationMessage", NotificationMessage.class);
		TransactionalEventListener annotation = AnnotationUtils.findAnnotation(listener, TransactionalEventListener.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
		assertThat(annotation.fallbackExecution()).isTrue();
	}
}
```

O segundo teste inspeciona a anotação em vez de simular uma transação. A fase transacional é uma decisão de contrato que precisa estar travada contra regressão, e verificá-la assim custa milissegundos em vez de subir um contexto transacional inteiro.

- [ ] **Step 6: Rodar o teste para confirmar que falha**

Run: `make backend-test`
Expected: falha de compilação, porque `NotificationDispatcher` não existe.

- [ ] **Step 7: Implementar `NotificationDispatcher`**

```java
package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationDispatcher {

	private final NotificationService notificationService;

	public NotificationDispatcher(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onNotificationMessage(NotificationMessage message) {
		notificationService.dispatch(message);
	}
}
```

`AFTER_COMMIT` é o coração desta task. `TicketService.create` e `TicketCommentService.create` são `@Transactional`; se o envio acontecesse dentro da transação, o cliente poderia receber notificação de um registro que sofreu rollback, ou recarregar a lista antes de o commit ficar visível.

`fallbackExecution = true` faz o listener rodar também quando a publicação acontece fora de uma transação. Sem isso, uma notificação publicada de um contexto não transacional seria silenciosamente descartada — o modo de falha mais difícil de diagnosticar deste desenho.

- [ ] **Step 8: Rodar os testes para confirmar que passam**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/sse/service/NotificationService.java backend/src/main/java/br/org/fadex/helpdesk/sse/service/NotificationDispatcher.java backend/src/test/java/br/org/fadex/helpdesk/sse/service/NotificationServiceTest.java backend/src/test/java/br/org/fadex/helpdesk/sse/service/NotificationDispatcherTest.java
git commit -m "feat(backend): entrega notificacoes por audiencia apos commit"
```

---

### Task 5: Heartbeat

Conexões ociosas são derrubadas por proxies e por timeouts intermediários. Um comentário periódico mantém o socket vivo e revela conexões mortas.

**Files:**
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/sse/service/NotificationService.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/sse/service/NotificationHeartbeatScheduler.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/sse/config/SchedulingConfig.java`
- Modify: `backend/src/test/java/br/org/fadex/helpdesk/sse/service/NotificationServiceTest.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/sse/service/NotificationHeartbeatSchedulerTest.java`

**Interfaces:**
- Consumes: `NotificationEmitterRegistry.findAll()`; `NotificationService` da Task 4.
- Produces: `NotificationService.sendHeartbeat()`; `NotificationHeartbeatScheduler.sendHeartbeat()`.

- [ ] **Step 1: Escrever os testes que falham**

Acrescente a `NotificationServiceTest.java`:

```java
	@Test
	void deveEnviarKeepAliveParaTodasAsConexoes() throws Exception {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		SseEmitter primeiroEmitter = mock(SseEmitter.class);
		SseEmitter segundoEmitter = mock(SseEmitter.class);
		SseSubscription primeiraConexao = new SseSubscription("conexao-1", USUARIO, Role.ADMIN, primeiroEmitter);
		SseSubscription segundaConexao = new SseSubscription("conexao-2", USUARIO, Role.ADMIN, segundoEmitter);

		when(registry.findAll()).thenReturn(List.of(primeiraConexao, segundaConexao));

		notificationService.sendHeartbeat();

		verify(primeiroEmitter).send(any(SseEmitter.SseEventBuilder.class));
		verify(segundoEmitter).send(any(SseEmitter.SseEventBuilder.class));
	}

	@Test
	void deveRemoverConexaoMortaDetectadaPeloKeepAlive() throws Exception {
		NotificationService notificationService = new NotificationService(
				registry,
				authenticatedUserService,
				TIMEOUT,
				RECONNECT_TIME
		);
		SseEmitter emitterMorto = mock(SseEmitter.class);
		SseSubscription conexaoMorta = new SseSubscription("conexao-1", USUARIO, Role.ADMIN, emitterMorto);

		when(registry.findAll()).thenReturn(List.of(conexaoMorta));
		doThrow(new IOException("broken pipe")).when(emitterMorto).send(any(SseEmitter.SseEventBuilder.class));

		notificationService.sendHeartbeat();

		verify(registry).remove(conexaoMorta);
	}
```

Crie `NotificationHeartbeatSchedulerTest.java`:

```java
package br.org.fadex.helpdesk.sse.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationHeartbeatSchedulerTest {

	@Mock
	private NotificationService notificationService;

	@InjectMocks
	private NotificationHeartbeatScheduler notificationHeartbeatScheduler;

	@Test
	void deveAcionarKeepAliveDoServico() {
		notificationHeartbeatScheduler.sendHeartbeat();

		verify(notificationService).sendHeartbeat();
	}

	@Test
	void deveUsarIntervaloConfiguravel() throws Exception {
		Method scheduled = NotificationHeartbeatScheduler.class.getMethod("sendHeartbeat");
		Scheduled annotation = AnnotationUtils.findAnnotation(scheduled, Scheduled.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.fixedRateString()).isEqualTo("${notifications.sse.heartbeat-interval}");
	}
}
```

- [ ] **Step 2: Rodar os testes para confirmar que falham**

Run: `make backend-test`
Expected: falha de compilação, porque `sendHeartbeat` e `NotificationHeartbeatScheduler` não existem.

- [ ] **Step 3: Implementar `sendHeartbeat` em `NotificationService`**

Acrescente os métodos abaixo, depois de `dispatch`:

```java
	public void sendHeartbeat() {
		List<SseSubscription> subscriptions = registry.findAll();

		for (SseSubscription subscription : subscriptions) {
			sendComment(subscription);
		}
	}

	private void sendComment(SseSubscription subscription) {
		try {
			subscription.emitter().send(SseEmitter.event().comment(HEARTBEAT_COMMENT));
		} catch (IOException | IllegalStateException exception) {
			registry.remove(subscription);
		}
	}
```

E a constante, junto de `CONNECTION_EVENT_NAME`:

```java
	private static final String HEARTBEAT_COMMENT = "ping";
```

O keep-alive vai como comentário, não como evento. No protocolo SSE, uma linha iniciada por dois-pontos é ignorada pelo parser do cliente: mantém o socket ativo sem poluir o fluxo de eventos da aplicação.

A escrita do heartbeat pode concorrer com a de um fanout no mesmo emitter. Não é preciso sincronizar: verificado no fonte do Spring Framework 7.0.8, `ResponseBodyEmitter.send` adquire um `writeLock` interno antes de escrever.

- [ ] **Step 4: Implementar o scheduler e habilitar o agendamento**

`NotificationHeartbeatScheduler.java`:

```java
package br.org.fadex.helpdesk.sse.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationHeartbeatScheduler {

	private final NotificationService notificationService;

	public NotificationHeartbeatScheduler(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@Scheduled(fixedRateString = "${notifications.sse.heartbeat-interval}")
	public void sendHeartbeat() {
		notificationService.sendHeartbeat();
	}
}
```

`SchedulingConfig.java`:

```java
package br.org.fadex.helpdesk.sse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

Sem `@EnableScheduling` a anotação `@Scheduled` é ignorada em silêncio e o heartbeat simplesmente nunca roda.

- [ ] **Step 5: Rodar os testes para confirmar que passam**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Verificação manual do keep-alive**

Com a aplicação rodando, abra o stream novamente e aguarde cerca de um minuto:

```bash
curl -N -H "Authorization: Bearer $TOKEN" -H 'Accept: text/event-stream' \
  http://localhost:8080/api/v1/notifications/stream
```

Expected: além do evento inicial, aparece uma linha `: ping` a cada vinte segundos.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/sse/service/NotificationService.java backend/src/main/java/br/org/fadex/helpdesk/sse/service/NotificationHeartbeatScheduler.java backend/src/main/java/br/org/fadex/helpdesk/sse/config/SchedulingConfig.java backend/src/test/java/br/org/fadex/helpdesk/sse/service/NotificationServiceTest.java backend/src/test/java/br/org/fadex/helpdesk/sse/service/NotificationHeartbeatSchedulerTest.java
git commit -m "feat(backend): mantem conexoes sse vivas com keep-alive"
```

---

### Task 6: Contrato da API e Verificação Final

`AGENTS.md` exige que `docs/backend/api.md` acompanhe qualquer mudança de contrato, para o frontend não precisar inferir comportamento pelo código.

**Files:**
- Modify: `docs/backend/api.md`

**Interfaces:**
- Consumes: o endpoint e o formato de evento das Tasks 3 a 5.
- Produces: documentação do contrato; nenhum código novo.

- [ ] **Step 1: Documentar o endpoint**

Acrescente a `docs/backend/api.md`, seguindo o formato já usado nas outras seções do arquivo:

````markdown
### `GET /api/v1/notifications/stream`

Abre um fluxo Server-Sent Events com as notificações do usuário autenticado. A resposta é `text/event-stream` e permanece aberta.

Autenticação usa o mesmo `Authorization: Bearer <token>` do restante da API. Como o `EventSource` nativo do navegador não envia headers, o cliente deve consumir o stream via `fetch` com leitura incremental do corpo.

Evento inicial, enviado assim que a conexão é aceita:

```
event: CONEXAO_ESTABELECIDA
id: 4f1c8b2a-1d2e-4f3a-8b9c-0d1e2f3a4b5c
retry: 5000
data: {"connectionId":"4f1c8b2a-1d2e-4f3a-8b9c-0d1e2f3a4b5c","serverTime":"2026-08-14T15:54:58"}
```

A cada vinte segundos o servidor envia um comentário de keep-alive, ignorado pelo parser SSE:

```
: ping
```

Sem token válido, a resposta é `401` no formato padrão de erro da API.

Não há reenvio de eventos perdidos: o cabeçalho `Last-Event-ID` não é tratado. Ao reconectar, o cliente deve recarregar os dados pelo endpoint REST correspondente.
````

- [ ] **Step 2: Verificar a suíte completa**

Run: `make backend-test`
Expected: BUILD SUCCESSFUL, sem testes ignorados.

- [ ] **Step 3: Conferir que nenhum service de domínio foi tocado**

Run: `git diff --name-only origin/dev...HEAD`
Expected: a lista não contém `TicketService.java`, `TicketCommentService.java`, `SecurityConfig.java` nem qualquer arquivo do pacote `security`.

- [ ] **Step 4: Commit**

```bash
git add docs/backend/api.md
git commit -m "docs(backend): documenta contrato do stream de notificacoes"
```

---

## Integração Futura

Fora do escopo deste plano, registrado para a worktree seguinte. Depois do merge de `feature(backend)/auth-rbac-historico`, notificar passa a ser uma linha no ponto em que o `TicketEvent` é gravado:

```java
applicationEventPublisher.publishEvent(NotificationMessage.of(
		ticketEvent.getType().name(),
		TicketEventMapper.toMinDto(ticketEvent),
		new NotificationAudience.Users(destinatarios)
));
```

O nome do evento reaproveita `TicketEventType`, o payload reaproveita `TicketEventMinDto`, e a audiência sai das regras de RBAC daquela branch. Nenhuma linha do motor precisa mudar.
