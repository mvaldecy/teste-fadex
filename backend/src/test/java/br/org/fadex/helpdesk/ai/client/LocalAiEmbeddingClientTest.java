package br.org.fadex.helpdesk.ai.client;

import br.org.fadex.helpdesk.ai.model.TicketEmbedding;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAiEmbeddingClientTest {

	private static final String EMBEDDING_RESPONSE = "{\"embeddings\":[[0.1,0.2,0.3]]}";

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	@Test
	void deveEnviarModeloConfiguradoAoOllamaEPropagarNoResultado() throws IOException {
		AtomicReference<String> requestBody = new AtomicReference<>();
		HttpServer server = createServer(requestBody);
		try {
			LocalAiEmbeddingClient client = new LocalAiEmbeddingClient(
					objectMapper,
					baseUrl(server),
					"nomic-embed-text",
					5
			);

			TicketEmbedding embedding = client.embed("Titulo\n\nDescricao");

			assertThat(objectMapper.readTree(requestBody.get()).path("model").asString())
					.isEqualTo("nomic-embed-text");
			assertThat(embedding.model()).isEqualTo("nomic-embed-text");
			assertThat(embedding.values()).containsExactly(0.1, 0.2, 0.3);
		} finally {
			server.stop(0);
		}
	}

	private HttpServer createServer(AtomicReference<String> requestBody) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/api/embed", exchange -> respond(exchange, requestBody));
		server.start();
		return server;
	}

	private void respond(HttpExchange exchange, AtomicReference<String> requestBody) throws IOException {
		requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

		byte[] body = EMBEDDING_RESPONSE.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, body.length);
		try (OutputStream output = exchange.getResponseBody()) {
			output.write(body);
		}
	}

	private String baseUrl(HttpServer server) {
		return "http://localhost:" + server.getAddress().getPort();
	}
}
