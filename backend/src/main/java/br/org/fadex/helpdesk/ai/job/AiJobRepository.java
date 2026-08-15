package br.org.fadex.helpdesk.ai.job;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AiJobRepository extends JpaRepository<AiJob, UUID>, JpaSpecificationExecutor<AiJob> {

	List<AiJob> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
			AiJobStatus status,
			LocalDateTime nextAttemptAt,
			Pageable pageable
	);

	long countByStatus(AiJobStatus status);

	List<AiJob> findByStatus(AiJobStatus status);

	boolean existsByTicketIdAndTypeAndStatusIn(UUID ticketId, AiJobType type, Collection<AiJobStatus> statuses);
}
