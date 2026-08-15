package br.org.fadex.helpdesk.ai.duplicate;

import br.org.fadex.helpdesk.exception.ForbiddenException;
import br.org.fadex.helpdesk.exception.NotFoundException;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.security.AccessControlService;
import br.org.fadex.helpdesk.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketSimilarityServiceTest {

	private static final UUID TICKET_ID = UUID.randomUUID();
	private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 14, 10, 0);

	@Mock
	private SimilarTicketRepository similarTicketRepository;

	@Mock
	private TicketService ticketService;

	@Mock
	private AccessControlService accessControlService;

	@Mock
	private DuplicateEmbeddingRepository duplicateEmbeddingRepository;

	// Construido a mao: o limiar e um double vindo de @Value, e o @InjectMocks nao preenche
	// primitivo — deixaria 0.0 sem avisar.
	private TicketSimilarityService ticketSimilarityService;

	@BeforeEach
	void setUp() {
		ticketSimilarityService = new TicketSimilarityService(
				similarTicketRepository,
				duplicateEmbeddingRepository,
				ticketService,
				accessControlService,
				0.75
		);
	}

	@Test
	void deveJuntarVinculosDasDuasDirecoes() {
		UUID comoOrigem = UUID.randomUUID();
		UUID comoAlvo = UUID.randomUUID();
		when(ticketService.findEntityById(TICKET_ID)).thenReturn(mock(Ticket.class));
		when(similarTicketRepository.findLinkedAsSource(TICKET_ID))
				.thenReturn(List.of(similar(comoOrigem, 0.95)));
		when(similarTicketRepository.findLinkedAsTarget(TICKET_ID))
				.thenReturn(List.of(similar(comoAlvo, 0.91)));

		List<SimilarTicketDto> similares = ticketSimilarityService.findSimilar(TICKET_ID);

		assertThat(similares).extracting(SimilarTicketDto::id)
				.containsExactlyInAnyOrder(comoOrigem, comoAlvo);
	}

	@Test
	void naoDeveRepetirOChamadoQuandoOVinculoExisteNasDuasDirecoes() {
		UUID outro = UUID.randomUUID();
		when(ticketService.findEntityById(TICKET_ID)).thenReturn(mock(Ticket.class));
		when(similarTicketRepository.findLinkedAsSource(TICKET_ID))
				.thenReturn(List.of(similar(outro, 0.93)));
		when(similarTicketRepository.findLinkedAsTarget(TICKET_ID))
				.thenReturn(List.of(similar(outro, 0.88)));

		List<SimilarTicketDto> similares = ticketSimilarityService.findSimilar(TICKET_ID);

		assertThat(similares).hasSize(1);
		assertThat(similares.getFirst().similarity()).isEqualTo(0.93);
	}

	@Test
	void deveOrdenarPelaMaiorSimilaridadeComNulosNoFim() {
		UUID alto = UUID.randomUUID();
		UUID baixo = UUID.randomUUID();
		UUID semScore = UUID.randomUUID();
		when(ticketService.findEntityById(TICKET_ID)).thenReturn(mock(Ticket.class));
		when(similarTicketRepository.findLinkedAsSource(TICKET_ID)).thenReturn(List.of(
				similar(baixo, 0.91),
				similar(semScore, null),
				similar(alto, 0.97)
		));
		when(similarTicketRepository.findLinkedAsTarget(TICKET_ID)).thenReturn(List.of());

		List<SimilarTicketDto> similares = ticketSimilarityService.findSimilar(TICKET_ID);

		assertThat(similares).extracting(SimilarTicketDto::id)
				.containsExactly(alto, baixo, semScore);
	}

	@Test
	void deveDevolverListaVaziaQuandoNaoHaVinculo() {
		when(ticketService.findEntityById(TICKET_ID)).thenReturn(mock(Ticket.class));
		when(similarTicketRepository.findLinkedAsSource(TICKET_ID)).thenReturn(List.of());
		when(similarTicketRepository.findLinkedAsTarget(TICKET_ID)).thenReturn(List.of());

		assertThat(ticketSimilarityService.findSimilar(TICKET_ID)).isEmpty();
	}

	@Test
	void deveNegarConsultaParaSolicitante() {
		doThrow(new ForbiddenException("Acesso negado ao recurso solicitado."))
				.when(accessControlService).assertAdmin();

		assertThatThrownBy(() -> ticketSimilarityService.findSimilar(TICKET_ID))
				.isInstanceOf(ForbiddenException.class);

		verify(similarTicketRepository, never()).findLinkedAsSource(TICKET_ID);
		verify(similarTicketRepository, never()).findLinkedAsTarget(TICKET_ID);
	}

	@Test
	void devePropagarNaoEncontradoQuandoChamadoNaoExiste() {
		when(ticketService.findEntityById(TICKET_ID))
				.thenThrow(new NotFoundException("Chamado nao encontrado."));

		assertThatThrownBy(() -> ticketSimilarityService.findSimilar(TICKET_ID))
				.isInstanceOf(NotFoundException.class);

		verify(similarTicketRepository, never()).findLinkedAsSource(TICKET_ID);
	}

	private SimilarTicketDto similar(UUID id, Double similarity) {
		return new SimilarTicketDto(
				id,
				"Chamado semelhante",
				TicketStatus.ABERTO,
				TicketPriority.MEDIA,
				TicketCategory.ACESSO,
				similarity,
				CREATED_AT
		);
	}
}
