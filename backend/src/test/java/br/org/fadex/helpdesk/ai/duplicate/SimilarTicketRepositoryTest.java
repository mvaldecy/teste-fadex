package br.org.fadex.helpdesk.ai.duplicate;

import br.org.fadex.helpdesk.config.JpaAuditingConfig;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.ticket.TicketLink;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.repository.TicketLinkRepository;
import br.org.fadex.helpdesk.repository.TicketRepository;
import br.org.fadex.helpdesk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova contra banco real que o vinculo e legivel nas duas direcoes e que a similaridade persiste.
 *
 * Um teste com mock do repository nao cobriria nada disso: o risco real esta na consulta e no
 * mapeamento da coluna nova, nao na orquestracao do service.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class SimilarTicketRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TicketRepository ticketRepository;

	@Autowired
	private TicketLinkRepository ticketLinkRepository;

	@Autowired
	private SimilarTicketRepository similarTicketRepository;

	@Test
	void deveEncontrarOVinculoNasDuasDirecoes() {
		User requester = userRepository.save(new User(
				"Solicitante Similares",
				"similares@fadex.org.br",
				"hash",
				Role.SOLICITANTE
		));
		Ticket alvo = ticketRepository.save(ticket(requester, "Chamado alvo"));
		Ticket origem = ticketRepository.save(ticket(requester, "Chamado origem"));
		Ticket anterior = ticketRepository.save(ticket(requester, "Chamado anterior"));

		// origem -> alvo, e anterior -> origem. Consultando por "origem" os dois precisam aparecer,
		// cada um por uma direcao diferente.
		ticketLinkRepository.save(new TicketLink(origem, alvo, requester, 0.93));
		ticketLinkRepository.saveAndFlush(new TicketLink(anterior, origem, requester, 0.87));

		List<SimilarTicketDto> comoOrigem = similarTicketRepository.findLinkedAsSource(origem.getId());
		List<SimilarTicketDto> comoAlvo = similarTicketRepository.findLinkedAsTarget(origem.getId());

		assertThat(comoOrigem).extracting(SimilarTicketDto::id).containsExactly(alvo.getId());
		assertThat(comoOrigem.getFirst().similarity()).isEqualTo(0.93);
		assertThat(comoOrigem.getFirst().title()).isEqualTo("Chamado alvo");
		assertThat(comoAlvo).extracting(SimilarTicketDto::id).containsExactly(anterior.getId());
		assertThat(comoAlvo.getFirst().similarity()).isEqualTo(0.87);
	}

	@Test
	void devePersistirVinculoSemSimilaridade() {
		User requester = userRepository.save(new User(
				"Solicitante Sem Score",
				"sem-score@fadex.org.br",
				"hash",
				Role.SOLICITANTE
		));
		Ticket origem = ticketRepository.save(ticket(requester, "Origem sem score"));
		Ticket alvo = ticketRepository.save(ticket(requester, "Alvo sem score"));

		ticketLinkRepository.saveAndFlush(new TicketLink(origem, alvo, requester));

		List<SimilarTicketDto> similares = similarTicketRepository.findLinkedAsSource(origem.getId());

		assertThat(similares).hasSize(1);
		assertThat(similares.getFirst().similarity()).isNull();
	}

	private Ticket ticket(User requester, String title) {
		return new Ticket(
				title,
				"Descricao do " + title,
				TicketCategory.ACESSO,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE,
				requester
		);
	}
}
