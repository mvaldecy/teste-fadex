package br.org.fadex.helpdesk.ai.indicator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndicatorControllerTest {

	@Mock
	private IndicatorService indicatorService;

	@InjectMocks
	private IndicatorController indicatorController;

	@Test
	void deveDevolverIndicadores() {
		IndicatorsDto indicators = new IndicatorsDto(
				LocalDateTime.of(2026, 8, 14, 18, 0),
				null,
				null,
				null,
				null
		);

		when(indicatorService.getIndicators()).thenReturn(indicators);

		ResponseEntity<IndicatorsDto> response = indicatorController.getIndicators();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(indicators);
	}

	@Test
	void deveExigirPapelAdminNaClasse() {
		PreAuthorize preAuthorize = IndicatorController.class.getAnnotation(PreAuthorize.class);

		assertThat(preAuthorize).isNotNull();
		assertThat(preAuthorize.value()).isEqualTo("hasRole('ADMIN')");
	}
}
