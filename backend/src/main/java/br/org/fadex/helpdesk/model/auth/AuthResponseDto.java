package br.org.fadex.helpdesk.model.auth;

import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.user.UserMinDto;

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
