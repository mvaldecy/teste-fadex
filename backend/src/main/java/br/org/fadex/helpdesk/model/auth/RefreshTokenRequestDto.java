package br.org.fadex.helpdesk.model.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDto(
		@NotBlank(message = "Refresh token e obrigatorio.")
		String refreshToken
) {
}
