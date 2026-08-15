package br.org.fadex.helpdesk.ai.duplicate;

import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.security.AccessControlService;
import br.org.fadex.helpdesk.service.TicketService;
import org.springframework.beans.factory.annotation.Value;
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
	private final DuplicateEmbeddingRepository duplicateEmbeddingRepository;
	private final TicketService ticketService;
	private final AccessControlService accessControlService;
	private final double similarityThreshold;

	public TicketSimilarityService(
			SimilarTicketRepository similarTicketRepository,
			DuplicateEmbeddingRepository duplicateEmbeddingRepository,
			TicketService ticketService,
			AccessControlService accessControlService,
			@Value("${app.ai.similarity.threshold}") double similarityThreshold
	) {
		this.similarTicketRepository = similarTicketRepository;
		this.duplicateEmbeddingRepository = duplicateEmbeddingRepository;
		this.ticketService = ticketService;
		this.accessControlService = accessControlService;
		this.similarityThreshold = similarityThreshold;
	}

	/**
	 * Ranking dos chamados mais proximos, **sem filtro de limiar**.
	 *
	 * A similaridade e recalculada na hora, e nao lida de {@code ticket_links}: o vinculo so existe
	 * acima do limiar, e o ponto deste metodo e justamente mostrar o que ficou abaixo. Sem isso, um
	 * chamado sem duplicata detectada e indistinguivel de um chamado onde o modelo falhou.
	 *
	 * O calculo percorre todos os embeddings da base em memoria. E adequado na escala de milhares
	 * de chamados e deve virar consulta ordenada pelo indice HNSW do pgvector acima disso — o
	 * indice ja existe, e so nao e usado porque o H2 dos testes nao roda o operador vetorial.
	 */
	@Transactional(readOnly = true)
	public List<NearestTicketDto> findNearest(UUID ticketId, int limit) {
		accessControlService.assertAdmin();
		ticketService.findEntityById(ticketId);

		Map<UUID, List<Double>> vectors = new LinkedHashMap<>();

		for (Object[] row : duplicateEmbeddingRepository.findEmbeddedTickets()) {
			UUID id = UUID.fromString(String.valueOf(row[0]));
			List<Double> vector = EmbeddingSimilarity.parse(
					row[1] == null ? null : String.valueOf(row[1]));

			if (!vector.isEmpty()) {
				vectors.put(id, vector);
			}
		}

		List<Double> source = vectors.get(ticketId);

		if (source == null) {
			return List.of();
		}

		record Scored(UUID id, double similarity) {
		}

		List<Scored> ranking = new ArrayList<>();

		for (Map.Entry<UUID, List<Double>> entry : vectors.entrySet()) {
			if (entry.getKey().equals(ticketId) || entry.getValue().size() != source.size()) {
				continue;
			}

			ranking.add(new Scored(
					entry.getKey(),
					EmbeddingSimilarity.cosine(source, entry.getValue())));
		}

		ranking.sort(Comparator.comparingDouble(Scored::similarity).reversed());
		List<Scored> top = ranking.subList(0, Math.min(limit, ranking.size()));

		if (top.isEmpty()) {
			return List.of();
		}

		Map<UUID, Ticket> tickets = new LinkedHashMap<>();

		for (Ticket ticket : duplicateEmbeddingRepository.findAllByIdIn(
				top.stream().map(Scored::id).toList())) {
			tickets.put(ticket.getId(), ticket);
		}

		List<NearestTicketDto> response = new ArrayList<>();

		for (Scored scored : top) {
			Ticket ticket = tickets.get(scored.id());

			if (ticket == null) {
				continue;
			}

			response.add(new NearestTicketDto(
					ticket.getId(),
					ticket.getTitle(),
					ticket.getStatus(),
					ticket.getPriority(),
					ticket.getCategory(),
					scored.similarity(),
					scored.similarity() >= similarityThreshold,
					ticket.getCreatedAt()
			));
		}

		return response;
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
