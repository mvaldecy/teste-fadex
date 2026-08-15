package br.org.fadex.helpdesk.notification.event;

import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.ticket.TicketMinDto;

import java.util.UUID;

/**
 * Evento de dominio de um chamado: um gatilho, dois transportes.
 *
 * O SSE e o e-mail sao derivados deste mesmo evento por listeners pos-commit, em vez de cada
 * service chamar {@code EmailSender} e {@code NotificationService} por conta propria.
 *
 * @param actorId       quem causou a acao; {@code null} quando a mudanca vem do worker de IA
 * @param previousPriority prioridade antes da mudanca; {@code null} na criacao do chamado
 * @param detail        texto ja pronto para o corpo da mensagem (troca de status, comentario)
 */
public record TicketNotificationEvent(
		TicketNotificationType type,
		TicketMinDto ticket,
		NotificationRecipient requester,
		NotificationRecipient assignee,
		UUID actorId,
		TicketPriority previousPriority,
		String detail
) {

	/**
	 * Chamado que nasce ALTA conta como chamado que passou a ser ALTA — e o requisito literal do
	 * desafio: alertar quando um chamado ALTA e aberto.
	 */
	public boolean becameHighPriority() {
		return ticket.priority() == TicketPriority.ALTA && previousPriority != TicketPriority.ALTA;
	}
}
