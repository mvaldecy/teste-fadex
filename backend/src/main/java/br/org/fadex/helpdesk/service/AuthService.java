package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.exception.UnauthorizedException;
import br.org.fadex.helpdesk.model.auth.AuthRequestDto;
import br.org.fadex.helpdesk.model.auth.AuthResponseDto;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.model.user.UserMapper;
import br.org.fadex.helpdesk.repository.UserRepository;
import br.org.fadex.helpdesk.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenService jwtTokenService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
	}

	public AuthResponseDto login(AuthRequestDto authRequestDto) {
		User user = userRepository.findByEmail(authRequestDto.email())
				.orElseThrow(() -> new UnauthorizedException("Credenciais inválidas."));

		validatePassword(authRequestDto.password(), user.getPasswordHash());

		String accessToken = jwtTokenService.generateToken(user);
		AuthResponseDto response = new AuthResponseDto(
				accessToken,
				jwtTokenService.getTokenType(),
				jwtTokenService.getExpirationSeconds(),
				user.getRole(),
				UserMapper.toMinDto(user)
		);

		return response;
	}

	private void validatePassword(String rawPassword, String passwordHash) {
		Boolean matches = passwordEncoder.matches(rawPassword, passwordHash);

		if (!matches) {
			throw new UnauthorizedException("Credenciais inválidas.");
		}
	}
}
