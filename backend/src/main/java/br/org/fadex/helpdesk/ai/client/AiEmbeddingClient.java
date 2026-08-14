package br.org.fadex.helpdesk.ai.client;

import br.org.fadex.helpdesk.ai.model.TicketEmbedding;

public interface AiEmbeddingClient {

	TicketEmbedding embed(String text);
}
