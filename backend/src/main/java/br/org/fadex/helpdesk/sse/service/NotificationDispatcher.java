package br.org.fadex.helpdesk.sse.service;

import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationDispatcher {

	private final NotificationService notificationService;

	public NotificationDispatcher(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onNotificationMessage(NotificationMessage message) {
		notificationService.dispatch(message);
	}
}
