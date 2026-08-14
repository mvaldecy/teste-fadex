package br.org.fadex.helpdesk.ai.client;

import br.org.fadex.helpdesk.ai.AiIntegrationException;
import br.org.fadex.helpdesk.ai.model.TicketClassification;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LocalAiTriageClient implements AiTriageClient {

	private static final String SYSTEM_PROMPT = "Classifique o chamado e responda apenas um objeto JSON com category, priority, confidence e justification. category e priority devem usar os valores dos enums fornecidos.";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final String classificationModel;

	public LocalAiTriageClient(
			ObjectMapper objectMapper,
			@Value("${app.ai.base-url}") String baseUrl,
			@Value("${app.ai.classification-model}") String classificationModel,
			@Value("${app.ai.worker.request-timeout-seconds}") int requestTimeoutSeconds
	) {
		this.restClient = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory(requestTimeoutSeconds))
				.build();
		this.objectMapper = objectMapper;
		this.classificationModel = classificationModel;
	}

	private ClientHttpRequestFactory requestFactory(int requestTimeoutSeconds) {
		Duration timeout = Duration.ofSeconds(requestTimeoutSeconds);
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(timeout);
		requestFactory.setReadTimeout(timeout);
		return requestFactory;
	}

	@Override
	public TicketClassification classify(String title, String description) {
		try {
			String response = restClient.post()
					.uri("/api/chat")
					.body(Map.of(
							"model", classificationModel,
							"stream", false,
							"format", "json",
							"options", Map.of("temperature", 0),
							"messages", List.of(
									Map.of("role", "system", "content", SYSTEM_PROMPT),
									Map.of("role", "user", "content", formatTicket(title, description))
							)
					))
					.retrieve()
					.body(String.class);

			return parseClassification(response);
		} catch (RestClientException exception) {
			throw new AiIntegrationException("Falha ao classificar chamado no Ollama", exception);
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new AiIntegrationException("Resposta de classificacao do Ollama e invalida", exception);
		}
	}

	private String formatTicket(String title, String description) {
		return "Titulo: " + (title == null ? "" : title) + "\n\nDescricao: " + (description == null ? "" : description);
	}

	private TicketClassification parseClassification(String response) {
		if (response == null || response.isBlank()) {
			throw new IllegalArgumentException("Resposta do Ollama esta em branco");
		}

		JsonNode content = objectMapper.readTree(response).path("message").path("content");
		if (!content.isString() || content.asString().isBlank()) {
			throw new IllegalArgumentException("Conteudo da resposta do Ollama esta em branco");
		}

		JsonNode classification = objectMapper.readTree(content.asString());
		JsonNode confidence = classification.path("confidence");
		if (!confidence.isNumber() || !Double.isFinite(confidence.doubleValue())) {
			throw new IllegalArgumentException("Confidence da resposta do Ollama e invalida");
		}

		return new TicketClassification(
				TicketCategory.valueOf(requiredText(classification, "category")),
				TicketPriority.valueOf(requiredText(classification, "priority")),
				confidence.doubleValue(),
				requiredText(classification, "justification")
		);
	}

	private String requiredText(JsonNode node, String fieldName) {
		JsonNode field = node.path(fieldName);
		if (!field.isString() || field.asString().isBlank()) {
			throw new IllegalArgumentException(fieldName + " e obrigatorio na resposta do Ollama");
		}
		return field.asString();
	}
}
