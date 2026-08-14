package br.org.fadex.helpdesk.ai.model;

import java.util.List;
import java.util.stream.Collectors;

public record TicketEmbedding(List<Double> values, String model) {

	public TicketEmbedding {
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("values nao pode estar vazia");
		}
		values = List.copyOf(values);
		if (values.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
			throw new IllegalArgumentException("values deve conter somente numeros finitos");
		}
		if (model == null || model.isBlank()) {
			throw new IllegalArgumentException("model nao pode estar em branco");
		}
	}

	public String toPgVectorLiteral() {
		return values.stream()
				.map(String::valueOf)
				.collect(Collectors.joining(",", "[", "]"));
	}
}
