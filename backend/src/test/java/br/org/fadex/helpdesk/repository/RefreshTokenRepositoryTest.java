package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.config.JpaAuditingConfig;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.token.RefreshToken;
import br.org.fadex.helpdesk.model.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class RefreshTokenRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void deveCarregarUsuarioJuntoAoBuscarRefreshTokenPorId() {
		User user = userRepository.saveAndFlush(new User(
				"Maria Solicitante",
				"maria.refresh@fadex.org.br",
				"hash",
				Role.SOLICITANTE,
				false
		));
		RefreshToken refreshToken = refreshTokenRepository.saveAndFlush(new RefreshToken(
				user,
				"hash-token",
				LocalDateTime.now().plusDays(7)
		));
		entityManager.clear();

		RefreshToken foundToken = refreshTokenRepository.findWithUserById(refreshToken.getId()).orElseThrow();

		Boolean userLoaded = entityManager.getEntityManagerFactory()
				.getPersistenceUnitUtil()
				.isLoaded(foundToken, "user");
		assertThat(userLoaded).isTrue();
		assertThat(foundToken.getUser().getEmail()).isEqualTo("maria.refresh@fadex.org.br");
	}
}
