package br.org.fadex.helpdesk.security;

import br.org.fadex.helpdesk.exception.UnauthorizedException;
import br.org.fadex.helpdesk.model.enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthenticatedUserService {

	private static final String USER_ID_CLAIM = "userId";
	private static final String ROLE_CLAIM = "role";

	public UUID getUserId() {
		Jwt jwt = getJwt();
		String userId = jwt.getClaimAsString(USER_ID_CLAIM);

		if (userId == null) {
			throw new UnauthorizedException("Usuário autenticado inválido.");
		}

		return UUID.fromString(userId);
	}

	public String getEmail() {
		Jwt jwt = getJwt();

		return jwt.getSubject();
	}

	public Role getRole() {
		Jwt jwt = getJwt();
		String role = jwt.getClaimAsString(ROLE_CLAIM);

		if (role == null) {
			throw new UnauthorizedException("Usuário autenticado inválido.");
		}

		return Role.valueOf(role);
	}

	private Jwt getJwt() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
			throw new UnauthorizedException("Autenticação necessária.");
		}

		return jwt;
	}
}
