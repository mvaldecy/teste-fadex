package br.org.fadex.helpdesk.ai.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiJobControllerTest {

	@Mock
	private AiJobService aiJobService;

	@InjectMocks
	private AiJobController aiJobController;

	private AiJobDto jobDto(UUID id, AiJobStatus status) {
		return new AiJobDto(
				id,
				UUID.randomUUID(),
				AiJobType.CLASSIFICATION,
				status,
				2,
				LocalDateTime.of(2026, 8, 14, 18, 5),
				"timeout ao chamar o modelo local",
				LocalDateTime.of(2026, 8, 14, 17, 0),
				LocalDateTime.of(2026, 8, 14, 18, 0)
		);
	}

	@Test
	void deveListarJobs() {
		Pageable pageable = PageRequest.of(0, 10);
		Page<AiJobDto> page = new PageImpl<>(List.of(jobDto(UUID.randomUUID(), AiJobStatus.FAILED)), pageable, 1);

		when(aiJobService.findAll(any(), any())).thenReturn(page);

		ResponseEntity<Page<AiJobDto>> response =
				aiJobController.findAll(new AiJobFilter(null, null, null), pageable);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().getTotalElements()).isEqualTo(1);
	}

	@Test
	void deveRepassarFiltroEPaginacaoParaOService() {
		Pageable pageable = PageRequest.of(1, 25);
		AiJobFilter filter = new AiJobFilter(AiJobStatus.FAILED, AiJobType.EMBEDDING, null);

		when(aiJobService.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(), pageable, 0));

		aiJobController.findAll(filter, pageable);

		verify(aiJobService).findAll(filter, pageable);
	}

	@Test
	void deveRetentarJob() {
		UUID id = UUID.randomUUID();

		when(aiJobService.retry(id)).thenReturn(jobDto(id, AiJobStatus.PENDING));

		ResponseEntity<AiJobDto> response = aiJobController.retry(id);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo(AiJobStatus.PENDING);
	}

	@Test
	void deveExigirPapelAdminNaClasse() {
		PreAuthorize preAuthorize = AiJobController.class.getAnnotation(PreAuthorize.class);

		assertThat(preAuthorize).isNotNull();
		assertThat(preAuthorize.value()).isEqualTo("hasRole('ADMIN')");
	}
}
