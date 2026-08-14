package br.org.fadex.helpdesk.ai.client;

import br.org.fadex.helpdesk.ai.AiIntegrationException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class LocalAiClientsTimeoutTest {

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	@Test
	void deveTraduzirTimeoutDoOllamaParaExcecaoDeIntegracao() throws IOException {
		HttpServer server = createSlowServer();
		try {
			LocalAiTriageClient client = new LocalAiTriageClient(
					objectMapper,
					baseUrl(server),
					"llama3.2:1b",
					1
			);

			assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
					assertThatThrownBy(() -> client.classify("Titulo", "Descricao"))
							.isInstanceOf(AiIntegrationException.class)
			);
		} finally {
			server.stop(0);
		}
	}

	@Test
	void deveTraduzirTimeoutDeEmbeddingParaExcecaoDeIntegracao() throws IOException {
		HttpServer server = createSlowServer();
		try {
			LocalAiEmbeddingClient client = new LocalAiEmbeddingClient(objectMapper, baseUrl(server), "all-minilm", 1);

			assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
					assertThatThrownBy(() -> client.embed("Titulo\n\nDescricao"))
							.isInstanceOf(AiIntegrationException.class)
			);
		} finally {
			server.stop(0);
		}
	}

	private HttpServer createSlowServer() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/api/chat", exchange -> delayResponse());
		server.createContext("/api/embed", exchange -> delayResponse());
		server.start();
		return server;
	}

	private void delayResponse() throws IOException {
		try {
			Thread.sleep(Duration.ofSeconds(5));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private String baseUrl(HttpServer server) {
		return "http://localhost:" + server.getAddress().getPort();
	}
}
