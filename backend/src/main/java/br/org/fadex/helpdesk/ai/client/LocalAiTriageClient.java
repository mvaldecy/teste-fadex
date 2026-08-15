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

import java.text.Normalizer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LocalAiTriageClient implements AiTriageClient {

	/**
	 * O prompt lista os valores aceitos porque o modelo nao tem como adivinha-los.
	 *
	 * Sem essa lista, o modelo respondia rotulos livres — "Impressora", "CRITICAL" — e o
	 * {@code valueOf} do parse estourava, derrubando toda classificacao para o fallback heuristico
	 * em silencio. Os valores sao derivados dos proprios enums: enum novo entra no prompt sozinho,
	 * em vez de precisar de alguem lembrar de atualizar um texto.
	 */
	private static final String SYSTEM_PROMPT = buildSystemPrompt();

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

	private static String buildSystemPrompt() {
		return "Voce classifica chamados de um helpdesk interno. Responda apenas um objeto JSON com "
				+ "as chaves category, priority, confidence e justification.\n"
				+ "category deve ser exatamente um destes valores: " + values(TicketCategory.values()) + ".\n"
				+ "priority deve ser exatamente um destes valores: " + values(TicketPriority.values()) + ".\n"
				+ "confidence e um numero entre 0 e 1 indicando o quanto voce esta seguro.\n"
				+ "justification e uma frase curta em portugues explicando a escolha.";
	}

	private static String values(Enum<?>[] values) {
		return Arrays.stream(values).map(Enum::name).collect(Collectors.joining(", "));
	}

	static String systemPrompt() {
		return SYSTEM_PROMPT;
	}

	/**
	 * Converte o rotulo devolvido pelo modelo no enum correspondente, tolerando variacao de caixa,
	 * espaco e acento.
	 *
	 * Modelos pequenos devolvem "Acesso", " ALTA " e "MEDIA" com acento mesmo com os valores
	 * listados no prompt. Rejeitar por diferenca de forma jogaria fora uma classificacao correta.
	 * O que continua sendo rejeitado e valor inexistente — "ACCESSO" nao vira ACESSO por adivinhacao.
	 */
	private <E extends Enum<E>> E toEnum(Class<E> type, String value) {
		String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toUpperCase(Locale.ROOT);

		return Arrays.stream(type.getEnumConstants())
				.filter(constant -> constant.name().equals(normalized))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"Valor de " + type.getSimpleName() + " desconhecido na resposta do Ollama: " + value
				));
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
				toEnum(TicketCategory.class, requiredText(classification, "category")),
				toEnum(TicketPriority.class, requiredText(classification, "priority")),
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
