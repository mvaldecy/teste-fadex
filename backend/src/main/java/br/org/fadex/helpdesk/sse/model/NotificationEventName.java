package br.org.fadex.helpdesk.sse.model;

/**
 * Nomes dos eventos SSE do projeto.
 *
 * Sao constantes de String, e nao um enum, porque {@link NotificationMessage#of} recebe o nome do
 * evento como String; um enum obrigaria a mudar a assinatura do record do motor de notificacoes.
 *
 * Este arquivo tem dono unico: a frente de API. As demais frentes apenas referenciam constantes ja
 * existentes, para que os nomes de evento nao colidam no merge.
 */
public final class NotificationEventName {

	public static final String CHAMADO_ATUALIZADO = "CHAMADO_ATUALIZADO";
	public static final String CHAMADO_ALTA_PRIORIDADE = "CHAMADO_ALTA_PRIORIDADE";
	public static final String INDICADORES_ATUALIZADOS = "INDICADORES_ATUALIZADOS";
	public static final String CLASSIFICACAO_CONCLUIDA = "CLASSIFICACAO_CONCLUIDA";
	public static final String JOB_IA_FALHOU = "JOB_IA_FALHOU";

	private NotificationEventName() {
	}
}
