package br.org.fadex.helpdesk.ai.duplicate;

import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketLink;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.TicketLinkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Deteccao de chamados duplicados por similaridade de embedding.
 *
 * Duplicado e sinal, nao regra: este service nunca altera status, prioridade ou categoria de nenhum
 * chamado, e nunca bloqueia a criacao. Apenas grava vinculos em {@code ticket_links}.
 *
 * O calculo e feito em Java (ver {@link EmbeddingSimilarity}) porque o H2 dos testes nao roda o
 * operador vetorial do pgvector.
 */
@Service
public class DuplicateDetectionService {

	private final DuplicateEmbeddingRepository duplicateEmbeddingRepository;
	private final TicketLinkRepository ticketLinkRepository;
	private final double similarityThreshold;
	private final int maxLinks;

	public DuplicateDetectionService(
			DuplicateEmbeddingRepository duplicateEmbeddingRepository,
			TicketLinkRepository ticketLinkRepository,
			@Value("${app.ai.similarity.threshold}") double similarityThreshold,
			@Value("${app.ai.similarity.limit}") int maxLinks
	) {
		this.duplicateEmbeddingRepository = duplicateEmbeddingRepository;
		this.ticketLinkRepository = ticketLinkRepository;
		this.similarityThreshold = similarityThreshold;
		this.maxLinks = maxLinks;
	}

	@Transactional
	public int detect(UUID sourceTicketId) {
		List<Object[]> rows = duplicateEmbeddingRepository.findEmbeddedTickets();
		List<DuplicateCandidate> candidates = toCandidates(rows);

		Optional<DuplicateCandidate> source = candidates.stream()
				.filter(candidate -> candidate.ticketId().equals(sourceTicketId))
				.findFirst();
		if (source.isEmpty()) {
			return 0;
		}

		List<Double> sourceVector = EmbeddingSimilarity.parse(source.get().embedding());
		if (sourceVector.isEmpty()) {
			return 0;
		}

		List<ScoredCandidate> matches = findMatches(candidates, sourceTicketId, sourceVector);
		int created = createLinks(sourceTicketId, matches);

		return created;
	}

	private List<DuplicateCandidate> toCandidates(List<Object[]> rows) {
		List<DuplicateCandidate> candidates = new ArrayList<>();

		for (Object[] row : rows) {
			UUID ticketId = UUID.fromString(String.valueOf(row[0]));
			String embedding = row[1] == null ? null : String.valueOf(row[1]);
			candidates.add(new DuplicateCandidate(ticketId, embedding));
		}

		return candidates;
	}

	private List<ScoredCandidate> findMatches(
			List<DuplicateCandidate> candidates,
			UUID sourceTicketId,
			List<Double> sourceVector
	) {
		List<ScoredCandidate> matches = new ArrayList<>();

		for (DuplicateCandidate candidate : candidates) {
			if (candidate.ticketId().equals(sourceTicketId)) {
				continue;
			}

			List<Double> vector = EmbeddingSimilarity.parse(candidate.embedding());
			if (vector.size() != sourceVector.size()) {
				continue;
			}

			double similarity = EmbeddingSimilarity.cosine(sourceVector, vector);
			if (similarity >= similarityThreshold) {
				matches.add(new ScoredCandidate(candidate.ticketId(), similarity));
			}
		}

		matches.sort(Comparator.comparingDouble(ScoredCandidate::similarity).reversed());

		return matches;
	}

	private int createLinks(UUID sourceTicketId, List<ScoredCandidate> matches) {
		if (matches.isEmpty()) {
			return 0;
		}

		Optional<Ticket> sourceTicket = duplicateEmbeddingRepository.findById(sourceTicketId);
		if (sourceTicket.isEmpty()) {
			return 0;
		}

		User createdBy = sourceTicket.get().getRequester();
		int created = 0;

		for (ScoredCandidate match : matches) {
			if (created >= maxLinks) {
				break;
			}
			if (ticketLinkRepository.existsBySourceTicketIdAndTargetTicketId(sourceTicketId, match.ticketId())) {
				continue;
			}

			Ticket target = duplicateEmbeddingRepository.getReferenceById(match.ticketId());
			ticketLinkRepository.save(new TicketLink(sourceTicket.get(), target, createdBy));
			created++;
		}

		return created;
	}

	private record ScoredCandidate(UUID ticketId, double similarity) {
	}
}
