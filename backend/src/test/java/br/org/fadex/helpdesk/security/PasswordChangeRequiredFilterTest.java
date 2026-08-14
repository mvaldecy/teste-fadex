package br.org.fadex.helpdesk.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PasswordChangeRequiredFilterTest {

	private final RestAccessDeniedHandler restAccessDeniedHandler = new RestAccessDeniedHandler(new ObjectMapper());
	private final PasswordChangeRequiredFilter filter = new PasswordChangeRequiredFilter(restAccessDeniedHandler);

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

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

	@Test
	void deveBloquearTrocaDeSenhaQuandoTokenNaoForLimitado() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/change-password");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "HS256")
				.subject("admin@fadex.org.br")
				.claim("userId", UUID.randomUUID().toString())
				.claim("role", "ADMIN")
				.build();
		Authentication authentication = new JwtAuthenticationToken(jwt);
		SecurityContextHolder.getContext().setAuthentication(authentication);

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
		verify(chain, never()).doFilter(request, response);
	}

	@Test
	void devePermitirTrocaDeSenhaQuandoTokenForLimitado() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/change-password");
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

		assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
		verify(chain).doFilter(request, response);
	}
}
