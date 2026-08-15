package br.org.fadex.helpdesk.ai.classification;

import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Grava as colunas de auditoria da sugestao da IA.
 *
 * Sao colunas de auditoria pura: registram o que a IA sugeriu, separado do que vale hoje no chamado.
 * Nao passam por {@code applyClassification} porque nao mudam o que o chamado e, nao tem transicao
 * valida ou invalida e nao aparecem no historico. Categoria, prioridade e origem continuam
 * exclusivas da seam.
 *
 * Existe como service transacional proprio, em vez de uma mutacao solta dentro do worker, porque o
 * {@code AiJobWorker} e instanciado pelo Quartz: depender de uma transacao ambiente naquela thread
 * deixaria a escrita silenciosamente sem efeito se o proxy transacional nao se aplicasse. Aqui a
 * transacao e garantida, e o dirty checking do Hibernate emite o UPDATE no commit.
 */
@Service
public class TicketAiSuggestionService {

	private final TicketService ticketService;

	public TicketAiSuggestionService(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	/**
	 * Registra a sugestao e devolve o id do solicitante.
	 *
	 * O solicitante sai daqui, e nao de uma leitura do worker, porque o relacionamento e lazy e o
	 * worker roda em thread do Quartz, sem open-in-view: fora desta transacao a leitura falharia.
	 */
	@Transactional
	public UUID recordSuggestion(
			UUID ticketId,
			TicketCategory category,
			TicketPriority priority,
			Double confidence
	) {
		Ticket ticket = ticketService.findEntityById(ticketId);
		ticket.applyAiSuggestion(category, priority, confidence);

		UUID requesterId = ticket.getRequester().getId();

		return requesterId;
	}
}
