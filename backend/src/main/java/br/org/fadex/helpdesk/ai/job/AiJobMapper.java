package br.org.fadex.helpdesk.ai.job;

public abstract class AiJobMapper {

	private AiJobMapper() {
	}

	public static AiJobDto toResponseDto(AiJob aiJob) {
		return new AiJobDto(
				aiJob.getId(),
				aiJob.getTicketId(),
				aiJob.getType(),
				aiJob.getStatus(),
				aiJob.getAttempts(),
				aiJob.getNextAttemptAt(),
				aiJob.getLastError(),
				aiJob.getCreatedAt(),
				aiJob.getUpdatedAt()
		);
	}

	public static AiJobSummaryDto toSummaryDto(AiJob aiJob) {
		return new AiJobSummaryDto(
				aiJob.getId(),
				aiJob.getTicketId(),
				aiJob.getType(),
				aiJob.getStatus(),
				aiJob.getAttempts(),
				aiJob.getNextAttemptAt(),
				aiJob.getCreatedAt()
		);
	}
}
