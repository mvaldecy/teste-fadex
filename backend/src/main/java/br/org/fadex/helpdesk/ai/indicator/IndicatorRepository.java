package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.ticket.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Leitura agregada de chamados para os indicadores.
 *
 * SOMENTE LEITURA — nenhum metodo de escrita entra aqui (decisao D4 do design). Existe separado de
 * {@code TicketRepository}, que pertence a frente de API, para que as queries de agregacao desta
 * frente nao disputem o mesmo arquivo no merge.
 */
public interface IndicatorRepository extends JpaRepository<Ticket, UUID> {

	@Query("""
			select new br.org.fadex.helpdesk.ai.indicator.TicketIndicatorProjection(
				ticket.id,
				ticket.status,
				ticket.priority,
				ticket.category,
				ticket.classificationOrigin,
				ticket.aiSuggestedCategory,
				ticket.aiSuggestedPriority,
				ticket.aiConfidence,
				requester.id,
				requester.name,
				assignee.id,
				assignee.name,
				ticket.createdAt,
				ticket.assignedAt,
				ticket.firstResponseAt,
				ticket.closedAt,
				ticket.classificationReviewedAt
			)
			from Ticket ticket
			join ticket.requester requester
			left join ticket.assignee assignee
			""")
	List<TicketIndicatorProjection> findAllProjections();
}
