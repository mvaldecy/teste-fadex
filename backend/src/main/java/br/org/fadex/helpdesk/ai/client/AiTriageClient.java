package br.org.fadex.helpdesk.ai.client;

import br.org.fadex.helpdesk.ai.model.TicketClassification;

public interface AiTriageClient {

	TicketClassification classify(String title, String description);
}
