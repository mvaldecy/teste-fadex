package br.org.fadex.helpdesk.notification;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.ticket.TicketMinDto;
import br.org.fadex.helpdesk.model.user.UserMinDto;
import br.org.fadex.helpdesk.notification.event.NotificationRecipient;
import br.org.fadex.helpdesk.notification.event.TicketNotificationEvent;
import br.org.fadex.helpdesk.notification.event.TicketNotificationType;
import br.org.fadex.helpdesk.sse.model.NotificationAudience;
import br.org.fadex.helpdesk.sse.model.NotificationEventName;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import br.org.fadex.helpdesk.sse.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketSseNotificationListenerTest {

	private static final UUID SOLICITANTE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID RESPONSAVEL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Mock
	private NotificationService notificationService;

	@InjectMocks
	private TicketSseNotificationListener listener;

	@Test
	void criacaoDeveAlcancarSolicitanteEAdmins() {
		List<NotificationMessage> messages = listener.toMessages(
				event(TicketNotificationType.CHAMADO_CRIADO, TicketPriority.MEDIA, null, null)
		);

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst().eventName()).isEqualTo(NotificationEventName.CHAMADO_ATUALIZADO);
		assertThat(messages.getFirst().audience())
				.isEqualTo(new NotificationAudience.UsersAndRoles(Set.of(SOLICITANTE_ID), Set.of(Role.ADMIN)));
	}

	@Test
	void mutacaoDeveAlcancarSolicitanteResponsavelEAdmins() {
		List<NotificationMessage> messages = listener.toMessages(event(
				TicketNotificationType.STATUS_ALTERADO,
				TicketPriority.MEDIA,
				new NotificationRecipient(RESPONSAVEL_ID, "Ana Admin", "ana@fadex.org.br"),
				TicketPriority.MEDIA
		));

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst().audience())
				.isEqualTo(new NotificationAudience.UsersAndRoles(
						Set.of(SOLICITANTE_ID, RESPONSAVEL_ID), Set.of(Role.ADMIN)));
	}

	@Test
	void chamadoNascidoAltaDeveGerarAlertaParaAdmins() {
		List<NotificationMessage> messages = listener.toMessages(
				event(TicketNotificationType.CHAMADO_CRIADO, TicketPriority.ALTA, null, null)
		);

		assertThat(messages).hasSize(2);
		assertThat(messages).extracting(NotificationMessage::eventName)
				.containsExactly(
						NotificationEventName.CHAMADO_ATUALIZADO,
						NotificationEventName.CHAMADO_ALTA_PRIORIDADE
				);
		assertThat(messages.get(1).audience())
				.isEqualTo(new NotificationAudience.Roles(Set.of(Role.ADMIN)));
	}

	@Test
	void chamadoQueJaEraAltaNaoDeveRepetirAlerta() {
		List<NotificationMessage> messages = listener.toMessages(event(
				TicketNotificationType.CLASSIFICACAO_ATUALIZADA,
				TicketPriority.ALTA,
				null,
				TicketPriority.ALTA
		));

		assertThat(messages).hasSize(1);
	}

	@Test
	void deveDespacharTodasAsMensagensDoEvento() {
		listener.onTicketNotification(
				event(TicketNotificationType.CHAMADO_CRIADO, TicketPriority.ALTA, null, null)
		);

		verify(notificationService, times(2)).dispatch(any(NotificationMessage.class));
	}

	@Test
	void falhaNoDespachoNaoDevePropagar() {
		doThrow(new IllegalStateException("emitter fechado"))
				.when(notificationService).dispatch(any(NotificationMessage.class));

		assertThatCode(() -> listener.onTicketNotification(
				event(TicketNotificationType.STATUS_ALTERADO, TicketPriority.MEDIA, null, TicketPriority.MEDIA)
		)).doesNotThrowAnyException();
	}

	private TicketNotificationEvent event(
			TicketNotificationType type,
			TicketPriority priority,
			NotificationRecipient assignee,
			TicketPriority previousPriority
	) {
		TicketMinDto ticket = new TicketMinDto(
				UUID.fromString("44444444-4444-4444-4444-444444444444"),
				"Erro ao acessar sistema",
				TicketCategory.SISTEMAS,
				priority,
				TicketStatus.ABERTO,
				ClassificationOrigin.PENDENTE,
				new UserMinDto(SOLICITANTE_ID, "Maria Solicitante"),
				assignee == null ? null : new UserMinDto(assignee.id(), assignee.name()),
				null,
				LocalDateTime.now()
		,
				null);

		return new TicketNotificationEvent(
				type,
				ticket,
				new NotificationRecipient(SOLICITANTE_ID, "Maria Solicitante", "maria@fadex.org.br"),
				assignee,
				SOLICITANTE_ID,
				previousPriority,
				"detalhe"
		);
	}
}
