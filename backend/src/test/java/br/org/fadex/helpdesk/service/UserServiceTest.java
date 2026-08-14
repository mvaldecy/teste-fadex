package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.exception.ConflictException;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.model.user.UserCreationDto;
import br.org.fadex.helpdesk.model.user.UserDto;
import br.org.fadex.helpdesk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserService userService;

	@Test
	void deveCriarUsuarioComSenhaCriptografadaEResponderComDtoCompleto() {
		UserCreationDto userCreationDto = new UserCreationDto(
				"Maria Solicitante",
				"maria@fadex.org.br",
				"senha123",
				Role.SOLICITANTE
		);
		User savedUser = new User(
				"Maria Solicitante",
				"maria@fadex.org.br",
				"hash-gerado",
				Role.SOLICITANTE
		);
		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

		when(userRepository.existsByEmail("maria@fadex.org.br")).thenReturn(false);
		when(passwordEncoder.encode("senha123")).thenReturn("hash-gerado");
		when(userRepository.save(any(User.class))).thenReturn(savedUser);

		UserDto response = userService.create(userCreationDto);

		verify(userRepository).save(userCaptor.capture());
		User userToSave = userCaptor.getValue();

		assertThat(userToSave.getName()).isEqualTo("Maria Solicitante");
		assertThat(userToSave.getEmail()).isEqualTo("maria@fadex.org.br");
		assertThat(userToSave.getPasswordHash()).isEqualTo("hash-gerado");
		assertThat(userToSave.getRole()).isEqualTo(Role.SOLICITANTE);
		assertThat(response.name()).isEqualTo("Maria Solicitante");
		assertThat(response.email()).isEqualTo("maria@fadex.org.br");
		assertThat(response.role()).isEqualTo(Role.SOLICITANTE);
	}

	@Test
	void deveImpedirCriacaoDeUsuarioComEmailJaCadastrado() {
		UserCreationDto userCreationDto = new UserCreationDto(
				"Maria Solicitante",
				"maria@fadex.org.br",
				"senha123",
				Role.SOLICITANTE
		);

		when(userRepository.existsByEmail("maria@fadex.org.br")).thenReturn(true);

		assertThatThrownBy(() -> userService.create(userCreationDto))
				.isInstanceOf(ConflictException.class)
				.hasMessage("E-mail já cadastrado.");

		verify(passwordEncoder, never()).encode("senha123");
		verify(userRepository, never()).save(any(User.class));
	}
}
