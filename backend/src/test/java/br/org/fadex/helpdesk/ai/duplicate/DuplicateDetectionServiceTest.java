package br.org.fadex.helpdesk.ai.duplicate;

import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketLink;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.TicketLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DuplicateDetectionServiceTest {

	private static final double THRESHOLD = 0.90;
	private static final int MAX_LINKS = 3;

	@Mock
	private DuplicateEmbeddingRepository duplicateEmbeddingRepository;

	@Mock
	private TicketLinkRepository ticketLinkRepository;

	private DuplicateDetectionService service;

	private final UUID sourceId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		service = new DuplicateDetectionService(
				duplicateEmbeddingRepository,
				ticketLinkRepository,
				THRESHOLD,
				MAX_LINKS
		);

		Ticket source = org.mockito.Mockito.mock(Ticket.class);
		User requester = org.mockito.Mockito.mock(User.class);
		lenient().when(source.getRequester()).thenReturn(requester);
		lenient().when(duplicateEmbeddingRepository.findById(sourceId)).thenReturn(Optional.of(source));
		lenient().when(duplicateEmbeddingRepository.getReferenceById(any(UUID.class)))
				.thenReturn(source);
		lenient().when(ticketLinkRepository.existsBySourceTicketIdAndTargetTicketId(any(), any()))
				.thenReturn(false);
	}

	private Object[] row(UUID ticketId, String embedding) {
		return new Object[] { ticketId.toString(), embedding };
	}

	@Test
	void deveCriarVinculoAcimaDoLimiar() {
		UUID similarId = UUID.randomUUID();
		when(duplicateEmbeddingRepository.findEmbeddedTickets()).thenReturn(List.<Object[]>of(
				row(sourceId, "[1.0,0.0,0.0]"),
				row(similarId, "[0.99,0.1,0.0]")
		));

		int created = service.detect(sourceId);

		assertThat(created).isEqualTo(1);
		verify(ticketLinkRepository).save(any(TicketLink.class));
	}

	@Test
	void deveGravarASimilaridadeDoParNoVinculo() {
		UUID similarId = UUID.randomUUID();
		when(duplicateEmbeddingRepository.findEmbeddedTickets()).thenReturn(List.<Object[]>of(
				row(sourceId, "[1.0,0.0,0.0]"),
				row(similarId, "[0.99,0.1,0.0]")
		));

		service.detect(sourceId);

		ArgumentCaptor<TicketLink> captor = ArgumentCaptor.forClass(TicketLink.class);
		verify(ticketLinkRepository).save(captor.capture());

		// Cosseno de [1,0,0] com [0.99,0.1,0.0], arredondado a quatro casas como a gravacao faz.
		double esperado = Math.round(
				EmbeddingSimilarity.cosine(List.of(1.0, 0.0, 0.0), List.of(0.99, 0.1, 0.0)) * 10000.0
		) / 10000.0;

		assertThat(captor.getValue().getSimilarity()).isNotNull();
		assertThat(captor.getValue().getSimilarity()).isEqualTo(esperado);
	}

	@Test
	void naoDeveCriarVinculoAbaixoDoLimiar() {
		UUID distantId = UUID.randomUUID();
		when(duplicateEmbeddingRepository.findEmbeddedTickets()).thenReturn(List.<Object[]>of(
				row(sourceId, "[1.0,0.0,0.0]"),
				row(distantId, "[0.0,1.0,0.0]")
		));

		int created = service.detect(sourceId);

		assertThat(created).isZero();
		verify(ticketLinkRepository, never()).save(any());
	}

	@Test
	void naoDeveVincularOChamadoAEleMesmo() {
		when(duplicateEmbeddingRepository.findEmbeddedTickets()).thenReturn(List.<Object[]>of(
				row(sourceId, "[1.0,0.0,0.0]")
		));

		int created = service.detect(sourceId);

		assertThat(created).isZero();
		verify(ticketLinkRepository, never()).save(any());
	}

	@Test
	void deveRespeitarOMaximoDeVinculosPorChamado() {
		when(duplicateEmbeddingRepository.findEmbeddedTickets()).thenReturn(List.<Object[]>of(
				row(sourceId, "[1.0,0.0,0.0]"),
				row(UUID.randomUUID(), "[1.0,0.01,0.0]"),
				row(UUID.randomUUID(), "[1.0,0.02,0.0]"),
				row(UUID.randomUUID(), "[1.0,0.03,0.0]"),
				row(UUID.randomUUID(), "[1.0,0.04,0.0]"),
				row(UUID.randomUUID(), "[1.0,0.05,0.0]")
		));

		int created = service.detect(sourceId);

		assertThat(created).isEqualTo(MAX_LINKS);
	}

	@Test
	void naoDeveDuplicarVinculoJaExistente() {
		UUID similarId = UUID.randomUUID();
		when(duplicateEmbeddingRepository.findEmbeddedTickets()).thenReturn(List.<Object[]>of(
				row(sourceId, "[1.0,0.0,0.0]"),
				row(similarId, "[0.99,0.1,0.0]")
		));
		when(ticketLinkRepository.existsBySourceTicketIdAndTargetTicketId(sourceId, similarId))
				.thenReturn(true);

		int created = service.detect(sourceId);

		assertThat(created).isZero();
		verify(ticketLinkRepository, never()).save(any());
	}

	@Test
	void deveIgnorarChamadoSemEmbedding() {
		when(duplicateEmbeddingRepository.findEmbeddedTickets()).thenReturn(List.<Object[]>of(
				row(UUID.randomUUID(), "[1.0,0.0,0.0]")
		));

		int created = service.detect(sourceId);

		assertThat(created).isZero();
		verify(ticketLinkRepository, never()).save(any());
	}

	@Test
	void deveIgnorarCandidatoComVetorDeOutraDimensao() {
		when(duplicateEmbeddingRepository.findEmbeddedTickets()).thenReturn(List.<Object[]>of(
				row(sourceId, "[1.0,0.0,0.0]"),
				row(UUID.randomUUID(), "[1.0,0.0]")
		));

		int created = service.detect(sourceId);

		assertThat(created).isZero();
	}

	@Test
	void deveGravarVinculoComOSolicitanteDoChamadoDeOrigem() {
		UUID similarId = UUID.randomUUID();
		when(duplicateEmbeddingRepository.findEmbeddedTickets()).thenReturn(List.<Object[]>of(
				row(sourceId, "[1.0,0.0,0.0]"),
				row(similarId, "[0.99,0.1,0.0]")
		));

		service.detect(sourceId);

		verify(ticketLinkRepository).save(argThat((TicketLink link) -> link.getCreatedBy() != null));
	}
}
