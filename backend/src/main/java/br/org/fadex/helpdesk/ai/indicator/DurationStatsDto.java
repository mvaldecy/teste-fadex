package br.org.fadex.helpdesk.ai.indicator;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Estatistica de uma amostra de duracoes.
 *
 * O histograma so acompanha o {@code overall} de cada bloco. Nos recortes por prioridade e por
 * categoria a chave e omitida — nao vem nula nem vazia —, porque nesses recortes a amostra e pequena
 * demais para uma distribuicao dizer alguma coisa, e omitir e a unica forma de o front distinguir
 * "aqui nao ha histograma" de "histograma sem nenhum chamado".
 */
public record DurationStatsDto(
		int sampleSize,
		Double averageHours,
		Double medianHours,
		Double p90Hours,
		@JsonInclude(JsonInclude.Include.NON_NULL)
		List<DurationBucketDto> histogram
) {

	public static DurationStatsDto empty() {
		return new DurationStatsDto(0, null, null, null, null);
	}
}
