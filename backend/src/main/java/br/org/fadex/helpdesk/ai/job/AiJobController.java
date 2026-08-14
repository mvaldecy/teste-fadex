package br.org.fadex.helpdesk.ai.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Operacao da fila de IA pelo ADMIN.
 *
 * {@code @EnableMethodSecurity} ja esta ligado no {@code SecurityConfig} e {@code /api/v1/**} cai em
 * {@code anyRequest().authenticated()}, entao o {@code @PreAuthorize} na classe basta.
 */
@RestController
@RequestMapping("/api/v1/ai/jobs")
@PreAuthorize("hasRole('ADMIN')")
public class AiJobController {

	private final AiJobService aiJobService;

	public AiJobController(AiJobService aiJobService) {
		this.aiJobService = aiJobService;
	}

	@GetMapping
	public ResponseEntity<Page<AiJobDto>> findAll(
			@ModelAttribute AiJobFilter filter,
			@PageableDefault(size = 10, sort = AiJobFields.CREATED_AT, direction = Sort.Direction.DESC) Pageable pageable
	) {
		Page<AiJobDto> jobs = aiJobService.findAll(filter, pageable);

		return ResponseEntity.ok(jobs);
	}

	@PostMapping("/{id}/retry")
	public ResponseEntity<AiJobDto> retry(@PathVariable UUID id) {
		AiJobDto job = aiJobService.retry(id);

		return ResponseEntity.ok(job);
	}
}
