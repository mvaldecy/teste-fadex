package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.sse.config.AsyncConfig;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class NotificationDispatcher {

	private final NotificationService notificationService;

	public NotificationDispatcher(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@Async(AsyncConfig.SSE_NOTIFICATION_EXECUTOR)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onNotificationMessage(NotificationMessage message) {
		try {
			notificationService.dispatch(message);
		} catch (Exception exception) {
			log.error("Falha ao despachar notificacao {}: {}", message.eventId(), exception.getMessage(), exception);
		}
	}
}
