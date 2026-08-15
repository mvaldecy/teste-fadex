package br.org.fadex.helpdesk.ai.job;

import br.org.fadex.helpdesk.exception.ConflictException;
import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AiJobService {

	private static final List<AiJobStatus> ACTIVE_STATUSES =
			List.of(AiJobStatus.PENDING, AiJobStatus.PROCESSING);

	private final AiJobRepository aiJobRepository;

	public AiJobService(AiJobRepository aiJobRepository) {
		this.aiJobRepository = aiJobRepository;
	}

	@Transactional
	public void enqueueTicketJobs(Ticket ticket) {
		LocalDateTime now = LocalDateTime.now();
		AiJob classificationJob = new AiJob(ticket.getId(), AiJobType.CLASSIFICATION, now);
		AiJob embeddingJob = new AiJob(ticket.getId(), AiJobType.EMBEDDING, now);

		aiJobRepository.save(classificationJob);
		aiJobRepository.save(embeddingJob);
	}

	/**
	 * Reenfileira a triagem de um chamado a pedido do ADMIN.
	 *
	 * A guarda contra job duplicado e por tipo, e nao por chamado: o job de embedding e mais lento
	 * que o de classificacao, e uma guarda por chamado deixaria um embedding ainda PENDING
	 * bloqueando a reclassificacao — justamente o que o ADMIN quer refazer. Cada tipo com job ativo
	 * e pulado; se nenhum tipo puder ser enfileirado, a chamada e recusada com conflito.
	 *
	 * Ativo e PENDING ou PROCESSING. FAILED tem o proprio caminho de retry, e DONE e exatamente o
	 * caso em que faz sentido reprocessar.
	 *
	 * Nao espera o modelo responder: grava os jobs e devolve. Quem processa e o worker do Quartz.
	 */
	@Transactional
	public List<AiJobDto> requeueTicketJobs(UUID ticketId) {
		LocalDateTime now = LocalDateTime.now();
		List<AiJobDto> requeued = new ArrayList<>();

		for (AiJobType type : AiJobType.values()) {
			boolean alreadyActive = aiJobRepository.existsByTicketIdAndTypeAndStatusIn(
					ticketId,
					type,
					ACTIVE_STATUSES
			);

			if (alreadyActive) {
				continue;
			}

			AiJob savedJob = aiJobRepository.save(new AiJob(ticketId, type, now));
			requeued.add(AiJobMapper.toResponseDto(savedJob));
		}

		if (requeued.isEmpty()) {
			throw new ConflictException("Ja existe triagem em andamento para este chamado.");
		}

		return requeued;
	}

	@Transactional(readOnly = true)
	public Page<AiJobDto> findAll(AiJobFilter filter, Pageable pageable) {
		Specification<AiJob> spec = AiJobSpecification.createSpecification(filter);
		Page<AiJob> jobs = aiJobRepository.findAll(spec, pageable);
		Page<AiJobDto> response = jobs.map(AiJobMapper::toResponseDto);

		return response;
	}

	@Transactional(readOnly = true)
	public List<AiJob> findDueJobs(LocalDateTime now, int limit) {
		PageRequest pageRequest = PageRequest.of(0, limit);
		List<AiJob> jobs = aiJobRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
				AiJobStatus.PENDING,
				now,
				pageRequest
		);

		return jobs;
	}

	@Transactional
	public AiJobDto retry(UUID id) {
		AiJob job = aiJobRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("Job de IA nao encontrado."));

		if (job.getStatus() != AiJobStatus.FAILED) {
			throw new ConflictException("Apenas jobs com falha podem ser retentados.");
		}

		job.retry(LocalDateTime.now());
		AiJob savedJob = aiJobRepository.save(job);
		AiJobDto response = AiJobMapper.toResponseDto(savedJob);

		return response;
	}
}
