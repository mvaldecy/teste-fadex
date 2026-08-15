package br.org.fadex.helpdesk.ai.duplicate;

import br.org.fadex.helpdesk.model.ticket.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Leitura dos embeddings gravados, para deteccao de duplicados.
 *
 * SOMENTE LEITURA — nenhum metodo de escrita pode ser adicionado aqui. {@code TicketRepository}
 * pertence a frente API e nao e alterado por esta frente.
 *
 * O {@code cast(... as varchar)} mantem a query identica no Postgres, onde a coluna e {@code vector},
 * e no H2 dos testes, onde e {@code varchar}. Sem o cast, o driver do Postgres nao converte
 * {@code vector} para {@code String}.
 */
public interface DuplicateEmbeddingRepository extends JpaRepository<Ticket, UUID> {

	@Query(value = """
			select cast(id as varchar), cast(embedding as varchar)
			from tickets
			where embedding is not null
			""", nativeQuery = true)
	List<Object[]> findEmbeddedTickets();

	/** Dados de exibicao dos chamados do ranking, numa consulta so. */
	@Query("""
			select ticket
			from Ticket ticket
			where ticket.id in :ids
			""")
	List<Ticket> findAllByIdIn(@Param("ids") Collection<UUID> ids);
}
