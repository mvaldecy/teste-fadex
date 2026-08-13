package br.org.fadex.helpdesk.security;

import br.org.fadex.helpdesk.exception.UnauthorizedException;
import br.org.fadex.helpdesk.model.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedUserServiceTest {

	private final AuthenticatedUserService authenticatedUserService = new AuthenticatedUserService();

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void deveLerDadosDoUsuarioAutenticadoAPartirDosClaimsDoJwt() {
		UUID userId = UUID.fromString("71e9c3d9-53b2-4c4e-9803-c504754dbb45");
		Jwt jwt = createJwt(userId, "admin@fadex.org.br", Role.ADMIN);
		JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
		SecurityContextHolder.getContext().setAuthentication(authentication);

		assertThat(authenticatedUserService.getUserId()).isEqualTo(userId);
		assertThat(authenticatedUserService.getEmail()).isEqualTo("admin@fadex.org.br");
		assertThat(authenticatedUserService.getRole()).isEqualTo(Role.ADMIN);
	}

	@Test
	void deveFalharQuandoNaoHouverUsuarioAutenticado() {
		assertThatThrownBy(authenticatedUserService::getUserId)
				.isInstanceOf(UnauthorizedException.class)
				.hasMessage("Autenticação necessária.");
	}

	private Jwt createJwt(UUID userId, String email, Role role) {
		Instant issuedAt = Instant.parse("2026-08-13T20:00:00Z");
		Instant expiresAt = Instant.parse("2026-08-13T21:00:00Z");

		return Jwt.withTokenValue("token")
				.header("alg", "HS256")
				.subject(email)
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim("userId", userId.toString())
				.claim("role", role.name())
				.build();
	}
}
