package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.config.JpaAuditingConfig;
import br.org.fadex.helpdesk.model.comment.TicketComment;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class TicketPersistenceTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TicketRepository ticketRepository;

	@Autowired
	private TicketCommentRepository ticketCommentRepository;

	@Test
	void devePersistirChamadoComSolicitanteResponsavelEnumsEComentarios() {
		User requester = userRepository.save(new User(
				"Maria Solicitante",
				"maria@fadex.org.br",
				"senha-com-hash",
				Role.SOLICITANTE
		));
		User assignee = userRepository.save(new User(
				"Admin Suporte",
				"admin@fadex.org.br",
				"senha-com-hash-admin",
				Role.ADMIN
		));

		Ticket ticket = new Ticket(
				"Erro no acesso",
				"Nao consigo acessar o sistema interno.",
				TicketCategory.ACESSO,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE,
				requester
		);
		ticket.assignTo(assignee);
		Ticket savedTicket = ticketRepository.save(ticket);

		ticketCommentRepository.save(new TicketComment(savedTicket, requester, "Chamado criado."));
		ticketCommentRepository.save(new TicketComment(savedTicket, assignee, "Responsavel atribuido."));

		Ticket foundTicket = ticketRepository.findById(savedTicket.getId()).orElseThrow();
		List<TicketComment> comments = ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(savedTicket.getId());

		assertThat(userRepository.findByEmail("maria@fadex.org.br")).contains(requester);
		assertThat(requester.getCreatedAt()).isNotNull();
		assertThat(requester.getUpdatedAt()).isNotNull();
		assertThat(foundTicket.getStatus()).isEqualTo(TicketStatus.ABERTO);
		assertThat(foundTicket.getCategory()).isEqualTo(TicketCategory.ACESSO);
		assertThat(foundTicket.getPriority()).isEqualTo(TicketPriority.MEDIA);
		assertThat(foundTicket.getClassificationOrigin()).isEqualTo(ClassificationOrigin.PENDENTE);
		assertThat(foundTicket.getRequester().getId()).isEqualTo(requester.getId());
		assertThat(foundTicket.getAssignee().getId()).isEqualTo(assignee.getId());
		assertThat(foundTicket.getCreatedAt()).isNotNull();
		assertThat(foundTicket.getUpdatedAt()).isNotNull();
		assertThat(comments).extracting(TicketComment::getText)
				.containsExactly("Chamado criado.", "Responsavel atribuido.");
		assertThat(comments).allSatisfy(comment -> {
			assertThat(comment.getCreatedAt()).isNotNull();
			assertThat(comment.getUpdatedAt()).isNotNull();
		});
	}

	@Test
	void deveImpedirEmailDuplicado() {
		userRepository.saveAndFlush(new User(
				"Maria Solicitante",
				"maria@fadex.org.br",
				"senha-com-hash",
				Role.SOLICITANTE
		));

		User duplicated = new User(
				"Outra Maria",
				"maria@fadex.org.br",
				"outro-hash",
				Role.SOLICITANTE
		);

		assertThatThrownBy(() -> userRepository.saveAndFlush(duplicated))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
