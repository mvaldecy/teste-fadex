package br.org.fadex.helpdesk.model.user;

import br.org.fadex.helpdesk.model.enums.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserDto(
		UUID id,
		String name,
		String email,
		Role role,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
}
