package br.org.fadex.helpdesk.ai.classification;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.ticket.TicketDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketClassificationControllerTest {

	@Mock
	private TicketClassificationReviewService ticketClassificationReviewService;

	@InjectMocks
	private TicketClassificationController ticketClassificationController;

	@Test
	void deveDevolverChamadoRevisado() {
		UUID id = UUID.randomUUID();
		TicketClassificationUpdateDto request = new TicketClassificationUpdateDto(
				TicketCategory.INFRAESTRUTURA,
				TicketPriority.ALTA,
				"Impacto em rede."
		);
		TicketDto ticket = ticketDto(id);

		when(ticketClassificationReviewService.review(id, request)).thenReturn(ticket);

		ResponseEntity<TicketDto> response = ticketClassificationController.review(id, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(ticket);
		verify(ticketClassificationReviewService).review(id, request);
	}

	@Test
	void deveExigirPapelAdminNoEndpoint() throws NoSuchMethodException {
		Method method = TicketClassificationController.class.getMethod(
				"review",
				UUID.class,
				TicketClassificationUpdateDto.class
		);
		PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

		assertThat(preAuthorize).isNotNull();
		assertThat(preAuthorize.value()).isEqualTo("hasRole('ADMIN')");
	}

	private TicketDto ticketDto(UUID id) {
		return new TicketDto(
				id,
				"Sem acesso a rede",
				"Predio inteiro sem conexao.",
				TicketCategory.INFRAESTRUTURA,
				TicketPriority.ALTA,
				TicketStatus.ABERTO,
				ClassificationOrigin.MANUAL,
				"Impacto em rede.",
				null,
				null,
				null,
				null,
				null,
				null,
				LocalDateTime.of(2026, 8, 14, 10, 0),
				LocalDateTime.of(2026, 8, 14, 11, 0),
				TicketCategory.INFRAESTRUTURA,
				TicketPriority.ALTA,
				0.87,
				null
		);
	}
}
