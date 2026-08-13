package br.org.fadex.helpdesk.model.auth;

import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.user.UserMinDto;

public record AuthResponseDto(
		String accessToken,
		String tokenType,
		Long expiresIn,
		Role role,
		UserMinDto user
) {
}
