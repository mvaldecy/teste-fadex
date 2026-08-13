package br.org.fadex.helpdesk.model.user;

import br.org.fadex.helpdesk.model.enums.Role;
import org.springframework.util.StringUtils;

import java.util.UUID;

public record UserFilter(
		UUID id,
		Role role,
		String name,
		String email,
		String search
) {

	public boolean hasId() {
		return id != null;
	}

	public boolean hasRole() {
		return role != null;
	}

	public boolean hasName() {
		return StringUtils.hasText(name);
	}

	public boolean hasEmail() {
		return StringUtils.hasText(email);
	}

	public boolean hasSearch() {
		return StringUtils.hasText(search);
	}
}
