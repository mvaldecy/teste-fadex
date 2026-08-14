package br.org.fadex.helpdesk.ai.job;

import java.time.LocalDateTime;
import java.util.UUID;

public record AiJobSummaryDto(
		UUID id,
		UUID ticketId,
		AiJobType type,
		AiJobStatus status,
		int attempts,
		LocalDateTime nextAttemptAt,
		LocalDateTime createdAt
) {
}
