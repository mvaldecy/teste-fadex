package br.org.fadex.helpdesk.ai.triage;

import br.org.fadex.helpdesk.ai.job.AiJobDto;
import br.org.fadex.helpdesk.ai.job.AiJobStatus;
import br.org.fadex.helpdesk.ai.job.AiJobType;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketTriageControllerTest {

	@Mock
	private TicketTriageService ticketTriageService;

	@InjectMocks
	private TicketTriageController ticketTriageController;

	@Test
	void deveResponderAceitoComOsJobsEnfileirados() {
		UUID id = UUID.randomUUID();
		List<AiJobDto> jobs = List.of(job(id, AiJobType.CLASSIFICATION), job(id, AiJobType.EMBEDDING));

		when(ticketTriageService.requestTriage(id)).thenReturn(jobs);

		ResponseEntity<List<AiJobDto>> response = ticketTriageController.requestTriage(id);

		// 202, nao 200: o worker ainda vai processar. Devolver 200 sugeriria classificacao pronta.
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(response.getBody()).isSameAs(jobs);
	}

	@Test
	void deveExigirPapelAdminNoEndpoint() throws NoSuchMethodException {
		Method method = TicketTriageController.class.getMethod("requestTriage", UUID.class);
		PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

		assertThat(preAuthorize).isNotNull();
		assertThat(preAuthorize.value()).isEqualTo("hasRole('ADMIN')");
	}

	private AiJobDto job(UUID ticketId, AiJobType type) {
		return new AiJobDto(
				UUID.randomUUID(),
				ticketId,
				type,
				AiJobStatus.PENDING,
				0,
				LocalDateTime.of(2026, 8, 14, 18, 0),
				null,
				LocalDateTime.of(2026, 8, 14, 18, 0),
				LocalDateTime.of(2026, 8, 14, 18, 0)
		);
	}
}
