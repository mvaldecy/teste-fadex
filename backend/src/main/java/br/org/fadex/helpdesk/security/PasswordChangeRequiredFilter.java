package br.org.fadex.helpdesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

	private static final String CHANGE_PASSWORD_PATH = "/api/v1/auth/change-password";
	private static final String MUST_CHANGE_PASSWORD_CLAIM = "mustChangePassword";

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
		Boolean changePasswordPath = CHANGE_PASSWORD_PATH.equals(request.getRequestURI());

		if (changePasswordPath && hasJwtAuthentication(authentication) && !requiresPasswordChange(authentication)) {
			restAccessDeniedHandler.handle(
					request,
					response,
					new AccessDeniedException("Token limitado de troca de senha obrigatorio.")
			);
			return;
		}

		if (requiresPasswordChange(authentication) && !changePasswordPath) {
			restAccessDeniedHandler.handle(
					request,
					response,
					new AccessDeniedException("Troca de senha obrigatoria.")
			);
			return;
		}

		filterChain.doFilter(request, response);
	}

	private Boolean hasJwtAuthentication(Authentication authentication) {
		return authentication != null && authentication.getPrincipal() instanceof Jwt;
	}

	private Boolean requiresPasswordChange(Authentication authentication) {
		if (!hasJwtAuthentication(authentication)) {
			return false;
		}

		Jwt jwt = (Jwt) authentication.getPrincipal();
		return Boolean.TRUE.equals(jwt.getClaimAsBoolean(MUST_CHANGE_PASSWORD_CLAIM));
	}
}
