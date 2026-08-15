package br.org.fadex.helpdesk.ai.duplicate;

import br.org.fadex.helpdesk.model.ticket.TicketLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Leitura dos vinculos de duplicidade. SOMENTE LEITURA.
 *
 * Sao duas consultas porque o vinculo e direcional: a deteccao grava {@code origem -> alvo} apenas
 * quando o job de embedding da origem roda. Para um chamado X o par pode estar gravado como
 * {@code Y -> X}, e consultar so uma direcao devolveria aba vazia justamente no chamado mais antigo
 * do par — que e o que o avaliador tende a abrir.
 */
public interface SimilarTicketRepository extends JpaRepository<TicketLink, UUID> {

	@Query("""
			select new br.org.fadex.helpdesk.ai.duplicate.SimilarTicketDto(
				ticket.id,
				ticket.title,
				ticket.status,
				ticket.priority,
				ticket.category,
				link.similarity,
				ticket.createdAt
			)
			from TicketLink link
			join link.targetTicket ticket
			where link.sourceTicket.id = :ticketId
			""")
	List<SimilarTicketDto> findLinkedAsSource(@Param("ticketId") UUID ticketId);

	@Query("""
			select new br.org.fadex.helpdesk.ai.duplicate.SimilarTicketDto(
				ticket.id,
				ticket.title,
				ticket.status,
				ticket.priority,
				ticket.category,
				link.similarity,
				ticket.createdAt
			)
			from TicketLink link
			join link.sourceTicket ticket
			where link.targetTicket.id = :ticketId
			""")
	List<SimilarTicketDto> findLinkedAsTarget(@Param("ticketId") UUID ticketId);

	/**
	 * Pares de vinculo que tocam qualquer um dos chamados informados.
	 *
	 * Uma consulta para a pagina inteira, e nao uma por chamado: o selo de semelhantes na listagem
	 * seria exatamente o tipo de enfeite que reintroduz o N+1 que acabamos de tirar dela. A
	 * contagem por chamado e feita em memoria sobre este resultado, que tem no maximo alguns pares
	 * por pagina.
	 */
	@Query("""
			select link.sourceTicket.id, link.targetTicket.id
			from TicketLink link
			where link.sourceTicket.id in :ids or link.targetTicket.id in :ids
			""")
	List<Object[]> findLinkedPairs(@Param("ids") Collection<UUID> ids);
}
