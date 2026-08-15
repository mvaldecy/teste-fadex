package br.org.fadex.helpdesk.ai.indicator;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/indicators")
@PreAuthorize("hasRole('ADMIN')")
public class IndicatorController {

	private final IndicatorService indicatorService;

	public IndicatorController(IndicatorService indicatorService) {
		this.indicatorService = indicatorService;
	}

	@GetMapping
	public ResponseEntity<IndicatorsDto> getIndicators() {
		IndicatorsDto indicators = indicatorService.getIndicators();

		return ResponseEntity.ok(indicators);
	}
}
