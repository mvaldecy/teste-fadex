package br.org.fadex.helpdesk.notification;

import br.org.fadex.helpdesk.mail.EmailMessage;
import br.org.fadex.helpdesk.mail.EmailSender;
import br.org.fadex.helpdesk.notification.event.TicketNotificationEvent;
import br.org.fadex.helpdesk.notification.event.UserCreatedNotificationEvent;
import br.org.fadex.helpdesk.sse.config.AsyncConfig;
import br.org.fadex.helpdesk.sse.model.NotificationEventName;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Transporte de e-mail do evento de dominio.
 *
 * Roda depois do commit e fora da thread da requisicao: e-mail que nao sai nao desfaz operacao de
 * negocio nem devolve erro para quem chamou a API. A falha vira log.
 */
@Slf4j
@Component
public class EmailNotificationListener {

	private final EmailSender emailSender;
	private final TicketEmailComposer emailComposer;

	public EmailNotificationListener(EmailSender emailSender, TicketEmailComposer emailComposer) {
		this.emailSender = emailSender;
		this.emailComposer = emailComposer;
	}

	@Async(AsyncConfig.SSE_NOTIFICATION_EXECUTOR)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onTicketNotification(TicketNotificationEvent event) {
		send(emailComposer.compose(event));
	}

	@Async(AsyncConfig.SSE_NOTIFICATION_EXECUTOR)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onUserCreated(UserCreatedNotificationEvent event) {
		send(List.of(emailComposer.compose(event)));
	}

	/**
	 * Falha de job de IA e derivada da propria mensagem SSE publicada pela frente de IA.
	 *
	 * O gatilho vive em {@code ai/}, que esta fora do escopo desta frente; escutar a mensagem em vez
	 * de editar o worker mantem as duas frentes sem sobreposicao de arquivos.
	 */
	@Async(AsyncConfig.SSE_NOTIFICATION_EXECUTOR)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onNotificationMessage(NotificationMessage message) {
		if (!NotificationEventName.JOB_IA_FALHOU.equals(message.eventName())) {
			return;
		}

		send(emailComposer.composeAiJobFailure(String.valueOf(message.data())));
	}

	private void send(List<EmailMessage> messages) {
		for (EmailMessage message : messages) {
			try {
				emailSender.send(message);
			} catch (Exception exception) {
				log.error(
						"Falha ao enviar e-mail \"{}\" para {}: {}",
						message.subject(),
						message.to(),
						exception.getMessage(),
						exception
				);
			}
		}
	}
}
