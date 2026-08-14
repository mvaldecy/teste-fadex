package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.exception.UnauthorizedException;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.token.RefreshToken;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	private RefreshTokenService refreshTokenService;

	@BeforeEach
	void setUp() {
		refreshTokenService = new RefreshTokenService(refreshTokenRepository, passwordEncoder, 3600L);
	}

	@Test
	void deveRejeitarTokenMalformadoSemConsultarRepositorio() {
		String malformedToken = UUID.fromString("f0a9f1bc-d2f8-410e-9134-f65510027fb8") + ".segredo.extra";

		assertThatThrownBy(() -> refreshTokenService.validate(malformedToken))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessage("Refresh token invalido.");

		verify(refreshTokenRepository, never()).findWithUserById(any(UUID.class));
		verify(passwordEncoder, never()).matches(anyString(), anyString());
	}

	@Test
	void deveRejeitarTokenComIdInexistente() {
		UUID tokenId = UUID.fromString("f0a9f1bc-d2f8-410e-9134-f65510027fb8");
		String rawRefreshToken = tokenId + ".segredo";

		when(refreshTokenRepository.findWithUserById(tokenId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> refreshTokenService.validate(rawRefreshToken))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessage("Refresh token invalido.");

		verify(passwordEncoder, never()).matches(anyString(), anyString());
	}

	@Test
	void deveRejeitarTokenComSecretIncorreto() {
		UUID tokenId = UUID.fromString("f0a9f1bc-d2f8-410e-9134-f65510027fb8");
		RefreshToken refreshToken = createRefreshToken(tokenId, LocalDateTime.now().plusMinutes(10));

		when(refreshTokenRepository.findWithUserById(tokenId)).thenReturn(Optional.of(refreshToken));
		when(passwordEncoder.matches("segredo-incorreto", "hash-salvo")).thenReturn(false);

		assertThatThrownBy(() -> refreshTokenService.validate(tokenId + ".segredo-incorreto"))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessage("Refresh token invalido.");
	}

	@Test
	void deveRejeitarTokenExpirado() {
		UUID tokenId = UUID.fromString("f0a9f1bc-d2f8-410e-9134-f65510027fb8");
		RefreshToken refreshToken = createRefreshToken(tokenId, LocalDateTime.now().minusMinutes(1));

		when(refreshTokenRepository.findWithUserById(tokenId)).thenReturn(Optional.of(refreshToken));

		assertThatThrownBy(() -> refreshTokenService.validate(tokenId + ".segredo"))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessage("Refresh token invalido.");

		verify(passwordEncoder, never()).matches(anyString(), anyString());
	}

	@Test
	void deveRejeitarTokenRevogado() {
		UUID tokenId = UUID.fromString("f0a9f1bc-d2f8-410e-9134-f65510027fb8");
		RefreshToken refreshToken = createRefreshToken(tokenId, LocalDateTime.now().plusMinutes(10));
		refreshToken.revoke();

		when(refreshTokenRepository.findWithUserById(tokenId)).thenReturn(Optional.of(refreshToken));

		assertThatThrownBy(() -> refreshTokenService.validate(tokenId + ".segredo"))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessage("Refresh token invalido.");

		verify(passwordEncoder, never()).matches(anyString(), anyString());
	}

	@Test
	void deveRetornarUsuarioQuandoTokenForValido() {
		UUID tokenId = UUID.fromString("f0a9f1bc-d2f8-410e-9134-f65510027fb8");
		User user = new User(
				"Maria Solicitante",
				"maria@fadex.org.br",
				"hash-senha",
				Role.SOLICITANTE,
				false
		);
		RefreshToken refreshToken = createRefreshToken(tokenId, user, LocalDateTime.now().plusMinutes(10));

		when(refreshTokenRepository.findWithUserById(tokenId)).thenReturn(Optional.of(refreshToken));
		when(passwordEncoder.matches("segredo-correto", "hash-salvo")).thenReturn(true);

		User response = refreshTokenService.validate(tokenId + ".segredo-correto");

		assertThat(response).isSameAs(user);
	}

	private RefreshToken createRefreshToken(UUID id, LocalDateTime expiresAt) {
		User user = new User(
				"Maria Solicitante",
				"maria@fadex.org.br",
				"hash-senha",
				Role.SOLICITANTE,
				false
		);

		return createRefreshToken(id, user, expiresAt);
	}

	private RefreshToken createRefreshToken(UUID id, User user, LocalDateTime expiresAt) {
		RefreshToken refreshToken = new RefreshToken(user, "hash-salvo", expiresAt);
		ReflectionTestUtils.setField(refreshToken, "id", id);

		return refreshToken;
	}
}
