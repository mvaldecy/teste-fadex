package br.org.fadex.helpdesk.ai.client;

import br.org.fadex.helpdesk.ai.AiIntegrationException;
import br.org.fadex.helpdesk.ai.model.TicketEmbedding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LocalAiEmbeddingClient implements AiEmbeddingClient {

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final String embeddingModel;

	public LocalAiEmbeddingClient(
			ObjectMapper objectMapper,
			@Value("${app.ai.base-url}") String baseUrl,
			@Value("${app.ai.embedding-model}") String embeddingModel,
			@Value("${app.ai.worker.request-timeout-seconds}") int requestTimeoutSeconds
	) {
		this.restClient = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory(requestTimeoutSeconds))
				.build();
		this.objectMapper = objectMapper;
		this.embeddingModel = embeddingModel;
	}

	private ClientHttpRequestFactory requestFactory(int requestTimeoutSeconds) {
		Duration timeout = Duration.ofSeconds(requestTimeoutSeconds);
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(timeout);
		requestFactory.setReadTimeout(timeout);
		return requestFactory;
	}

	@Override
	public TicketEmbedding embed(String text) {
		try {
			String response = restClient.post()
					.uri("/api/embed")
					.body(Map.of("model", embeddingModel, "input", text))
					.retrieve()
					.body(String.class);

			return parseEmbedding(response);
		} catch (RestClientException exception) {
			throw new AiIntegrationException("Falha ao gerar embedding no Ollama", exception);
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new AiIntegrationException("Resposta de embedding do Ollama e invalida", exception);
		}
	}

	private TicketEmbedding parseEmbedding(String response) {
		if (response == null || response.isBlank()) {
			throw new IllegalArgumentException("Resposta do Ollama esta em branco");
		}

		JsonNode embeddings = objectMapper.readTree(response).path("embeddings");
		if (!embeddings.isArray() || embeddings.isEmpty() || !embeddings.get(0).isArray()) {
			throw new IllegalArgumentException("Embeddings da resposta do Ollama sao invalidos");
		}

		List<Double> values = new ArrayList<>();
		for (JsonNode value : embeddings.get(0)) {
			if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
				throw new IllegalArgumentException("Embedding contem valor invalido");
			}
			values.add(value.doubleValue());
		}
		return new TicketEmbedding(values, embeddingModel);
	}
}
