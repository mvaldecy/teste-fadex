package br.org.fadex.helpdesk.model.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthRequestDto(
		@NotBlank
		@Email
		String email,

		@NotBlank
		String password
) {
}
