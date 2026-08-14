package br.org.fadex.helpdesk.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequestDto(
		@NotBlank(message = "Senha atual e obrigatoria.")
		String currentPassword,

		@NotBlank(message = "Nova senha e obrigatoria.")
		@Size(min = 8, max = 72, message = "Nova senha deve ter entre 8 e 72 caracteres.")
		String newPassword,

		@NotBlank(message = "Confirmacao de senha e obrigatoria.")
		String confirmPassword
) {
}
