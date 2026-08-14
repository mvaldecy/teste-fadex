package br.org.fadex.helpdesk.ai.duplicate;

import java.util.UUID;

public record DuplicateCandidate(UUID ticketId, String embedding) {
}
