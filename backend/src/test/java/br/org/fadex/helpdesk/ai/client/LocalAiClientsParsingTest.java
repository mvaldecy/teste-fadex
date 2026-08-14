package br.org.fadex.helpdesk.ai.client;

import br.org.fadex.helpdesk.ai.AiIntegrationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAiClientsParsingTest {

	private static final String INVALID_JSON = "{\"message\": ";

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	@Test
	void deveTraduzirRespostaMalformadaDeClassificacaoParaExcecaoDeIntegracao() throws IOException {
		HttpServer server = createServer();
		try {
			LocalAiTriageClient client = new LocalAiTriageClient(
					objectMapper,
					baseUrl(server),
					"llama3.2:1b",
					1
			);

			assertThatThrownBy(() -> client.classify("Titulo", "Descricao"))
					.isInstanceOf(AiIntegrationException.class);
		} finally {
			server.stop(0);
		}
	}

	@Test
	void deveTraduzirRespostaMalformadaDeEmbeddingParaExcecaoDeIntegracao() throws IOException {
		HttpServer server = createServer();
		try {
			LocalAiEmbeddingClient client = new LocalAiEmbeddingClient(objectMapper, baseUrl(server), "all-minilm", 1);

			assertThatThrownBy(() -> client.embed("Titulo\n\nDescricao"))
					.isInstanceOf(AiIntegrationException.class);
		} finally {
			server.stop(0);
		}
	}

	private HttpServer createServer() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/api/chat", LocalAiClientsParsingTest::respondInvalidJson);
		server.createContext("/api/embed", LocalAiClientsParsingTest::respondInvalidJson);
		server.start();
		return server;
	}

	private String baseUrl(HttpServer server) {
		return "http://localhost:" + server.getAddress().getPort();
	}

	private static void respondInvalidJson(HttpExchange exchange) throws IOException {
		byte[] body = INVALID_JSON.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, body.length);
		try (OutputStream output = exchange.getResponseBody()) {
			output.write(body);
		}
	}
}
