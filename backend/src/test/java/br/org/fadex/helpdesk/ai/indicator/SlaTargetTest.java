package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.enums.TicketPriority;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SlaTargetTest {

	@Test
	void deveExporAlvosDoDocumentoDeFrentes() {
		assertThat(SlaTarget.forPriority(TicketPriority.ALTA).getTargetHours()).isEqualTo(4);
		assertThat(SlaTarget.forPriority(TicketPriority.MEDIA).getTargetHours()).isEqualTo(24);
		assertThat(SlaTarget.forPriority(TicketPriority.BAIXA).getTargetHours()).isEqualTo(72);
	}

	@Test
	void chamadoFechadoDentroDoAlvoCumpre() {
		SlaTarget target = SlaTarget.forPriority(TicketPriority.ALTA);

		assertThat(target.evaluate(Duration.ofHours(3), true)).isEqualTo(SlaOutcome.WITHIN);
	}

	@Test
	void chamadoFechadoForaDoAlvoViola() {
		SlaTarget target = SlaTarget.forPriority(TicketPriority.ALTA);

		assertThat(target.evaluate(Duration.ofHours(9), true)).isEqualTo(SlaOutcome.BREACHED);
	}

	@Test
	void chamadoAbertoAindaDentroDoAlvoFicaForaDoDenominador() {
		SlaTarget target = SlaTarget.forPriority(TicketPriority.MEDIA);

		assertThat(target.evaluate(Duration.ofHours(2), false)).isEqualTo(SlaOutcome.NOT_EVALUABLE);
	}

	@Test
	void chamadoAbertoJaEstouradoViola() {
		SlaTarget target = SlaTarget.forPriority(TicketPriority.MEDIA);

		assertThat(target.evaluate(Duration.ofHours(30), false)).isEqualTo(SlaOutcome.BREACHED);
	}

	@Test
	void limiteExatoCumpreQuandoFechado() {
		SlaTarget target = SlaTarget.forPriority(TicketPriority.ALTA);

		assertThat(target.evaluate(Duration.ofHours(4), true)).isEqualTo(SlaOutcome.WITHIN);
	}

	@Test
	void limiteExatoAindaNaoViolaQuandoAberto() {
		SlaTarget target = SlaTarget.forPriority(TicketPriority.ALTA);

		assertThat(target.evaluate(Duration.ofHours(4), false)).isEqualTo(SlaOutcome.NOT_EVALUABLE);
	}

	@Test
	void deveExporAPrioridadeCorrespondente() {
		assertThat(SlaTarget.forPriority(TicketPriority.BAIXA).getPriority())
				.isEqualTo(TicketPriority.BAIXA);
	}
}
