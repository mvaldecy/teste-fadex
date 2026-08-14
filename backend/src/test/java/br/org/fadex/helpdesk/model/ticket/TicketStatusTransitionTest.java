package br.org.fadex.helpdesk.model.ticket;

import br.org.fadex.helpdesk.model.enums.TicketStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketStatusTransitionTest {

	@Test
	void deveAceitarAsTransicoesValidas() {
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.ABERTO, TicketStatus.EM_ANDAMENTO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.ABERTO, TicketStatus.RESOLVIDO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.ABERTO, TicketStatus.FECHADO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.EM_ANDAMENTO, TicketStatus.ABERTO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.EM_ANDAMENTO, TicketStatus.RESOLVIDO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.EM_ANDAMENTO, TicketStatus.FECHADO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.RESOLVIDO, TicketStatus.EM_ANDAMENTO)).isTrue();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.RESOLVIDO, TicketStatus.FECHADO)).isTrue();
	}

	@Test
	void naoDevePermitirSairDeChamadoFechado() {
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.FECHADO, TicketStatus.ABERTO)).isFalse();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.FECHADO, TicketStatus.EM_ANDAMENTO)).isFalse();
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.FECHADO, TicketStatus.RESOLVIDO)).isFalse();
		assertThat(TicketStatusTransition.allowedFrom(TicketStatus.FECHADO)).isEmpty();
	}

	@Test
	void naoDevePermitirReabrirChamadoResolvidoParaAberto() {
		assertThat(TicketStatusTransition.isAllowed(TicketStatus.RESOLVIDO, TicketStatus.ABERTO)).isFalse();
	}

	@Test
	void naoDevePermitirTransicaoParaOMesmoStatus() {
		for (TicketStatus status : TicketStatus.values()) {
			assertThat(TicketStatusTransition.isAllowed(status, status)).isFalse();
		}
	}

	@Test
	void deveExporOsDestinosPermitidosDeCadaStatus() {
		assertThat(TicketStatusTransition.allowedFrom(TicketStatus.ABERTO))
				.containsExactlyInAnyOrder(TicketStatus.EM_ANDAMENTO, TicketStatus.RESOLVIDO, TicketStatus.FECHADO);
		assertThat(TicketStatusTransition.allowedFrom(TicketStatus.RESOLVIDO))
				.containsExactlyInAnyOrder(TicketStatus.EM_ANDAMENTO, TicketStatus.FECHADO);
	}
}
