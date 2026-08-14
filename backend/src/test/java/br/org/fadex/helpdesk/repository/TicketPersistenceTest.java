package br.org.fadex.helpdesk.repository;

import br.org.fadex.helpdesk.config.JpaAuditingConfig;
import br.org.fadex.helpdesk.model.comment.TicketComment;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketEventType;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.event.TicketEvent;
import br.org.fadex.helpdesk.model.ticket.Ticket;
import br.org.fadex.helpdesk.model.token.RefreshToken;
import br.org.fadex.helpdesk.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
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

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private TicketEventRepository ticketEventRepository;

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
		ticket.applyAutomaticClassification(
				TicketCategory.SISTEMAS,
				TicketPriority.ALTA,
				"Classificacao automatica por fallback deterministico."
		);
		ticket.updateEmbeddingMetadata("all-minilm", LocalDateTime.of(2026, 8, 14, 10, 0));
		Ticket savedTicket = ticketRepository.save(ticket);

		ticketCommentRepository.save(new TicketComment(savedTicket, requester, "Chamado criado."));
		ticketCommentRepository.save(new TicketComment(savedTicket, assignee, "Responsavel atribuido."));

		Ticket foundTicket = ticketRepository.findById(savedTicket.getId()).orElseThrow();
		List<TicketComment> comments = ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(savedTicket.getId());

		assertThat(userRepository.findByEmail("maria@fadex.org.br")).contains(requester);
		assertThat(requester.getCreatedAt()).isNotNull();
		assertThat(requester.getUpdatedAt()).isNotNull();
		assertThat(foundTicket.getStatus()).isEqualTo(TicketStatus.ABERTO);
		assertThat(foundTicket.getCategory()).isEqualTo(TicketCategory.SISTEMAS);
		assertThat(foundTicket.getPriority()).isEqualTo(TicketPriority.ALTA);
		assertThat(foundTicket.getClassificationOrigin()).isEqualTo(ClassificationOrigin.IA);
		assertThat(foundTicket.getClassificationJustification())
				.isEqualTo("Classificacao automatica por fallback deterministico.");
		assertThat(foundTicket.getEmbeddingModel()).isEqualTo("all-minilm");
		assertThat(foundTicket.getEmbeddingUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 0));
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

	@Test
	void devePersistirUsuarioComTrocaObrigatoriaRefreshTokenEEventoDeChamado() {
		User requester = userRepository.save(new User(
				"Maria Solicitante",
				"maria.persistencia@fadex.org.br",
				"hash",
				Role.SOLICITANTE,
				true
		));
		Ticket ticket = ticketRepository.save(new Ticket(
				"Erro ao acessar sistema",
				"Nao consigo acessar o sistema interno.",
				TicketCategory.OUTROS,
				TicketPriority.MEDIA,
				ClassificationOrigin.PENDENTE,
				requester
		));
		RefreshToken refreshToken = refreshTokenRepository.save(new RefreshToken(
				requester,
				"hash-token",
				LocalDateTime.now().plusDays(7)
		));
		TicketEvent event = ticketEventRepository.save(new TicketEvent(
				ticket,
				requester,
				TicketEventType.CHAMADO_CRIADO,
				"Chamado criado.",
				null
		));

		assertThat(requester.getMustChangePassword()).isTrue();
		assertThat(refreshToken.getUser()).isEqualTo(requester);
		assertThat(refreshToken.getTokenHash()).isEqualTo("hash-token");
		assertThat(refreshToken.getRevokedAt()).isNull();
		assertThat(event.getTicket()).isEqualTo(ticket);
		assertThat(event.getActor()).isEqualTo(requester);
		assertThat(event.getType()).isEqualTo(TicketEventType.CHAMADO_CRIADO);
		assertThat(event.getCreatedAt()).isNotNull();
	}
}
