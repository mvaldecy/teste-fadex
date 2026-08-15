package br.org.fadex.helpdesk.notification;

import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.notification.event.NotificationRecipient;
import br.org.fadex.helpdesk.notification.event.TicketNotificationEvent;
import br.org.fadex.helpdesk.sse.config.AsyncConfig;
import br.org.fadex.helpdesk.sse.model.NotificationAudience;
import br.org.fadex.helpdesk.sse.model.NotificationEventName;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import br.org.fadex.helpdesk.sse.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Transporte SSE do evento de dominio do chamado.
 *
 * O {@code NotificationDispatcher} continua existindo e nao foi tocado: ele atende quem publica
 * {@code NotificationMessage} direto, como a frente de IA.
 */
@Slf4j
@Component
public class TicketSseNotificationListener {

	private final NotificationService notificationService;

	public TicketSseNotificationListener(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@Async(AsyncConfig.SSE_NOTIFICATION_EXECUTOR)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onTicketNotification(TicketNotificationEvent event) {
		for (NotificationMessage message : toMessages(event)) {
			try {
				notificationService.dispatch(message);
			} catch (Exception exception) {
				log.error(
						"Falha ao despachar notificacao {}: {}",
						message.eventId(),
						exception.getMessage(),
						exception
				);
			}
		}
	}

	List<NotificationMessage> toMessages(TicketNotificationEvent event) {
		List<NotificationMessage> messages = new ArrayList<>();

		messages.add(NotificationMessage.of(
				NotificationEventName.CHAMADO_ATUALIZADO,
				event.ticket(),
				audienceFor(event)
		));

		if (event.becameHighPriority()) {
			messages.add(NotificationMessage.of(
					NotificationEventName.CHAMADO_ALTA_PRIORIDADE,
					event.ticket(),
					new NotificationAudience.Roles(Set.of(Role.ADMIN))
			));
		}

		return messages;
	}

	/**
	 * A audiencia inclui todo ADMIN, e nao apenas solicitante e responsavel. O motivo e o mesmo
	 * em qualquer mutacao: o ADMIN enxerga todos os chamados na listagem, entao precisa ver a
	 * linha mudar sem recarregar a pagina. Restringir as atualizacoes aos dois envolvidos deixava
	 * a listagem do ADMIN parada enquanto o painel dele reagia — os indicadores ja iam para
	 * {@code Roles(ADMIN)} —, o que parecia falha do tempo real e era so a audiencia.
	 */
	private NotificationAudience audienceFor(TicketNotificationEvent event) {
		Set<UUID> userIds = new HashSet<>();
		userIds.add(event.requester().id());

		NotificationRecipient assignee = event.assignee();

		if (assignee != null) {
			userIds.add(assignee.id());
		}

		return new NotificationAudience.UsersAndRoles(userIds, Set.of(Role.ADMIN));
	}
}
