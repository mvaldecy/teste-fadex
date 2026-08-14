package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.model.token.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	@EntityGraph(attributePaths = "user")
	Optional<RefreshToken> findWithUserById(UUID id);

	List<RefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);
}
