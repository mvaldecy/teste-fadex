package br.org.fadex.helpdesk.ai.job;

import br.org.fadex.helpdesk.exception.ConflictException;
import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AiJobService {

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
