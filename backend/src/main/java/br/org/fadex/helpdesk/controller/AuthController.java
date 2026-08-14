package br.org.fadex.helpdesk.controller;

import br.org.fadex.helpdesk.model.auth.AuthRequestDto;
import br.org.fadex.helpdesk.model.auth.AuthResponseDto;
import br.org.fadex.helpdesk.model.auth.ChangePasswordRequestDto;
import br.org.fadex.helpdesk.model.auth.RefreshTokenRequestDto;
import br.org.fadex.helpdesk.security.AuthenticatedUserService;
import br.org.fadex.helpdesk.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final AuthenticatedUserService authenticatedUserService;

	public AuthController(AuthService authService, AuthenticatedUserService authenticatedUserService) {
		this.authService = authService;
		this.authenticatedUserService = authenticatedUserService;
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody AuthRequestDto authRequestDto) {
		AuthResponseDto auth = authService.login(authRequestDto);

		return ResponseEntity.ok(auth);
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto refreshTokenRequestDto) {
		AuthResponseDto auth = authService.refresh(refreshTokenRequestDto);

		return ResponseEntity.ok(auth);
	}

	@PostMapping("/change-password")
	public ResponseEntity<AuthResponseDto> changePassword(
			@Valid @RequestBody ChangePasswordRequestDto changePasswordRequestDto
	) {
		AuthResponseDto auth = authService.changePassword(
				authenticatedUserService.getUserId(),
				changePasswordRequestDto
		);

		return ResponseEntity.ok(auth);
	}
}
