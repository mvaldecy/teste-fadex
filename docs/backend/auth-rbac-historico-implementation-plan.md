# Auth RBAC Historico Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar RBAC por role, senha provisoria com troca obrigatoria, refresh token persistido, historico de eventos de chamados e validacoes melhores na API backend.

**Architecture:** A API continua organizada nas camadas existentes de controller, service, repository, model, security e exception. As regras de acesso por role ficam nos services antes das specifications; auth ganha refresh token persistido e claim de troca obrigatoria; historico de chamado fica em uma entidade separada de comentarios.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Security resource server JWT, Spring Data JPA, Flyway, Jakarta Validation, JUnit Platform, Mockito, H2 em testes, PostgreSQL em desenvolvimento.

## Global Constraints

- Documentacao por dominio: documentos deste pacote ficam em `docs/backend`.
- Nao alterar frontend nesta entrega.
- Nao versionar `.env`, `backend/.env` ou `frontend/.env.local`.
- Backend deve seguir pacotes sob `br.org.fadex.helpdesk`.
- Controllers retornam `ResponseEntity`.
- Listagens usam paginacao e ordenacao padrao por `createdAt desc`, tamanho 10.
- DTOs seguem padrao `NomeCreationDto`, `NomeDto`, `NomeMinDto`.
- Specifications ficam em classes proprias com `createSpecification`.
- Services devem manter variaveis intermediarias, sem concentrar tudo direto no `return`.
- Erros passam pelo `GlobalExceptionHandler` e pela estrutura padrao da API.
- Como o banco usa Flyway com `ddl-auto=validate`, toda mudanca de schema deve entrar em migration.
- Use TDD: escrever teste que falha, verificar RED, implementar minimo, verificar GREEN.
- Nao criar commits de implementacao durante a execucao automatica. Nesta execucao, manter as alteracoes de implementacao unstaged durante as tasks e fazer stage final apenas para revisao do usuario. Commits separados serao feitos depois por solicitacao explicita.

---

## File Structure

- `backend/src/main/resources/db/migration/V2__auth_rbac_history.sql`: adiciona `must_change_password`, `refresh_tokens` e `ticket_events`.
- `backend/src/main/java/br/org/fadex/helpdesk/model/user/User.java`: inclui `mustChangePassword`, construtores e metodo de troca de senha.
- `backend/src/main/java/br/org/fadex/helpdesk/model/user/UserCreationDto.java`: remove `password` e adiciona mensagens de validacao.
- `backend/src/main/java/br/org/fadex/helpdesk/model/user/UserDto.java`: expõe `mustChangePassword`.
- `backend/src/main/java/br/org/fadex/helpdesk/model/user/UserMapper.java`: mapeia novo campo e cria usuario com senha provisoria.
- `backend/src/main/java/br/org/fadex/helpdesk/model/auth/*.java`: novos DTOs para refresh e troca de senha, ajuste da resposta de login.
- `backend/src/main/java/br/org/fadex/helpdesk/model/token/RefreshToken.java`: entidade de refresh token.
- `backend/src/main/java/br/org/fadex/helpdesk/repository/RefreshTokenRepository.java`: consultas por id e tokens ativos do usuario.
- `backend/src/main/java/br/org/fadex/helpdesk/service/RefreshTokenService.java`: gera, persiste, valida e revoga refresh tokens.
- `backend/src/main/java/br/org/fadex/helpdesk/security/JwtTokenService.java`: emite token normal e token limitado com claim `mustChangePassword`.
- `backend/src/main/java/br/org/fadex/helpdesk/security/PasswordChangeRequiredFilter.java`: bloqueia endpoints protegidos quando a claim exige troca de senha.
- `backend/src/main/java/br/org/fadex/helpdesk/service/AuthService.java`: login, refresh e troca de senha.
- `backend/src/main/java/br/org/fadex/helpdesk/controller/AuthController.java`: endpoints `/login`, `/refresh`, `/change-password`.
- `backend/src/main/java/br/org/fadex/helpdesk/service/UserService.java`: criacao com senha provisoria, envio de e-mail e filtro por role.
- `backend/src/main/java/br/org/fadex/helpdesk/security/AccessControlService.java`: helpers de role e propriedade de recurso.
- `backend/src/main/java/br/org/fadex/helpdesk/exception/ForbiddenException.java`: excecao 403.
- `backend/src/main/java/br/org/fadex/helpdesk/model/event/*.java`: entidade, DTOs, filtro, fields, mapper e enum de eventos.
- `backend/src/main/java/br/org/fadex/helpdesk/repository/TicketEventRepository.java`: repository de eventos.
- `backend/src/main/java/br/org/fadex/helpdesk/repository/TicketEventSpecification.java`: filtro por ticket e tipo.
- `backend/src/main/java/br/org/fadex/helpdesk/service/TicketEventService.java`: grava e lista eventos.
- `backend/src/main/java/br/org/fadex/helpdesk/controller/TicketEventController.java`: endpoint de historico.
- `backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java`: filtro por role e evento `CHAMADO_CRIADO`.
- `backend/src/main/java/br/org/fadex/helpdesk/service/TicketCommentService.java`: permissao por role e evento `COMENTARIO_ADICIONADO`.
- `backend/src/main/java/br/org/fadex/helpdesk/config/DevDataSeeder.java`: cria usuarios dev com `mustChangePassword = false`.
- `docs/backend/api.md`: atualiza contrato.

---

### Task 1: Schema e Modelos Base

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__auth_rbac_history.sql`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/exception/ForbiddenException.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/token/RefreshToken.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/repository/RefreshTokenRepository.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/event/TicketEvent.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/event/TicketEventDto.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/event/TicketEventMinDto.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/event/TicketEventFields.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/event/TicketEventFilter.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/event/TicketEventMapper.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/enums/TicketEventType.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/repository/TicketEventRepository.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/repository/TicketEventSpecification.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/user/User.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/user/UserDto.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/user/UserMapper.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/user/UserFields.java`
- Modify: `backend/src/test/java/br/org/fadex/helpdesk/repository/TicketPersistenceTest.java`

**Interfaces:**
- Produces: `User(String name, String email, String passwordHash, Role role, Boolean mustChangePassword)`
- Produces: `Boolean User.getMustChangePassword()`
- Produces: `void User.changePassword(String passwordHash)`
- Produces: `RefreshToken(User user, String tokenHash, LocalDateTime expiresAt)`
- Produces: `UUID RefreshToken.getId()`
- Produces: `void RefreshToken.revoke()`
- Produces: `TicketEvent(Ticket ticket, User actor, TicketEventType type, String description, String metadata)`
- Produces: `TicketEventType.CHAMADO_CRIADO` and `TicketEventType.COMENTARIO_ADICIONADO`

- [ ] **Step 1: Write the failing persistence test**

Add this test to `TicketPersistenceTest` or create `AuthHistoryPersistenceTest` in the same repository test package:

```java
@Test
void devePersistirUsuarioComTrocaObrigatoriaRefreshTokenEEventoDeChamado() {
	User requester = userRepository.save(new User(
			"Maria Solicitante",
			"maria.persistencia@fadex.org.br",
			"hash",
			Role.SOLICITANTE,
			true
	));
	Ticket ticket = ticketRepository.save(new Ticket(
			"Erro ao acessar sistema",
			"Nao consigo acessar o sistema interno.",
			TicketCategory.OUTROS,
			TicketPriority.MEDIA,
			ClassificationOrigin.PENDENTE,
			requester
	));
	RefreshToken refreshToken = refreshTokenRepository.save(new RefreshToken(
			requester,
			"hash-token",
			LocalDateTime.now().plusDays(7)
	));
	TicketEvent event = ticketEventRepository.save(new TicketEvent(
			ticket,
			requester,
			TicketEventType.CHAMADO_CRIADO,
			"Chamado criado.",
			null
	));

	assertThat(requester.getMustChangePassword()).isTrue();
	assertThat(refreshToken.getUser()).isEqualTo(requester);
	assertThat(refreshToken.getTokenHash()).isEqualTo("hash-token");
	assertThat(refreshToken.getRevokedAt()).isNull();
	assertThat(event.getTicket()).isEqualTo(ticket);
	assertThat(event.getActor()).isEqualTo(requester);
	assertThat(event.getType()).isEqualTo(TicketEventType.CHAMADO_CRIADO);
	assertThat(event.getCreatedAt()).isNotNull();
}
```

Add `@Autowired` fields in the test:

```java
@Autowired
private RefreshTokenRepository refreshTokenRepository;

@Autowired
private TicketEventRepository ticketEventRepository;
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
make backend-test
```

Expected: compile failure because `RefreshToken`, `TicketEvent`, repositories and the new `User` constructor do not exist.

- [ ] **Step 3: Write the minimal schema and domain implementation**

Create `V2__auth_rbac_history.sql`:

```sql
alter table users
    add column must_change_password boolean not null default false;

create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(255) not null,
    expires_at timestamp not null,
    revoked_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_refresh_tokens_user foreign key (user_id) references users (id),
    constraint uk_refresh_tokens_token_hash unique (token_hash)
);

create table ticket_events (
    id uuid primary key,
    ticket_id uuid not null,
    actor_id uuid,
    type varchar(50) not null,
    description varchar(255) not null,
    metadata text,
    created_at timestamp not null,
    constraint fk_ticket_events_ticket foreign key (ticket_id) references tickets (id),
    constraint fk_ticket_events_actor foreign key (actor_id) references users (id),
    constraint ck_ticket_events_type check (type in (
        'CHAMADO_CRIADO',
        'COMENTARIO_ADICIONADO',
        'STATUS_ALTERADO',
        'RESPONSAVEL_ATRIBUIDO',
        'PRIORIDADE_ALTERADA',
        'CATEGORIA_ALTERADA',
        'CLASSIFICACAO_ATUALIZADA'
    ))
);

create index idx_refresh_tokens_user_id on refresh_tokens (user_id);
create index idx_refresh_tokens_expires_at on refresh_tokens (expires_at);
create index idx_ticket_events_ticket_id_created_at on ticket_events (ticket_id, created_at);
create index idx_ticket_events_actor_id on ticket_events (actor_id);
create index idx_ticket_events_type on ticket_events (type);
```

Implement `ForbiddenException`:

```java
package br.org.fadex.helpdesk.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApplicationException {

	private static final String CODE = "FORBIDDEN";

	public ForbiddenException(String message) {
		super(CODE, message, HttpStatus.FORBIDDEN);
	}
}
```

Update `User` with `mustChangePassword`, the new constructor, old constructor delegating to `false`, getter and password mutation:

```java
@Column(name = "must_change_password", nullable = false)
private Boolean mustChangePassword;

public User(String name, String email, String passwordHash, Role role) {
	this(name, email, passwordHash, role, false);
}

public User(String name, String email, String passwordHash, Role role, Boolean mustChangePassword) {
	this.name = name;
	this.email = email;
	this.passwordHash = passwordHash;
	this.role = role;
	this.mustChangePassword = mustChangePassword;
}

public Boolean getMustChangePassword() {
	return mustChangePassword;
}

public void changePassword(String passwordHash) {
	this.passwordHash = passwordHash;
	this.mustChangePassword = false;
}
```

Create entities using the same auditing style as existing models:

```java
@Entity
@Table(name = "refresh_tokens")
@EntityListeners(AuditingEntityListener.class)
public class RefreshToken {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, unique = true, length = 255)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

	@Column(name = "created_at", nullable = false)
	@CreatedDate
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	@LastModifiedDate
	private LocalDateTime updatedAt;

	protected RefreshToken() {
	}

	public RefreshToken(User user, String tokenHash, LocalDateTime expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public void revoke() {
		this.revokedAt = LocalDateTime.now();
	}
}
```

```java
@Entity
@Table(name = "ticket_events")
@EntityListeners(AuditingEntityListener.class)
public class TicketEvent {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ticket_id", nullable = false)
	private Ticket ticket;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id")
	private User actor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private TicketEventType type;

	@Column(nullable = false, length = 255)
	private String description;

	@Column(columnDefinition = "text")
	private String metadata;

	@Column(name = "created_at", nullable = false)
	@CreatedDate
	private LocalDateTime createdAt;

	protected TicketEvent() {
	}

	public TicketEvent(Ticket ticket, User actor, TicketEventType type, String description, String metadata) {
		this.ticket = ticket;
		this.actor = actor;
		this.type = type;
		this.description = description;
		this.metadata = metadata;
	}
}
```

Create repositories:

```java
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
	List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);
}
```

```java
public interface TicketEventRepository extends JpaRepository<TicketEvent, UUID>, JpaSpecificationExecutor<TicketEvent> {
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
make backend-test
```

Expected: PASS.

- [ ] **Step 5: Stage changes only**

Run:

```bash
git add backend/src/main/resources/db/migration/V2__auth_rbac_history.sql \
  backend/src/main/java/br/org/fadex/helpdesk/exception/ForbiddenException.java \
  backend/src/main/java/br/org/fadex/helpdesk/model/token \
  backend/src/main/java/br/org/fadex/helpdesk/repository/RefreshTokenRepository.java \
  backend/src/main/java/br/org/fadex/helpdesk/model/event \
  backend/src/main/java/br/org/fadex/helpdesk/model/enums/TicketEventType.java \
  backend/src/main/java/br/org/fadex/helpdesk/repository/TicketEventRepository.java \
  backend/src/main/java/br/org/fadex/helpdesk/repository/TicketEventSpecification.java \
  backend/src/main/java/br/org/fadex/helpdesk/model/user/User.java \
  backend/src/main/java/br/org/fadex/helpdesk/model/user/UserDto.java \
  backend/src/main/java/br/org/fadex/helpdesk/model/user/UserMapper.java \
  backend/src/main/java/br/org/fadex/helpdesk/model/user/UserFields.java \
  backend/src/test/java/br/org/fadex/helpdesk/repository
```

Expected: files staged; do not commit.

---

### Task 2: Criacao de Usuario com Senha Provisoria e Validacoes

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/security/TemporaryPasswordGenerator.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/user/UserCreationDto.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/user/UserDto.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/user/UserMapper.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/service/UserService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/config/DevDataSeeder.java`
- Modify: DTO validation messages in `backend/src/main/java/br/org/fadex/helpdesk/model/auth/AuthRequestDto.java`, `backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketCreationDto.java`, `backend/src/main/java/br/org/fadex/helpdesk/model/comment/TicketCommentCreationDto.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/UserServiceTest.java`

**Interfaces:**
- Consumes: `User(..., Boolean mustChangePassword)` from Task 1.
- Produces: `String TemporaryPasswordGenerator.generate()`
- Produces: `UserService.create(UserCreationDto userCreationDto)` generating and emailing provisional password.
- Produces: `UserDto(..., Boolean mustChangePassword, ...)`

- [ ] **Step 1: Write failing service tests**

Replace the existing create test in `UserServiceTest` with:

```java
@Mock
private TemporaryPasswordGenerator temporaryPasswordGenerator;

@Mock
private EmailSender emailSender;

@Test
void deveCriarUsuarioComSenhaProvisoriaEEnviarEmail() {
	UserCreationDto userCreationDto = new UserCreationDto(
			"Maria Solicitante",
			"maria@fadex.org.br",
			Role.SOLICITANTE
	);
	User savedUser = new User(
			"Maria Solicitante",
			"maria@fadex.org.br",
			"hash-gerado",
			Role.SOLICITANTE,
			true
	);
	ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
	ArgumentCaptor<EmailMessage> emailCaptor = ArgumentCaptor.forClass(EmailMessage.class);

	when(userRepository.existsByEmail("maria@fadex.org.br")).thenReturn(false);
	when(temporaryPasswordGenerator.generate()).thenReturn("SenhaProvisoria123");
	when(passwordEncoder.encode("SenhaProvisoria123")).thenReturn("hash-gerado");
	when(userRepository.save(any(User.class))).thenReturn(savedUser);

	UserDto response = userService.create(userCreationDto);

	verify(userRepository).save(userCaptor.capture());
	verify(emailSender).send(emailCaptor.capture());
	User userToSave = userCaptor.getValue();
	EmailMessage email = emailCaptor.getValue();

	assertThat(userToSave.getPasswordHash()).isEqualTo("hash-gerado");
	assertThat(userToSave.getMustChangePassword()).isTrue();
	assertThat(email.to()).isEqualTo("maria@fadex.org.br");
	assertThat(email.subject()).isEqualTo("Acesso provisório ao Fadex Helpdesk");
	assertThat(email.text()).contains("SenhaProvisoria123");
	assertThat(response.mustChangePassword()).isTrue();
}
```

Update duplicate email test to use the new constructor:

```java
UserCreationDto userCreationDto = new UserCreationDto(
		"Maria Solicitante",
		"maria@fadex.org.br",
		Role.SOLICITANTE
);

verify(temporaryPasswordGenerator, never()).generate();
verify(emailSender, never()).send(any(EmailMessage.class));
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.UserServiceTest
```

Expected: compile failure because `TemporaryPasswordGenerator`, `EmailSender` dependency and new DTO shape are missing.

- [ ] **Step 3: Implement minimal user creation flow**

Update `UserCreationDto`:

```java
public record UserCreationDto(
		@NotBlank(message = "Nome e obrigatorio.")
		@Size(max = 120, message = "Nome deve ter no maximo 120 caracteres.")
		String name,

		@NotBlank(message = "E-mail e obrigatorio.")
		@Email(message = "E-mail deve ter formato valido.")
		@Size(max = 180, message = "E-mail deve ter no maximo 180 caracteres.")
		String email,

		@NotNull(message = "Perfil e obrigatorio.")
		Role role
) {
}
```

Create `TemporaryPasswordGenerator`:

```java
@Service
public class TemporaryPasswordGenerator {

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";
	private static final int LENGTH = 16;
	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		StringBuilder password = new StringBuilder(LENGTH);
		for (int index = 0; index < LENGTH; index++) {
			int position = secureRandom.nextInt(ALPHABET.length());
			password.append(ALPHABET.charAt(position));
		}
		return password.toString();
	}
}
```

Update `UserService` constructor and create flow:

```java
private final TemporaryPasswordGenerator temporaryPasswordGenerator;
private final EmailSender emailSender;

@Transactional
public UserDto create(UserCreationDto userCreationDto) {
	validateEmailAvailable(userCreationDto.email());

	String temporaryPassword = temporaryPasswordGenerator.generate();
	String passwordHash = passwordEncoder.encode(temporaryPassword);
	User user = UserMapper.toEntity(userCreationDto, passwordHash, true);
	User savedUser = userRepository.save(user);

	EmailMessage message = new EmailMessage(
			savedUser.getEmail(),
			"Acesso provisório ao Fadex Helpdesk",
			"Ola, " + savedUser.getName() + ". Sua senha provisoria e: " + temporaryPassword
	);
	emailSender.send(message);

	UserDto response = UserMapper.toResponseDto(savedUser);
	return response;
}
```

Update `UserMapper`:

```java
public static User toEntity(UserCreationDto userCreationDto, String passwordHash, Boolean mustChangePassword) {
	return new User(
			userCreationDto.name(),
			userCreationDto.email(),
			passwordHash,
			userCreationDto.role(),
			mustChangePassword
	);
}
```

Update `DevDataSeeder` to pass `false` explicitly when creating development users.

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.UserServiceTest
```

Expected: PASS.

- [ ] **Step 5: Run all backend tests**

Run:

```bash
make backend-test
```

Expected: PASS.

- [ ] **Step 6: Stage changes only**

Run:

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/security/TemporaryPasswordGenerator.java \
  backend/src/main/java/br/org/fadex/helpdesk/model/user/UserCreationDto.java \
  backend/src/main/java/br/org/fadex/helpdesk/model/user/UserDto.java \
  backend/src/main/java/br/org/fadex/helpdesk/model/user/UserMapper.java \
  backend/src/main/java/br/org/fadex/helpdesk/service/UserService.java \
  backend/src/main/java/br/org/fadex/helpdesk/config/DevDataSeeder.java \
  backend/src/main/java/br/org/fadex/helpdesk/model/auth/AuthRequestDto.java \
  backend/src/main/java/br/org/fadex/helpdesk/model/ticket/TicketCreationDto.java \
  backend/src/main/java/br/org/fadex/helpdesk/model/comment/TicketCommentCreationDto.java \
  backend/src/test/java/br/org/fadex/helpdesk/service/UserServiceTest.java
```

Expected: files staged; do not commit.

---

### Task 3: Login, Refresh Token e Troca Obrigatoria de Senha

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/auth/RefreshTokenRequestDto.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/model/auth/ChangePasswordRequestDto.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/service/RefreshTokenService.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/security/PasswordChangeRequiredFilter.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/model/auth/AuthResponseDto.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/controller/AuthController.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/service/AuthService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/security/JwtTokenService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/security/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/test/java/br/org/fadex/helpdesk/service/AuthServiceTest.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/security/PasswordChangeRequiredFilterTest.java`

**Interfaces:**
- Consumes: `RefreshToken` and `RefreshTokenRepository` from Task 1.
- Produces: `AuthResponseDto(String accessToken, String refreshToken, String tokenType, Long expiresIn, Boolean mustChangePassword, Role role, UserMinDto user)`
- Produces: `String JwtTokenService.generateToken(User user)`
- Produces: `String JwtTokenService.generatePasswordChangeToken(User user)`
- Produces: `String RefreshTokenService.create(User user)`
- Produces: `User RefreshTokenService.validate(String rawRefreshToken)`
- Produces: `void RefreshTokenService.revokeActiveTokens(UUID userId)`
- Produces: `AuthResponseDto AuthService.refresh(RefreshTokenRequestDto dto)`
- Produces: `AuthResponseDto AuthService.changePassword(ChangePasswordRequestDto dto)`

- [ ] **Step 1: Write failing auth service tests**

Create `AuthServiceTest`:

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtTokenService jwtTokenService;

	@Mock
	private RefreshTokenService refreshTokenService;

	@InjectMocks
	private AuthService authService;

	@Test
	void deveRetornarTokenLimitadoSemRefreshQuandoSenhaProvisoria() {
		User user = new User("Maria", "maria@fadex.org.br", "hash", Role.SOLICITANTE, true);
		AuthRequestDto request = new AuthRequestDto("maria@fadex.org.br", "provisoria");

		when(userRepository.findByEmail("maria@fadex.org.br")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("provisoria", "hash")).thenReturn(true);
		when(jwtTokenService.generatePasswordChangeToken(user)).thenReturn("access-limitado");
		when(jwtTokenService.getTokenType()).thenReturn("Bearer");
		when(jwtTokenService.getExpirationSeconds()).thenReturn(3600L);

		AuthResponseDto response = authService.login(request);

		assertThat(response.accessToken()).isEqualTo("access-limitado");
		assertThat(response.refreshToken()).isNull();
		assertThat(response.mustChangePassword()).isTrue();
		verify(refreshTokenService, never()).create(any(User.class));
	}

	@Test
	void deveRetornarTokenNormalERefreshQuandoSenhaJaFoiTrocada() {
		User user = new User("Admin", "admin@fadex.org.br", "hash", Role.ADMIN, false);
		AuthRequestDto request = new AuthRequestDto("admin@fadex.org.br", "admin123");

		when(userRepository.findByEmail("admin@fadex.org.br")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("admin123", "hash")).thenReturn(true);
		when(jwtTokenService.generateToken(user)).thenReturn("access");
		when(refreshTokenService.create(user)).thenReturn("refresh");
		when(jwtTokenService.getTokenType()).thenReturn("Bearer");
		when(jwtTokenService.getExpirationSeconds()).thenReturn(3600L);

		AuthResponseDto response = authService.login(request);

		assertThat(response.accessToken()).isEqualTo("access");
		assertThat(response.refreshToken()).isEqualTo("refresh");
		assertThat(response.mustChangePassword()).isFalse();
	}

	@Test
	void deveTrocarSenhaRevogarRefreshAntigosERetornarTokensNormais() {
		UUID userId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		User user = new User("Maria", "maria@fadex.org.br", "hash-antigo", Role.SOLICITANTE, true);
		ChangePasswordRequestDto request = new ChangePasswordRequestDto("provisoria", "NovaSenha123", "NovaSenha123");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("provisoria", "hash-antigo")).thenReturn(true);
		when(passwordEncoder.encode("NovaSenha123")).thenReturn("hash-novo");
		when(jwtTokenService.generateToken(user)).thenReturn("access");
		when(refreshTokenService.create(user)).thenReturn("refresh");
		when(jwtTokenService.getTokenType()).thenReturn("Bearer");
		when(jwtTokenService.getExpirationSeconds()).thenReturn(3600L);

		AuthResponseDto response = authService.changePassword(userId, request);

		assertThat(user.getPasswordHash()).isEqualTo("hash-novo");
		assertThat(user.getMustChangePassword()).isFalse();
		verify(refreshTokenService).revokeActiveTokens(userId);
		assertThat(response.refreshToken()).isEqualTo("refresh");
		assertThat(response.mustChangePassword()).isFalse();
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.AuthServiceTest
```

Expected: compile failure because DTOs, methods and service dependencies do not exist.

- [ ] **Step 3: Implement auth DTOs and service behavior**

Update `AuthResponseDto`:

```java
public record AuthResponseDto(
		String accessToken,
		String refreshToken,
		String tokenType,
		Long expiresIn,
		Boolean mustChangePassword,
		Role role,
		UserMinDto user
) {
}
```

Create request DTOs:

```java
public record RefreshTokenRequestDto(
		@NotBlank(message = "Refresh token e obrigatorio.")
		String refreshToken
) {
}
```

```java
public record ChangePasswordRequestDto(
		@NotBlank(message = "Senha atual e obrigatoria.")
		String currentPassword,

		@NotBlank(message = "Nova senha e obrigatoria.")
		@Size(min = 8, max = 72, message = "Nova senha deve ter entre 8 e 72 caracteres.")
		String newPassword,

		@NotBlank(message = "Confirmacao de senha e obrigatoria.")
		String confirmPassword
) {
}
```

Implement `AuthService` public methods:

```java
public AuthResponseDto login(AuthRequestDto authRequestDto) {
	User user = userRepository.findByEmail(authRequestDto.email())
			.orElseThrow(() -> new UnauthorizedException("Credenciais invalidas."));

	validatePassword(authRequestDto.password(), user.getPasswordHash());

	if (Boolean.TRUE.equals(user.getMustChangePassword())) {
		return createPasswordChangeResponse(user);
	}

	return createRegularResponse(user);
}

public AuthResponseDto refresh(RefreshTokenRequestDto requestDto) {
	User user = refreshTokenService.validate(requestDto.refreshToken());

	if (Boolean.TRUE.equals(user.getMustChangePassword())) {
		throw new UnauthorizedException("Troca de senha obrigatoria.");
	}

	return createRegularResponse(user);
}

public AuthResponseDto changePassword(UUID userId, ChangePasswordRequestDto requestDto) {
	User user = userRepository.findById(userId)
			.orElseThrow(() -> new UnauthorizedException("Usuario autenticado invalido."));

	validatePassword(requestDto.currentPassword(), user.getPasswordHash());
	validatePasswordConfirmation(requestDto);

	String passwordHash = passwordEncoder.encode(requestDto.newPassword());
	user.changePassword(passwordHash);
	refreshTokenService.revokeActiveTokens(userId);

	return createRegularResponse(user);
}
```

Create helper responses:

```java
private AuthResponseDto createPasswordChangeResponse(User user) {
	String accessToken = jwtTokenService.generatePasswordChangeToken(user);
	return new AuthResponseDto(
			accessToken,
			null,
			jwtTokenService.getTokenType(),
			jwtTokenService.getExpirationSeconds(),
			true,
			user.getRole(),
			UserMapper.toMinDto(user)
	);
}

private AuthResponseDto createRegularResponse(User user) {
	String accessToken = jwtTokenService.generateToken(user);
	String refreshToken = refreshTokenService.create(user);
	return new AuthResponseDto(
			accessToken,
			refreshToken,
			jwtTokenService.getTokenType(),
			jwtTokenService.getExpirationSeconds(),
			false,
			user.getRole(),
			UserMapper.toMinDto(user)
	);
}
```

- [ ] **Step 4: Implement refresh token service**

Use an opaque refresh token in the format `<refreshTokenId>.<secret>`. Store only the BCrypt hash of `secret`; use the UUID prefix to load the row by id. The UUID prefix is not a credential; the persisted credential hash is the hash of the secret portion delivered to the client.

```java
@Service
public class RefreshTokenService {

	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final SecureRandom secureRandom = new SecureRandom();
	private final Long expirationSeconds;

	public RefreshTokenService(
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			@Value("${security.refresh-token.expiration-seconds}") Long expirationSeconds
	) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.expirationSeconds = expirationSeconds;
	}

	public String create(User user) {
		String secret = generateSecret();
		String tokenHash = passwordEncoder.encode(secret);
		LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expirationSeconds);
		RefreshToken refreshToken = refreshTokenRepository.save(new RefreshToken(user, tokenHash, expiresAt));
		return refreshToken.getId() + "." + secret;
	}

	public User validate(String rawRefreshToken) {
		ParsedRefreshToken parsedToken = parse(rawRefreshToken);
		RefreshToken refreshToken = refreshTokenRepository.findById(parsedToken.id())
				.orElseThrow(() -> new UnauthorizedException("Refresh token invalido."));

		validateRefreshToken(parsedToken.secret(), refreshToken);
		return refreshToken.getUser();
	}

	@Transactional
	public void revokeActiveTokens(UUID userId) {
		List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
		activeTokens.forEach(RefreshToken::revoke);
	}
}
```

Add helpers to the same service:

```java
private void validateRefreshToken(String secret, RefreshToken refreshToken) {
	if (refreshToken.getRevokedAt() != null || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
		throw new UnauthorizedException("Refresh token invalido.");
	}

	if (!passwordEncoder.matches(secret, refreshToken.getTokenHash())) {
		throw new UnauthorizedException("Refresh token invalido.");
	}
}

private ParsedRefreshToken parse(String rawRefreshToken) {
	String[] parts = rawRefreshToken.split("\\.", 2);
	if (parts.length != 2) {
		throw new UnauthorizedException("Refresh token invalido.");
	}
	try {
		return new ParsedRefreshToken(UUID.fromString(parts[0]), parts[1]);
	} catch (IllegalArgumentException exception) {
		throw new UnauthorizedException("Refresh token invalido.");
	}
}

private record ParsedRefreshToken(UUID id, String secret) {
}
```

- [ ] **Step 5: Write failing filter test**

Create `PasswordChangeRequiredFilterTest` with MockMvc or unit-level filter test:

```java
@Test
void deveBloquearEndpointProtegidoQuandoTokenExigeTrocaDeSenha() throws Exception {
	MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tickets");
	MockHttpServletResponse response = new MockHttpServletResponse();
	FilterChain chain = mock(FilterChain.class);
	Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.subject("maria@fadex.org.br")
			.claim("userId", UUID.randomUUID().toString())
			.claim("role", "SOLICITANTE")
			.claim("mustChangePassword", true)
			.build();
	Authentication authentication = new JwtAuthenticationToken(jwt);
	SecurityContextHolder.getContext().setAuthentication(authentication);

	filter.doFilter(request, response, chain);

	assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
	verify(chain, never()).doFilter(request, response);
}
```

- [ ] **Step 6: Implement limited-token JWT and filter**

Update `JwtTokenService`:

```java
public String generateToken(User user) {
	return generateToken(user, false);
}

public String generatePasswordChangeToken(User user) {
	return generateToken(user, true);
}

private String generateToken(User user, Boolean mustChangePassword) {
	// existing claims plus:
	.claim("mustChangePassword", mustChangePassword)
}
```

Implement `PasswordChangeRequiredFilter`:

```java
@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

	private static final String CHANGE_PASSWORD_PATH = "/api/v1/auth/change-password";
	private final RestAccessDeniedHandler restAccessDeniedHandler;

	public PasswordChangeRequiredFilter(RestAccessDeniedHandler restAccessDeniedHandler) {
		this.restAccessDeniedHandler = restAccessDeniedHandler;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (requiresPasswordChange(authentication) && !CHANGE_PASSWORD_PATH.equals(request.getRequestURI())) {
			restAccessDeniedHandler.handle(
					request,
					response,
					new AccessDeniedException("Troca de senha obrigatoria.")
			);
			return;
		}

		filterChain.doFilter(request, response);
	}
}
```

Register it after Bearer token authentication in `SecurityConfig`.

Also update `SecurityConfig` to permit refresh without an access token:

```java
.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
.requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
```

Do not make `/api/v1/auth/change-password` public; it must require the limited access token from login.

- [ ] **Step 7: Run targeted and full tests**

Run:

```bash
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.AuthServiceTest
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.security.PasswordChangeRequiredFilterTest
make backend-test
```

Expected: PASS.

- [ ] **Step 8: Stage changes only**

Run:

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/model/auth \
  backend/src/main/java/br/org/fadex/helpdesk/controller/AuthController.java \
  backend/src/main/java/br/org/fadex/helpdesk/service/AuthService.java \
  backend/src/main/java/br/org/fadex/helpdesk/service/RefreshTokenService.java \
  backend/src/main/java/br/org/fadex/helpdesk/security/JwtTokenService.java \
  backend/src/main/java/br/org/fadex/helpdesk/security/PasswordChangeRequiredFilter.java \
  backend/src/main/java/br/org/fadex/helpdesk/security/SecurityConfig.java \
  backend/src/main/resources/application.properties \
  backend/src/test/java/br/org/fadex/helpdesk/service/AuthServiceTest.java \
  backend/src/test/java/br/org/fadex/helpdesk/security/PasswordChangeRequiredFilterTest.java
```

Expected: files staged; do not commit.

---

### Task 4: RBAC por Role em Usuarios, Chamados e Comentarios

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/security/AccessControlService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/security/AuthenticatedUserService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/service/UserService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketCommentService.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/UserServiceTest.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketCommentServiceTest.java`

**Interfaces:**
- Consumes: `ForbiddenException`.
- Produces: `AccessControlService.isAdmin()`
- Produces: `AccessControlService.getAuthenticatedUserId()`
- Produces: `AccessControlService.assertCanAccessUser(UUID userId)`
- Produces: `AccessControlService.assertCanAccessTicket(Ticket ticket)`

- [ ] **Step 1: Write failing RBAC service tests**

Add to `TicketServiceTest`:

```java
@Test
void deveForcarSolicitanteAutenticadoNoFiltroDeChamados() {
	UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
	PageRequest pageable = PageRequest.of(0, 10);

	when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
	when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);
	when(ticketRepository.findAll(anyTicketSpecification(), eq(pageable))).thenReturn(Page.empty(pageable));

	ticketService.findAll(new TicketFilter(null, null, null, null, null, "erro"), pageable);

	verify(ticketRepository).findAll(anyTicketSpecification(), eq(pageable));
}
```

Add a direct behavior test for forbidden detail:

```java
@Test
void deveNegarDetalheDeChamadoDeOutroSolicitante() {
	UUID ticketId = UUID.fromString("e05968eb-a518-4ff9-8aa2-2d7a53497e45");
	UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
	User requester = new User("Joao", "joao@fadex.org.br", "hash", Role.SOLICITANTE, false);
	Ticket ticket = new Ticket("Titulo", "Descricao", TicketCategory.OUTROS, TicketPriority.MEDIA, ClassificationOrigin.PENDENTE, requester);

	when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
	when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
	when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);

	assertThatThrownBy(() -> ticketService.findById(ticketId))
			.isInstanceOf(ForbiddenException.class)
			.hasMessage("Acesso negado ao recurso solicitado.");
}
```

Use reflection or test helper to set `requester.id` to a different UUID if equality by id is needed.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketServiceTest
```

Expected: failure because role restrictions are not implemented.

- [ ] **Step 3: Implement access service and resolved filters**

Create `AccessControlService`:

```java
@Service
public class AccessControlService {

	private final AuthenticatedUserService authenticatedUserService;

	public boolean isAdmin() {
		return authenticatedUserService.getRole() == Role.ADMIN;
	}

	public UUID getAuthenticatedUserId() {
		return authenticatedUserService.getUserId();
	}

	public void assertCanAccessUser(UUID userId) {
		if (!isAdmin() && !getAuthenticatedUserId().equals(userId)) {
			throw new ForbiddenException("Acesso negado ao recurso solicitado.");
		}
	}

	public void assertCanAccessTicket(Ticket ticket) {
		if (isAdmin()) {
			return;
		}
		UUID requesterId = ticket.getRequester().getId();
		if (!getAuthenticatedUserId().equals(requesterId)) {
			throw new ForbiddenException("Acesso negado ao recurso solicitado.");
		}
	}
}
```

Update `TicketService.findAll`:

```java
TicketFilter resolvedFilter = resolveFilterByRole(filter);
Specification<Ticket> spec = TicketSpecification.createSpecification(resolvedFilter);
```

Add helper:

```java
private TicketFilter resolveFilterByRole(TicketFilter filter) {
	if (accessControlService.isAdmin()) {
		return filter;
	}
	return new TicketFilter(
			filter.status(),
			filter.priority(),
			filter.category(),
			accessControlService.getAuthenticatedUserId(),
			filter.assigneeId(),
			filter.search()
	);
}
```

Update `findById` and comment service to call `accessControlService.assertCanAccessTicket(ticket)`.

Update `UserService.findAll`:

```java
UserFilter resolvedFilter = accessControlService.isAdmin()
		? filter
		: new UserFilter(accessControlService.getAuthenticatedUserId(), filter.role(), filter.name(), filter.email(), filter.search());
```

Update `UserService.findById` to call `accessControlService.assertCanAccessUser(id)`.

- [ ] **Step 4: Run targeted tests**

Run:

```bash
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.UserServiceTest
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketServiceTest
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketCommentServiceTest
```

Expected: PASS.

- [ ] **Step 5: Run all backend tests**

Run:

```bash
make backend-test
```

Expected: PASS.

- [ ] **Step 6: Stage changes only**

Run:

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/security/AccessControlService.java \
  backend/src/main/java/br/org/fadex/helpdesk/security/AuthenticatedUserService.java \
  backend/src/main/java/br/org/fadex/helpdesk/service/UserService.java \
  backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java \
  backend/src/main/java/br/org/fadex/helpdesk/service/TicketCommentService.java \
  backend/src/test/java/br/org/fadex/helpdesk/service/UserServiceTest.java \
  backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java \
  backend/src/test/java/br/org/fadex/helpdesk/service/TicketCommentServiceTest.java
```

Expected: files staged; do not commit.

---

### Task 5: Historico de Eventos de Chamado

**Files:**
- Create: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketEventService.java`
- Create: `backend/src/main/java/br/org/fadex/helpdesk/controller/TicketEventController.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java`
- Modify: `backend/src/main/java/br/org/fadex/helpdesk/service/TicketCommentService.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketEventServiceTest.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java`
- Test: `backend/src/test/java/br/org/fadex/helpdesk/service/TicketCommentServiceTest.java`

**Interfaces:**
- Consumes: `TicketEvent`, `TicketEventRepository`, `TicketEventType` from Task 1.
- Consumes: `AccessControlService.assertCanAccessTicket(Ticket ticket)` from Task 4.
- Produces: `void TicketEventService.record(Ticket ticket, User actor, TicketEventType type, String description)`
- Produces: `Page<TicketEventMinDto> TicketEventService.findAll(UUID ticketId, TicketEventFilter filter, Pageable pageable)`

- [ ] **Step 1: Write failing event recording tests**

Add to `TicketServiceTest`:

```java
@Mock
private TicketEventService ticketEventService;

@Test
void deveGravarEventoAoCriarChamado() {
	UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
	User requester = new User("Maria", "maria@fadex.org.br", "hash", Role.SOLICITANTE, false);
	TicketCreationDto dto = new TicketCreationDto("Erro ao acessar sistema", "Nao consigo acessar o sistema interno.");

	when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);
	when(userService.findEntityById(authenticatedUserId)).thenReturn(requester);
	when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

	ticketService.create(dto);

	verify(ticketEventService).record(any(Ticket.class), eq(requester), eq(TicketEventType.CHAMADO_CRIADO), eq("Chamado criado."));
}
```

Add to `TicketCommentServiceTest`:

```java
@Mock
private TicketEventService ticketEventService;

@Test
void deveGravarEventoAoCriarComentario() {
	// reuse existing create-comment setup
	ticketCommentService.create(ticketId, creationDto);

	verify(ticketEventService).record(ticket, requester, TicketEventType.COMENTARIO_ADICIONADO, "Comentario adicionado.");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketServiceTest
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketCommentServiceTest
```

Expected: compile failure or verification failure because event service integration does not exist.

- [ ] **Step 3: Implement event service and integrations**

Create `TicketEventService`:

```java
@Service
public class TicketEventService {

	private final TicketEventRepository ticketEventRepository;
	private final TicketRepository ticketRepository;
	private final AccessControlService accessControlService;

	@Transactional
	public void record(Ticket ticket, User actor, TicketEventType type, String description) {
		TicketEvent event = new TicketEvent(ticket, actor, type, description, null);
		ticketEventRepository.save(event);
	}

	@Transactional(readOnly = true)
	public Page<TicketEventMinDto> findAll(UUID ticketId, TicketEventFilter filter, Pageable pageable) {
		Ticket ticket = ticketRepository.findById(ticketId)
				.orElseThrow(() -> new NotFoundException("Chamado nao encontrado."));
		accessControlService.assertCanAccessTicket(ticket);
		TicketEventFilter resolvedFilter = new TicketEventFilter(ticketId, filter.type());
		Specification<TicketEvent> spec = TicketEventSpecification.createSpecification(resolvedFilter);
		Page<TicketEvent> events = ticketEventRepository.findAll(spec, pageable);
		Page<TicketEventMinDto> response = events.map(TicketEventMapper::toMinDto);
		return response;
	}
}
```

This deliberately injects `TicketRepository` instead of `TicketService` to avoid a circular dependency, because `TicketService` records events through `TicketEventService`.

Update `TicketService.create` after save:

```java
ticketEventService.record(savedTicket, requester, TicketEventType.CHAMADO_CRIADO, "Chamado criado.");
```

Update `TicketCommentService.create` after save:

```java
ticketEventService.record(ticket, author, TicketEventType.COMENTARIO_ADICIONADO, "Comentario adicionado.");
```

- [ ] **Step 4: Implement event controller**

Create `TicketEventController`:

```java
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/events")
public class TicketEventController {

	private final TicketEventService ticketEventService;

	@GetMapping
	public ResponseEntity<Page<TicketEventMinDto>> findAll(
			@PathVariable UUID ticketId,
			@ModelAttribute TicketEventFilter filter,
			@PageableDefault(size = 10, sort = TicketEventFields.CREATED_AT, direction = Sort.Direction.DESC) Pageable pageable
	) {
		Page<TicketEventMinDto> events = ticketEventService.findAll(ticketId, filter, pageable);
		return ResponseEntity.ok(events);
	}
}
```

- [ ] **Step 5: Run targeted and full tests**

Run:

```bash
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketEventServiceTest
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketServiceTest
cd backend && ./gradlew test --tests br.org.fadex.helpdesk.service.TicketCommentServiceTest
make backend-test
```

Expected: PASS.

- [ ] **Step 6: Stage changes only**

Run:

```bash
git add backend/src/main/java/br/org/fadex/helpdesk/service/TicketEventService.java \
  backend/src/main/java/br/org/fadex/helpdesk/controller/TicketEventController.java \
  backend/src/main/java/br/org/fadex/helpdesk/service/TicketService.java \
  backend/src/main/java/br/org/fadex/helpdesk/service/TicketCommentService.java \
  backend/src/test/java/br/org/fadex/helpdesk/service/TicketEventServiceTest.java \
  backend/src/test/java/br/org/fadex/helpdesk/service/TicketServiceTest.java \
  backend/src/test/java/br/org/fadex/helpdesk/service/TicketCommentServiceTest.java
```

Expected: files staged; do not commit.

---

### Task 6: Contrato da API e Verificacao Final

**Files:**
- Modify: `docs/backend/api.md`
- Test: full backend verification

**Interfaces:**
- Consumes: all public API behavior from Tasks 1-5.
- Produces: updated API documentation for frontend implementation.

- [ ] **Step 1: Update API documentation**

Update `docs/backend/api.md` with these contract changes:

```markdown
### `POST /api/v1/auth/login`

Response `200`:

{
  "accessToken": "<jwt>",
  "refreshToken": "<refresh-token-ou-null>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "mustChangePassword": false,
  "role": "ADMIN",
  "user": {
    "id": "00000000-0000-0000-0000-000000000000",
    "name": "Administrador"
  }
}
```

Document:

```markdown
### `POST /api/v1/auth/refresh`
### `POST /api/v1/auth/change-password`
### `GET /api/v1/tickets/{ticketId}/events`
```

Update `POST /api/v1/users` request to remove `password`:

```json
{
  "name": "Solicitante",
  "email": "solicitante@fadex.org.br",
  "role": "SOLICITANTE"
}
```

Add role behavior:

```markdown
Usuarios com role `SOLICITANTE` enxergam apenas os proprios usuarios e chamados.
Ao listar chamados, a API força `requesterId` igual ao usuario autenticado.
Ao listar usuarios, a API força `id` igual ao usuario autenticado.
```

- [ ] **Step 2: Run final backend tests**

Run:

```bash
make backend-test
```

Expected: PASS.

- [ ] **Step 3: Inspect staged implementation**

Run:

```bash
git diff --cached --stat
git diff --stat
git status --short
```

Expected:

- Implementation and docs are staged.
- `Desafio_Analista_Desenvolvimento_Fadex.pdf` remains untracked and untouched.
- No implementation commits were created.

- [ ] **Step 4: Stage documentation only**

Run:

```bash
git add docs/backend/api.md
```

Expected: file staged; do not commit.

---

## Subagent Execution Notes

Use `superpowers:subagent-driven-development` for execution. Although several topics are conceptually independent, implementation on one branch should be sequenced by task to avoid overlapping edits to the same services and DTOs. Parallel agents are useful for investigation or review, but implementation tasks in this plan should be dispatched one task at a time with review gates.

Recommended execution order:

1. Task 1: Schema e modelos base.
2. Task 2: Criacao de usuario com senha provisoria e validacoes.
3. Task 3: Login, refresh token e troca obrigatoria de senha.
4. Task 4: RBAC por role.
5. Task 5: Historico de eventos.
6. Task 6: Contrato da API e verificacao final.

After all tasks pass review, stage implementation changes once and report the staged file groups. Do not create implementation commits unless the user explicitly asks.
