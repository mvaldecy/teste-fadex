package br.org.fadex.helpdesk.ai.duplicate;

import java.util.Arrays;
import java.util.List;

/**
 * Parse do literal pgvector e similaridade de cosseno.
 *
 * O calculo fica em Java, e nao no operador {@code <=>} do pgvector, porque o profile de teste mapeia
 * a coluna de embedding para {@code varchar} no H2 e desliga o indice vetorial — um {@code order by
 * embedding <=> :vetor} nao rodaria em teste e a deteccao ficaria sem cobertura (decisao D8).
 */
public abstract class EmbeddingSimilarity {

	private EmbeddingSimilarity() {
	}

	public static List<Double> parse(String literal) {
		if (literal == null || literal.isBlank()) {
			return List.of();
		}

		String content = literal.trim().replace("[", "").replace("]", "").trim();
		if (content.isBlank()) {
			return List.of();
		}

		return Arrays.stream(content.split(","))
				.map(String::trim)
				.map(Double::valueOf)
				.toList();
	}

	public static double cosine(List<Double> left, List<Double> right) {
		if (left.size() != right.size()) {
			throw new IllegalArgumentException("Vetores devem ter o mesmo tamanho.");
		}

		double dotProduct = 0.0;
		double leftNorm = 0.0;
		double rightNorm = 0.0;

		for (int index = 0; index < left.size(); index++) {
			double leftValue = left.get(index);
			double rightValue = right.get(index);
			dotProduct += leftValue * rightValue;
			leftNorm += leftValue * leftValue;
			rightNorm += rightValue * rightValue;
		}

		if (leftNorm == 0.0 || rightNorm == 0.0) {
			return 0.0;
		}

		return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
	}
}
