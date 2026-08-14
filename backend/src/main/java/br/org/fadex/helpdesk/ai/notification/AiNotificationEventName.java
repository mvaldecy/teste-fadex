package br.org.fadex.helpdesk.ai.notification;

/**
 * Nomes de eventos SSE disparados pela frente IA.
 *
 * Temporario: quando a frente API mergear {@code NotificationEventName}, trocar os usos por aquelas
 * constantes e apagar esta classe. As grafias aqui sao identicas as da tabela de eventos do documento
 * de frentes de trabalho. {@code NotificationMessage.of(...)} recebe {@code String}, entao esta frente
 * nao fica bloqueada pela ausencia do enum da frente API.
 */
public abstract class AiNotificationEventName {

	public static final String CLASSIFICACAO_CONCLUIDA = "CLASSIFICACAO_CONCLUIDA";
	public static final String JOB_IA_FALHOU = "JOB_IA_FALHOU";
	public static final String INDICADORES_ATUALIZADOS = "INDICADORES_ATUALIZADOS";

	private AiNotificationEventName() {
	}
}
