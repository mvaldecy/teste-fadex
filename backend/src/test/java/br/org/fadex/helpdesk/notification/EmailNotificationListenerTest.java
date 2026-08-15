package br.org.fadex.helpdesk.notification;

import br.org.fadex.helpdesk.mail.EmailDeliveryException;
import br.org.fadex.helpdesk.mail.EmailMessage;
import br.org.fadex.helpdesk.mail.EmailSender;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.notification.event.UserCreatedNotificationEvent;
import br.org.fadex.helpdesk.sse.model.NotificationAudience;
import br.org.fadex.helpdesk.sse.model.NotificationEventName;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationListenerTest {

	@Mock
	private EmailSender emailSender;

	@Mock
	private TicketEmailComposer emailComposer;

	@InjectMocks
	private EmailNotificationListener listener;

	@Test
	void deveEnviarUmaMensagemPorDestinatario() {
		UserCreatedNotificationEvent event = new UserCreatedNotificationEvent(
				UUID.randomUUID(),
				"Maria",
				"maria@fadex.org.br",
				"SenhaProvisoria123"
		);
		EmailMessage message = new EmailMessage("maria@fadex.org.br", "Assunto", "Texto");

		when(emailComposer.compose(event)).thenReturn(message);

		listener.onUserCreated(event);

		verify(emailSender).send(message);
	}

	@Test
	void falhaDeEnvioNaoDevePropagarParaQuemPublicouOEvento() {
		UserCreatedNotificationEvent event = new UserCreatedNotificationEvent(
				UUID.randomUUID(),
				"Maria",
				"maria@fadex.org.br",
				"SenhaProvisoria123"
		);
		EmailMessage message = new EmailMessage("maria@fadex.org.br", "Assunto", "Texto");

		when(emailComposer.compose(event)).thenReturn(message);
		doThrow(new EmailDeliveryException("falha", new RuntimeException()))
				.when(emailSender).send(any(EmailMessage.class));

		assertThatCode(() -> listener.onUserCreated(event)).doesNotThrowAnyException();
	}

	@Test
	void deveEnviarEmailQuandoJobDeIaFalhar() {
		NotificationMessage message = NotificationMessage.of(
				NotificationEventName.JOB_IA_FALHOU,
				"timeout ao chamar o modelo local",
				new NotificationAudience.Roles(Set.of(Role.ADMIN))
		);
		EmailMessage email = new EmailMessage("ana@fadex.org.br", "Job de IA falhou", "Texto");

		when(emailComposer.composeAiJobFailure("timeout ao chamar o modelo local"))
				.thenReturn(List.of(email));

		listener.onNotificationMessage(message);

		verify(emailSender).send(email);
	}

	@Test
	void naoDeveEnviarEmailParaOutrosEventosSse() {
		NotificationMessage message = NotificationMessage.of(
				NotificationEventName.CHAMADO_ATUALIZADO,
				"payload",
				new NotificationAudience.Roles(Set.of(Role.ADMIN))
		);

		listener.onNotificationMessage(message);

		verifyNoInteractions(emailComposer);
		verify(emailSender, never()).send(any(EmailMessage.class));
	}
}
