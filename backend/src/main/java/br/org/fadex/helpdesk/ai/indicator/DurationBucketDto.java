package br.org.fadex.helpdesk.ai.indicator;

/**
 * Faixa de duracao do histograma, em horas.
 *
 * Intervalo fechado no inicio e aberto no fim: uma duracao de exatamente 4h cai na faixa 4–8, nunca
 * na 0–4. {@code toHours} nulo marca a ultima faixa, que nao tem teto — e onde a cauda longa se
 * acumula, em vez de esticar o eixo do grafico ate o chamado mais demorado da base.
 */
public record DurationBucketDto(
		int fromHours,
		Integer toHours,
		long count
) {
}
