package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.model.auth.AuthRequestDto;
import br.org.fadex.helpdesk.model.auth.AuthResponseDto;
import br.org.fadex.helpdesk.model.auth.ChangePasswordRequestDto;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.UserRepository;
import br.org.fadex.helpdesk.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
