package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.exception.ConflictException;
import br.org.fadex.helpdesk.exception.ForbiddenException;
import br.org.fadex.helpdesk.mail.EmailMessage;
import br.org.fadex.helpdesk.mail.EmailSender;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.model.user.UserCreationDto;
import br.org.fadex.helpdesk.model.user.UserDto;
import br.org.fadex.helpdesk.model.user.UserFields;
import br.org.fadex.helpdesk.model.user.UserFilter;
import br.org.fadex.helpdesk.repository.UserRepository;
import br.org.fadex.helpdesk.security.AccessControlService;
import br.org.fadex.helpdesk.security.AuthenticatedUserService;
import br.org.fadex.helpdesk.security.TemporaryPasswordGenerator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private TemporaryPasswordGenerator temporaryPasswordGenerator;

	@Mock
	private EmailSender emailSender;

	@Mock
	private AuthenticatedUserService authenticatedUserService;

	private AccessControlService accessControlService;

	private UserService userService;

	@BeforeEach
	void setUp() {
		accessControlService = new AccessControlService(authenticatedUserService);
		userService = new UserService(
				userRepository,
				passwordEncoder,
				temporaryPasswordGenerator,
				emailSender,
				accessControlService
		);
	}

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

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(userRepository.existsByEmail("maria@fadex.org.br")).thenReturn(false);
		when(temporaryPasswordGenerator.generate()).thenReturn("SenhaProvisoria123");
		when(passwordEncoder.encode("SenhaProvisoria123")).thenReturn("hash-gerado");
		when(userRepository.save(any(User.class))).thenReturn(savedUser);

		UserDto response = userService.create(userCreationDto);

		verify(userRepository).save(userCaptor.capture());
		verify(emailSender).send(emailCaptor.capture());
		User userToSave = userCaptor.getValue();
		EmailMessage email = emailCaptor.getValue();

		assertThat(userToSave.getName()).isEqualTo("Maria Solicitante");
		assertThat(userToSave.getEmail()).isEqualTo("maria@fadex.org.br");
		assertThat(userToSave.getPasswordHash()).isEqualTo("hash-gerado");
		assertThat(userToSave.getRole()).isEqualTo(Role.SOLICITANTE);
		assertThat(userToSave.getMustChangePassword()).isTrue();
		assertThat(email.to()).isEqualTo("maria@fadex.org.br");
		assertThat(email.subject()).isEqualTo("Acesso provisorio ao Fadex Helpdesk");
		assertThat(email.text()).contains("SenhaProvisoria123");
		assertThat(response.name()).isEqualTo("Maria Solicitante");
		assertThat(response.email()).isEqualTo("maria@fadex.org.br");
		assertThat(response.role()).isEqualTo(Role.SOLICITANTE);
		assertThat(response.mustChangePassword()).isTrue();
	}

	@Test
	void deveNegarCriacaoDeUsuarioParaSolicitanteSemGerarSenhaSalvarOuEnviarEmail() {
		UserCreationDto userCreationDto = new UserCreationDto(
				"Maria Solicitante",
				"maria@fadex.org.br",
				Role.SOLICITANTE
		);

		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);

		assertThatThrownBy(() -> userService.create(userCreationDto))
				.isInstanceOf(ForbiddenException.class)
				.hasMessage("Acesso negado ao recurso solicitado.");

		verify(userRepository, never()).existsByEmail(any(String.class));
		verify(temporaryPasswordGenerator, never()).generate();
		verify(passwordEncoder, never()).encode(any(String.class));
		verify(userRepository, never()).save(any(User.class));
		verify(emailSender, never()).send(any(EmailMessage.class));
	}

	@Test
	void deveImpedirCriacaoDeUsuarioComEmailJaCadastrado() {
		UserCreationDto userCreationDto = new UserCreationDto(
				"Maria Solicitante",
				"maria@fadex.org.br",
				Role.SOLICITANTE
		);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(userRepository.existsByEmail("maria@fadex.org.br")).thenReturn(true);

		assertThatThrownBy(() -> userService.create(userCreationDto))
				.isInstanceOf(ConflictException.class)
				.hasMessage("E-mail já cadastrado.");

		verify(temporaryPasswordGenerator, never()).generate();
		verify(emailSender, never()).send(any(EmailMessage.class));
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void deveForcarUsuarioAutenticadoNoFiltroDeUsuarios() {
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		UUID requestedUserId = UUID.fromString("b9ff9b29-e32b-4a51-a586-0119beeb0cd5");
		PageRequest pageable = PageRequest.of(0, 10);
		ArgumentCaptor<Specification<User>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);

		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);
		when(userRepository.findAll(anyUserSpecification(), eq(pageable))).thenReturn(Page.empty(pageable));

		userService.findAll(new UserFilter(requestedUserId, Role.ADMIN, null, null, "maria"), pageable);

		verify(userRepository).findAll(specificationCaptor.capture(), eq(pageable));
		assertUserSpecificationFiltersId(specificationCaptor.getValue(), authenticatedUserId, requestedUserId);
	}

	@Test
	void deveManterFiltroInformadoParaAdminAoListarUsuarios() {
		UUID requestedUserId = UUID.fromString("b9ff9b29-e32b-4a51-a586-0119beeb0cd5");
		PageRequest pageable = PageRequest.of(0, 10);
		ArgumentCaptor<Specification<User>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);

		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(userRepository.findAll(anyUserSpecification(), eq(pageable))).thenReturn(Page.empty(pageable));

		userService.findAll(new UserFilter(requestedUserId, Role.SOLICITANTE, null, null, null), pageable);

		verify(userRepository).findAll(specificationCaptor.capture(), eq(pageable));
		assertUserSpecificationFiltersId(specificationCaptor.getValue(), requestedUserId, null);
		verify(authenticatedUserService, never()).getUserId();
	}

	@Test
	void deveNegarDetalheDeUsuarioDiferenteParaSolicitante() {
		UUID requestedUserId = UUID.fromString("b9ff9b29-e32b-4a51-a586-0119beeb0cd5");
		UUID authenticatedUserId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");

		when(authenticatedUserService.getRole()).thenReturn(Role.SOLICITANTE);
		when(authenticatedUserService.getUserId()).thenReturn(authenticatedUserId);

		assertThatThrownBy(() -> userService.findById(requestedUserId))
				.isInstanceOf(ForbiddenException.class)
				.hasMessage("Acesso negado ao recurso solicitado.");

		verify(userRepository, never()).findById(any(UUID.class));
	}

	@Test
	void devePermitirDetalheDeUsuarioParaAdmin() {
		UUID requestedUserId = UUID.fromString("b9ff9b29-e32b-4a51-a586-0119beeb0cd5");
		User user = new User(
				"Maria Solicitante",
				"maria@fadex.org.br",
				"senha-com-hash",
				Role.SOLICITANTE,
				false
		);

		org.springframework.test.util.ReflectionTestUtils.setField(user, "id", requestedUserId);
		when(authenticatedUserService.getRole()).thenReturn(Role.ADMIN);
		when(userRepository.findById(requestedUserId)).thenReturn(java.util.Optional.of(user));

		UserDto response = userService.findById(requestedUserId);

		assertThat(response.id()).isEqualTo(requestedUserId);
		assertThat(response.name()).isEqualTo("Maria Solicitante");
		verify(authenticatedUserService, never()).getUserId();
	}

	private Specification<User> anyUserSpecification() {
		return any();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void assertUserSpecificationFiltersId(
			Specification<User> specification,
			UUID expectedUserId,
			UUID forbiddenUserId
	) {
		Root<User> root = mock(Root.class);
		CriteriaQuery<?> query = mock(CriteriaQuery.class);
		CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
		Path<Object> userIdPath = mock(Path.class);
		Predicate userIdPredicate = mock(Predicate.class);
		Predicate combinedPredicate = mock(Predicate.class);

		when(root.get(UserFields.ID)).thenReturn((Path) userIdPath);
		when(criteriaBuilder.equal(userIdPath, expectedUserId)).thenReturn(userIdPredicate);
		when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(combinedPredicate);

		specification.toPredicate(root, query, criteriaBuilder);

		verify(criteriaBuilder).equal(userIdPath, expectedUserId);
		if (forbiddenUserId != null) {
			verify(criteriaBuilder, never()).equal(userIdPath, forbiddenUserId);
		}
	}
}
