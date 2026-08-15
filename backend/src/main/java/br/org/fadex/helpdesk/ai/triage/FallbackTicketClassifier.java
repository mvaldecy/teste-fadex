package br.org.fadex.helpdesk.ai.triage;

import br.org.fadex.helpdesk.ai.model.TicketClassification;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class FallbackTicketClassifier {

	public TicketClassification classify(String title, String description) {
		String normalizedText = normalize(title, description);
		return new TicketClassification(
				resolveCategory(normalizedText),
				resolvePriority(normalizedText),
				0.6,
				"Classificacao por fallback deterministico baseado em palavras-chave."
		);
	}

	/**
	 * Alem de minusculas, remove os acentos.
	 *
	 * Metade das palavras-chave abaixo esta escrita sem acento — {@code ferias},
	 * {@code indisponivel}, {@code duvida}, {@code orientacao}, {@code aplicacao} — e o
	 * {@code contains} e literal. Sem esta normalizacao, quem escrevesse "ferias" do jeito
	 * certo, com acento, nao casava com nada e o chamado caia em OUTROS/MEDIA. O defeito
	 * passou despercebido porque os chamados semeados tambem estao sem acento: o seed
	 * mascarava o caso real, que e uma pessoa digitando portugues normalmente.
	 */
	private String normalize(String title, String description) {
		String text = ((title == null ? "" : title) + " " + (description == null ? "" : description))
				.toLowerCase(Locale.ROOT);
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	private TicketCategory resolveCategory(String normalizedText) {
		if (containsAny(normalizedText, "senha", "login", "acesso", "bloqueado")) {
			return TicketCategory.ACESSO;
		}
		if (containsAny(normalizedText, "sistema", "erro", "aplicacao", "interno")) {
			return TicketCategory.SISTEMAS;
		}
		if (containsAny(normalizedText, "rede", "internet", "servidor", "infra")) {
			return TicketCategory.INFRAESTRUTURA;
		}
		if (containsAny(normalizedText, "computador", "impressora", "teclado", "mouse")) {
			return TicketCategory.EQUIPAMENTOS;
		}
		if (containsAny(normalizedText, "financeiro", "pagamento", "nota fiscal", "boleto")) {
			return TicketCategory.FINANCEIRO;
		}
		if (containsAny(normalizedText, "rh", "ferias", "folha", "beneficio")) {
			return TicketCategory.RH;
		}
		return TicketCategory.OUTROS;
	}

	private TicketPriority resolvePriority(String normalizedText) {
		if (containsAny(normalizedText, "urgente", "indisponivel", "parado", "bloqueado", "nao consegue acessar")) {
			return TicketPriority.ALTA;
		}
		if (containsAny(normalizedText, "duvida", "orientacao", "quando possivel")) {
			return TicketPriority.BAIXA;
		}
		return TicketPriority.MEDIA;
	}

	private boolean containsAny(String text, String... keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return true;
			}
		}
		return false;
	}
}
