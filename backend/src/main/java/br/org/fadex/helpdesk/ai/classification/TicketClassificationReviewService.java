package br.org.fadex.helpdesk.ai.classification;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketDto;
import br.org.fadex.helpdesk.security.AccessControlService;
import br.org.fadex.helpdesk.service.TicketService;
import br.org.fadex.helpdesk.sse.model.NotificationAudience;
import br.org.fadex.helpdesk.sse.model.NotificationEventName;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Revisao da classificacao pelo ADMIN: aceitar a sugestao da IA ou corrigi-la.
 *
 * A escrita em categoria, prioridade e origem acontece exclusivamente por
 * {@link TicketService#applyClassification}, que registra o evento de historico. Aqui ficam apenas
 * as duas regras proprias da revisao: decidir a origem resultante e carimbar o instante em que o
 * ADMIN olhou o chamado.
 */
@Service
public class TicketClassificationReviewService {

	private static final String ACCEPTED_JUSTIFICATION = "Sugestao da IA aceita pelo administrador.";
	private static final String CORRECTED_JUSTIFICATION = "Classificacao ajustada manualmente pelo administrador.";
	private static final String INDICATORS_REASON = "CLASSIFICACAO_REVISADA";

	private final TicketService ticketService;
	private final AccessControlService accessControlService;
	private final ApplicationEventPublisher applicationEventPublisher;
	private final Clock clock;

	public TicketClassificationReviewService(
			TicketService ticketService,
			AccessControlService accessControlService,
			ApplicationEventPublisher applicationEventPublisher,
			Clock clock
	) {
		this.ticketService = ticketService;
		this.accessControlService = accessControlService;
		this.applicationEventPublisher = applicationEventPublisher;
		this.clock = clock;
	}

	@Transactional
	public TicketDto review(UUID id, TicketClassificationUpdateDto ticketClassificationUpdateDto) {
		accessControlService.assertAdmin();

		Ticket ticket = ticketService.findEntityById(id);
		ClassificationOrigin origin = resolveOrigin(ticket, ticketClassificationUpdateDto);
		String justification = resolveJustification(ticketClassificationUpdateDto, origin);

		ticketService.applyClassification(
				id,
				ticketClassificationUpdateDto.category(),
				ticketClassificationUpdateDto.priority(),
				origin,
				justification
		);

		// O carimbo persiste por dirty checking na mesma transacao: findEntityById devolve a entidade
		// gerenciada e applyClassification roda com propagacao REQUIRED, entao ha uma unica transacao
		// e um unico flush. Nao mover a leitura para fora daqui — o carimbo se perderia, e e ele que
		// sustenta o denominador da concordancia admin x IA.
		ticket.markClassificationReviewed(LocalDateTime.now(clock));

		publishIndicatorsUpdated(id);

		TicketDto response = ticketService.findById(id);

		return response;
	}

	private ClassificationOrigin resolveOrigin(Ticket ticket, TicketClassificationUpdateDto dto) {
		boolean hasSuggestion = ticket.getAiSuggestedCategory() != null
				&& ticket.getAiSuggestedPriority() != null;

		if (!hasSuggestion) {
			return ClassificationOrigin.MANUAL;
		}

		boolean matchesSuggestion = ticket.getAiSuggestedCategory() == dto.category()
				&& ticket.getAiSuggestedPriority() == dto.priority();

		return matchesSuggestion ? ClassificationOrigin.IA : ClassificationOrigin.MANUAL;
	}

	private String resolveJustification(TicketClassificationUpdateDto dto, ClassificationOrigin origin) {
		if (dto.justification() != null && !dto.justification().isBlank()) {
			return dto.justification();
		}

		return origin == ClassificationOrigin.IA ? ACCEPTED_JUSTIFICATION : CORRECTED_JUSTIFICATION;
	}

	private void publishIndicatorsUpdated(UUID ticketId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("reason", INDICATORS_REASON);
		payload.put("ticketId", ticketId);
		payload.put("occurredAt", LocalDateTime.now(clock));

		NotificationMessage message = NotificationMessage.of(
				NotificationEventName.INDICADORES_ATUALIZADOS,
				payload,
				new NotificationAudience.Roles(Set.of(Role.ADMIN))
		);

		applicationEventPublisher.publishEvent(message);
	}
}
