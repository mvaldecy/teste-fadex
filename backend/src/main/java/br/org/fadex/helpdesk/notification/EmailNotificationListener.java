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

		send(emailComposer.composeAiJobFailure(describeFailure(message.data())));
	}

	/**
	 * O formato do {@code data} de {@code JOB_IA_FALHOU} pertence a frente de IA e pode ser texto ou
	 * DTO. Sem este tratamento, um record cairia no corpo do e-mail como {@code toString()} de Java.
	 *
	 * Texto entra direto; DTO entra pelo componente {@code lastError}; o resto cai numa frase fixa
	 * com o link do painel, que e o que o ADMIN precisa para agir.
	 */
	private String describeFailure(Object data) {
		if (data instanceof String text && !text.isBlank()) {
			return text;
		}

		String lastError = readLastError(data);

		if (lastError != null && !lastError.isBlank()) {
			return lastError;
		}

		return "Um job da triagem por IA falhou. Abra o painel de jobs para ver o erro e reagendar.";
	}

	private String readLastError(Object data) {
		if (data == null) {
			return null;
		}

		try {
			Object value = data.getClass().getMethod("lastError").invoke(data);

			return value == null ? null : value.toString();
		} catch (ReflectiveOperationException | RuntimeException exception) {
			log.debug("Payload de {} sem componente lastError.", NotificationEventName.JOB_IA_FALHOU);

			return null;
		}
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
