package br.org.fadex.helpdesk.service;

import br.org.fadex.helpdesk.exception.UnauthorizedException;
import br.org.fadex.helpdesk.model.token.RefreshToken;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenService {

	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final SecureRandom secureRandom = new SecureRandom();
	private final Long expirationSeconds;

	public RefreshTokenService(
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			@Value("${security.refresh-token.expiration-seconds}") Long expirationSeconds
	) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.expirationSeconds = expirationSeconds;
	}

	public String create(User user) {
		String secret = generateSecret();
		String tokenHash = passwordEncoder.encode(secret);
		LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expirationSeconds);
		RefreshToken refreshToken = refreshTokenRepository.save(new RefreshToken(user, tokenHash, expiresAt));

		return refreshToken.getId() + "." + secret;
	}

	@Transactional(readOnly = true)
	public User validate(String rawRefreshToken) {
		ParsedRefreshToken parsedToken = parse(rawRefreshToken);
		RefreshToken refreshToken = refreshTokenRepository.findWithUserById(parsedToken.id())
				.orElseThrow(() -> new UnauthorizedException("Refresh token invalido."));

		validateRefreshToken(parsedToken.secret(), refreshToken);

		return refreshToken.getUser();
	}

	@Transactional
	public void revokeActiveTokens(UUID userId) {
		List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
		activeTokens.forEach(RefreshToken::revoke);
	}

	private String generateSecret() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);

		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private void validateRefreshToken(String secret, RefreshToken refreshToken) {
		if (refreshToken.getRevokedAt() != null || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new UnauthorizedException("Refresh token invalido.");
		}

		if (!passwordEncoder.matches(secret, refreshToken.getTokenHash())) {
			throw new UnauthorizedException("Refresh token invalido.");
		}
	}

	private ParsedRefreshToken parse(String rawRefreshToken) {
		String[] parts = rawRefreshToken.split("\\.", -1);
		if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
			throw new UnauthorizedException("Refresh token invalido.");
		}

		try {
			return new ParsedRefreshToken(UUID.fromString(parts[0]), parts[1]);
		} catch (IllegalArgumentException exception) {
			throw new UnauthorizedException("Refresh token invalido.");
		}
	}

	private record ParsedRefreshToken(UUID id, String secret) {
	}
}
