package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.model.ticket.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TicketEmbeddingRepository extends JpaRepository<Ticket, UUID> {

	@Modifying
	@Query(value = """
			update tickets
			set embedding = cast(:embedding as vector),
			    embedding_model = :embeddingModel,
			    embedding_updated_at = :embeddingUpdatedAt
			where id = :ticketId
			""", nativeQuery = true)
	int updateEmbedding(
			@Param("ticketId") UUID ticketId,
			@Param("embedding") String embedding,
			@Param("embeddingModel") String embeddingModel,
			@Param("embeddingUpdatedAt") LocalDateTime embeddingUpdatedAt
	);
}
