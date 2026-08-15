package br.org.fadex.helpdesk.ai.indicator;

/**
 * Estado da fila de jobs de IA.
 *
 * {@code averageQueueToDoneSeconds} mede {@code updatedAt - createdAt} dos jobs concluidos: fila
 * mais execucao. {@code AiJob} nao registra o instante de inicio do processamento, entao o nome
 * declara a mistura em vez de chamar isso de tempo de processamento.
 */
public record JobQueueIndicatorsDto(
		long pending,
		long processing,
		long failed,
		long done,
		Double averageQueueToDoneSeconds
) {
}
