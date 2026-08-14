package br.org.fadex.helpdesk.model.user;

import br.org.fadex.helpdesk.model.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserCreationDto(
		@NotBlank(message = "Nome e obrigatorio.")
		@Size(max = 120, message = "Nome deve ter no maximo 120 caracteres.")
		String name,

		@NotBlank(message = "E-mail e obrigatorio.")
		@Email(message = "E-mail deve ter formato valido.")
		@Size(max = 180, message = "E-mail deve ter no maximo 180 caracteres.")
		String email,

		@NotNull(message = "Perfil e obrigatorio.")
		Role role
) {
}
