package br.org.fadex.helpdesk.security;

import br.org.fadex.helpdesk.model.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class JwtTokenService {

	private static final String TOKEN_TYPE = "Bearer";

	private final JwtEncoder jwtEncoder;
	private final Long expirationSeconds;

	public JwtTokenService(
			JwtEncoder jwtEncoder,
			@Value("${security.jwt.expiration-seconds}") Long expirationSeconds
	) {
		this.jwtEncoder = jwtEncoder;
		this.expirationSeconds = expirationSeconds;
	}

	public String generateToken(User user) {
		return generateToken(user, false);
	}

	public String generatePasswordChangeToken(User user) {
		return generateToken(user, true);
	}

	private String generateToken(User user, Boolean mustChangePassword) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);

		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(user.getEmail())
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim("userId", user.getId().toString())
				.claim("role", user.getRole().name())
				.claim("mustChangePassword", mustChangePassword)
				.build();

		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public String getTokenType() {
		return TOKEN_TYPE;
	}

	public Long getExpirationSeconds() {
		return expirationSeconds;
	}
}
