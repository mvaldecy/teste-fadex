package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.enums.TicketPriority;

import java.time.Duration;

/**
 * Alvos de SLA por prioridade, como configuracao e nao como tabela (decisao D6 do design).
 *
 * Regra de apuracao: chamado fechado cumpre se fechou dentro do alvo. Chamado ainda aberto e dentro
 * do alvo fica {@link SlaOutcome#NOT_EVALUABLE}, ou seja, fora do denominador — so vira violacao
 * depois de estourar. Sem isso, todo chamado recem-criado contaria como violacao e o percentual
 * afundaria sozinho com o tempo.
 */
public enum SlaTarget {

	ALTA(TicketPriority.ALTA, 4),
	MEDIA(TicketPriority.MEDIA, 24),
	BAIXA(TicketPriority.BAIXA, 72);

	private final TicketPriority priority;
	private final int targetHours;

	SlaTarget(TicketPriority priority, int targetHours) {
		this.priority = priority;
		this.targetHours = targetHours;
	}

	public static SlaTarget forPriority(TicketPriority priority) {
		for (SlaTarget target : values()) {
			if (target.priority == priority) {
				return target;
			}
		}

		throw new IllegalArgumentException("Prioridade sem alvo de SLA: " + priority);
	}

	public SlaOutcome evaluate(Duration elapsed, boolean closed) {
		boolean withinTarget = elapsed.toSeconds() <= Duration.ofHours(targetHours).toSeconds();

		if (closed) {
			return withinTarget ? SlaOutcome.WITHIN : SlaOutcome.BREACHED;
		}

		return withinTarget ? SlaOutcome.NOT_EVALUABLE : SlaOutcome.BREACHED;
	}

	public TicketPriority getPriority() {
		return priority;
	}

	public int getTargetHours() {
		return targetHours;
	}
}
