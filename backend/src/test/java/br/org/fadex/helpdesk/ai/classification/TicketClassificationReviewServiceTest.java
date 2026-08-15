package br.org.fadex.helpdesk.ai.classification;

import br.org.fadex.helpdesk.exception.ForbiddenException;
import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.security.AccessControlService;
import br.org.fadex.helpdesk.service.TicketService;
import br.org.fadex.helpdesk.sse.model.NotificationEventName;
import br.org.fadex.helpdesk.sse.model.NotificationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketClassificationReviewServiceTest {

	private static final UUID TICKET_ID = UUID.randomUUID();
	private static final ZoneId ZONE = ZoneId.of("America/Fortaleza");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 18, 30);

	private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

	@Mock
	private TicketService ticketService;

	@Mock
	private AccessControlService accessControlService;

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@Test
	void deveManterOrigemIaQuandoAdminAceitaASugestao() {
		Ticket ticket = ticketComSugestao(TicketCategory.ACESSO, TicketPriority.MEDIA);
		when(ticketService.findEntityById(TICKET_ID)).thenReturn(ticket);

		service().review(TICKET_ID, dto(TicketCategory.ACESSO, TicketPriority.MEDIA, null));

		verify(ticketService).applyClassification(
				eq(TICKET_ID),
				eq(TicketCategory.ACESSO),
				eq(TicketPriority.MEDIA),
				eq(ClassificationOrigin.IA),
				any()
		);
	}

	@Test
	void deveVirarManualQuandoAdminCorrigeASugestao() {
		Ticket ticket = ticketComSugestao(TicketCategory.ACESSO, TicketPriority.MEDIA);
		when(ticketService.findEntityById(TICKET_ID)).thenReturn(ticket);

		service().review(TICKET_ID, dto(
				TicketCategory.INFRAESTRUTURA,
				TicketPriority.ALTA,
				"Afeta o predio inteiro."
		));

		verify(ticketService).applyClassification(
				TICKET_ID,
				TicketCategory.INFRAESTRUTURA,
				TicketPriority.ALTA,
				ClassificationOrigin.MANUAL,
				"Afeta o predio inteiro."
		);
	}

	@Test
	void deveVirarManualQuandoApenasAPrioridadeDivergeDaSugestao() {
		Ticket ticket = ticketComSugestao(TicketCategory.ACESSO, TicketPriority.MEDIA);
		when(ticketService.findEntityById(TICKET_ID)).thenReturn(ticket);

		service().review(TICKET_ID, dto(TicketCategory.ACESSO, TicketPriority.ALTA, null));

		verify(ticketService).applyClassification(
				eq(TICKET_ID),
				eq(TicketCategory.ACESSO),
				eq(TicketPriority.ALTA),
				eq(ClassificationOrigin.MANUAL),
				any()
		);
	}

	@Test
	void deveVirarManualQuandoNaoHaSugestaoRegistrada() {
		Ticket ticket = ticketSemSugestao();
		when(ticketService.findEntityById(TICKET_ID)).thenReturn(ticket);

		service().review(TICKET_ID, dto(TicketCategory.RH, TicketPriority.BAIXA, null));

		verify(ticketService).applyClassification(
				eq(TICKET_ID),
				eq(TicketCategory.RH),
				eq(TicketPriority.BAIXA),
				eq(ClassificationOrigin.MANUAL),
				any()
		);
	}

	@Test
	void deveCarimbarInstanteDaRevisaoNoAceite() {
		Ticket ticket = ticketComSugestao(TicketCategory.ACESSO, TicketPriority.MEDIA);
		when(ticketService.findEntityById(TICKET_ID)).thenReturn(ticket);

		service().review(TICKET_ID, dto(TicketCategory.ACESSO, TicketPriority.MEDIA, null));

		verify(ticket).markClassificationReviewed(NOW);
	}

	@Test
	void deveCarimbarInstanteDaRevisaoNaCorrecao() {
		Ticket ticket = ticketComSugestao(TicketCategory.ACESSO, TicketPriority.MEDIA);
		when(ticketService.findEntityById(TICKET_ID)).thenReturn(ticket);

		service().review(TICKET_ID, dto(TicketCategory.RH, TicketPriority.ALTA, null));

		verify(ticket).markClassificationReviewed(NOW);
	}

	@Test
	void deveNegarRevisaoParaSolicitante() {
		doThrow(new ForbiddenException("Acesso negado ao recurso solicitado."))
				.when(accessControlService).assertAdmin();

		assertThatThrownBy(() -> service().review(TICKET_ID, dto(TicketCategory.ACESSO, TicketPriority.MEDIA, null)))
				.isInstanceOf(ForbiddenException.class);

		verify(ticketService, never()).applyClassification(any(), any(), any(), any(), any());
	}

	@Test
	void devePropagarNaoEncontradoQuandoChamadoNaoExiste() {
		when(ticketService.findEntityById(TICKET_ID)).thenThrow(new NotFoundException("Chamado nao encontrado."));

		assertThatThrownBy(() -> service().review(TICKET_ID, dto(TicketCategory.ACESSO, TicketPriority.MEDIA, null)))
				.isInstanceOf(NotFoundException.class);

		verify(ticketService, never()).applyClassification(any(), any(), any(), any(), any());
	}

	@Test
	void devePublicarIndicadoresAtualizados() {
		Ticket ticket = ticketComSugestao(TicketCategory.ACESSO, TicketPriority.MEDIA);
		when(ticketService.findEntityById(TICKET_ID)).thenReturn(ticket);

		service().review(TICKET_ID, dto(TicketCategory.ACESSO, TicketPriority.MEDIA, null));

		ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
		verify(applicationEventPublisher, atLeastOnce()).publishEvent(captor.capture());

		assertThat(captor.getAllValues())
				.extracting(NotificationMessage::eventName)
				.contains(NotificationEventName.INDICADORES_ATUALIZADOS);
	}

	private TicketClassificationReviewService service() {
		return new TicketClassificationReviewService(
				ticketService,
				accessControlService,
				applicationEventPublisher,
				clock
		);
	}

	private TicketClassificationUpdateDto dto(
			TicketCategory category,
			TicketPriority priority,
			String justification
	) {
		return new TicketClassificationUpdateDto(category, priority, justification);
	}

	private Ticket ticketComSugestao(TicketCategory category, TicketPriority priority) {
		Ticket ticket = mock(Ticket.class);
		when(ticket.getAiSuggestedCategory()).thenReturn(category);
		when(ticket.getAiSuggestedPriority()).thenReturn(priority);

		return ticket;
	}

	private Ticket ticketSemSugestao() {
		Ticket ticket = mock(Ticket.class);
		when(ticket.getAiSuggestedCategory()).thenReturn(null);

		return ticket;
	}
}
