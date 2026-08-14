package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.exception.UnauthorizedException;
import br.org.fadex.helpdesk.model.auth.AuthRequestDto;
import br.org.fadex.helpdesk.model.auth.AuthResponseDto;
import br.org.fadex.helpdesk.model.auth.ChangePasswordRequestDto;
import br.org.fadex.helpdesk.model.auth.RefreshTokenRequestDto;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.model.user.UserMapper;
import br.org.fadex.helpdesk.repository.UserRepository;
import br.org.fadex.helpdesk.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;
	private final RefreshTokenService refreshTokenService;

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenService jwtTokenService,
			RefreshTokenService refreshTokenService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
		this.refreshTokenService = refreshTokenService;
	}

	public AuthResponseDto login(AuthRequestDto authRequestDto) {
		User user = userRepository.findByEmail(authRequestDto.email())
				.orElseThrow(() -> new UnauthorizedException("Credenciais invalidas."));

		validatePassword(authRequestDto.password(), user.getPasswordHash());

		if (Boolean.TRUE.equals(user.getMustChangePassword())) {
			return createPasswordChangeResponse(user);
		}

		return createRegularResponse(user);
	}

	public AuthResponseDto refresh(RefreshTokenRequestDto requestDto) {
		User user = refreshTokenService.validate(requestDto.refreshToken());

		if (Boolean.TRUE.equals(user.getMustChangePassword())) {
			throw new UnauthorizedException("Troca de senha obrigatoria.");
		}

		return createRegularResponse(user);
	}

	@Transactional
	public AuthResponseDto changePassword(UUID userId, ChangePasswordRequestDto requestDto) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new UnauthorizedException("Usuario autenticado invalido."));

		validatePassword(requestDto.currentPassword(), user.getPasswordHash());
		validatePasswordConfirmation(requestDto);

		String passwordHash = passwordEncoder.encode(requestDto.newPassword());
		user.changePassword(passwordHash);
		refreshTokenService.revokeActiveTokens(userId);

		return createRegularResponse(user);
	}

	private AuthResponseDto createPasswordChangeResponse(User user) {
		String accessToken = jwtTokenService.generatePasswordChangeToken(user);

		return new AuthResponseDto(
				accessToken,
				null,
				jwtTokenService.getTokenType(),
				jwtTokenService.getExpirationSeconds(),
				true,
				user.getRole(),
				UserMapper.toMinDto(user)
		);
	}

	private AuthResponseDto createRegularResponse(User user) {
		String accessToken = jwtTokenService.generateToken(user);
		String refreshToken = refreshTokenService.create(user);

		return new AuthResponseDto(
				accessToken,
				refreshToken,
				jwtTokenService.getTokenType(),
				jwtTokenService.getExpirationSeconds(),
				false,
				user.getRole(),
				UserMapper.toMinDto(user)
		);
	}

	private void validatePassword(String rawPassword, String passwordHash) {
		Boolean matches = passwordEncoder.matches(rawPassword, passwordHash);

		if (!matches) {
			throw new UnauthorizedException("Credenciais invalidas.");
		}
	}

	private void validatePasswordConfirmation(ChangePasswordRequestDto requestDto) {
		if (!requestDto.newPassword().equals(requestDto.confirmPassword())) {
			throw new UnauthorizedException("Confirmacao de senha invalida.");
		}
	}
}
