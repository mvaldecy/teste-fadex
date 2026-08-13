package br.org.fadex.helpdesk.model.user;

import br.org.fadex.helpdesk.model.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserCreationDto(
		@NotBlank
		@Size(max = 120)
		String name,

		@NotBlank
		@Email
		@Size(max = 180)
		String email,

		@NotBlank
		@Size(min = 6, max = 72)
		String password,

		@NotNull
		Role role
) {
}
