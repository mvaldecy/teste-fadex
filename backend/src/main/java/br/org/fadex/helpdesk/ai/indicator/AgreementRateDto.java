package br.org.fadex.helpdesk.ai.indicator;

/**
 * Taxa de concordancia entre o ADMIN e a IA.
 *
 * {@code evaluated} conta apenas chamados revisados pelo ADMIN que tinham sugestao registrada.
 * Chamado que a IA classificou e ninguem revisou fica fora: conta-lo como aceite inflaria a taxa
 * para perto de 100% e o numero deixaria de medir o que diz medir.
 */
public record AgreementRateDto(long evaluated, long agreed, Double percentage) {
}
