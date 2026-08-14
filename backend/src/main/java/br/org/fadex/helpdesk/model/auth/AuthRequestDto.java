package br.org.fadex.helpdesk.model.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthRequestDto(
		@NotBlank(message = "E-mail e obrigatorio.")
		@Email(message = "E-mail deve ter formato valido.")
		String email,

		@NotBlank(message = "Senha e obrigatoria.")
		String password
) {
}
