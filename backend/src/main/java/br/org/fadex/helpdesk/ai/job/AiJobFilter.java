package br.org.fadex.helpdesk.ai.job;

import java.util.UUID;

public record AiJobFilter(AiJobStatus status, AiJobType type, UUID ticketId) {

	public boolean hasStatus() {
		return status != null;
	}

	public boolean hasType() {
		return type != null;
	}

	public boolean hasTicketId() {
		return ticketId != null;
	}
}
