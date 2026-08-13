package br.org.fadex.helpdesk.controller;

import br.org.fadex.helpdesk.model.auth.AuthRequestDto;
import br.org.fadex.helpdesk.model.auth.AuthResponseDto;
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

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody AuthRequestDto authRequestDto) {
		AuthResponseDto auth = authService.login(authRequestDto);

		return ResponseEntity.ok(auth);
	}
}
