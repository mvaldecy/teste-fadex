package br.org.fadex.helpdesk.model.user;

import java.util.UUID;

public record UserMinDto(
		UUID id,
		String name
) {
}
