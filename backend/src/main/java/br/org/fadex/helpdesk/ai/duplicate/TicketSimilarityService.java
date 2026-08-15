package br.org.fadex.helpdesk.ai.duplicate;

import br.org.fadex.helpdesk.security.AccessControlService;
import br.org.fadex.helpdesk.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consulta os chamados semelhantes ja detectados para um chamado.
 *
 * Nao roda deteccao: o vinculo e gravado pelo worker de embedding. Aqui e so leitura do que ja
 * existe em {@code ticket_links}.
 *
 * Restrito a ADMIN de proposito. O resultado expoe titulo e situacao de chamados de outros
 * solicitantes, e a visibilidade de chamado no projeto e escopada por solicitante
 * ({@code assertCanAccessTicket}). Liberar esta leitura para SOLICITANTE vazaria o titulo de
 * chamados alheios por um caminho lateral.
 */
@Service
public class TicketSimilarityService {

	private final SimilarTicketRepository similarTicketRepository;
	private final TicketService ticketService;
	private final AccessControlService accessControlService;

	public TicketSimilarityService(
			SimilarTicketRepository similarTicketRepository,
			TicketService ticketService,
			AccessControlService accessControlService
	) {
		this.similarTicketRepository = similarTicketRepository;
		this.ticketService = ticketService;
		this.accessControlService = accessControlService;
	}

	@Transactional(readOnly = true)
	public List<SimilarTicketDto> findSimilar(UUID ticketId) {
		accessControlService.assertAdmin();

		// Confirma a existencia do chamado antes de consultar os vinculos: sem isso um id inexistente
		// devolveria lista vazia em vez de 404, e o front nao distinguiria "sem semelhantes" de
		// "chamado nao existe".
		ticketService.findEntityById(ticketId);

		List<SimilarTicketDto> asSource = similarTicketRepository.findLinkedAsSource(ticketId);
		List<SimilarTicketDto> asTarget = similarTicketRepository.findLinkedAsTarget(ticketId);
		List<SimilarTicketDto> response = merge(asSource, asTarget);

		return response;
	}

	/**
	 * Junta as duas direcoes, remove repetido e ordena pela maior similaridade.
	 *
	 * O mesmo par pode estar gravado nas duas direcoes — {@code uk_ticket_links_pair} e sobre o par
	 * ordenado, entao {@code X -> Y} e {@code Y -> X} coexistem. Nesse caso vale o maior score, que
	 * e a leitura mais util: a deteccao mais confiante das duas.
	 */
	private List<SimilarTicketDto> merge(List<SimilarTicketDto> asSource, List<SimilarTicketDto> asTarget) {
		Map<UUID, SimilarTicketDto> byTicketId = new LinkedHashMap<>();

		List<SimilarTicketDto> all = new ArrayList<>(asSource);
		all.addAll(asTarget);

		for (SimilarTicketDto candidate : all) {
			byTicketId.merge(candidate.id(), candidate, this::mostConfident);
		}

		List<SimilarTicketDto> merged = new ArrayList<>(byTicketId.values());
		merged.sort(
				Comparator.comparing(
						SimilarTicketDto::similarity,
						Comparator.nullsLast(Comparator.reverseOrder())
				).thenComparing(
						SimilarTicketDto::createdAt,
						Comparator.nullsLast(Comparator.reverseOrder())
				)
		);

		return merged;
	}

	private SimilarTicketDto mostConfident(SimilarTicketDto left, SimilarTicketDto right) {
		if (left.similarity() == null) {
			return right;
		}
		if (right.similarity() == null) {
			return left;
		}

		return left.similarity() >= right.similarity() ? left : right;
	}
}
