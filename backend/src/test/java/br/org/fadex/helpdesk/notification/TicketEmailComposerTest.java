package br.org.fadex.helpdesk.notification;

import br.org.fadex.helpdesk.mail.EmailMessage;
import br.org.fadex.helpdesk.mail.EmailTemplateRenderer;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.ticket.TicketMinDto;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.model.user.UserMinDto;
import br.org.fadex.helpdesk.notification.event.NotificationRecipient;
import br.org.fadex.helpdesk.notification.event.TicketNotificationEvent;
import br.org.fadex.helpdesk.notification.event.TicketNotificationType;
import br.org.fadex.helpdesk.notification.event.UserCreatedNotificationEvent;
import br.org.fadex.helpdesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketEmailComposerTest {

	private static final UUID SOLICITANTE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID RESPONSAVEL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID OUTRO_ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

	@Mock
	private UserRepository userRepository;

	private TicketEmailComposer composer;

	@BeforeEach
	void setUp() {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.HTML);
		resolver.setCharacterEncoding("UTF-8");

		TemplateEngine templateEngine = new SpringTemplateEngine();
		templateEngine.setTemplateResolver(resolver);

		composer = new TicketEmailComposer(
				new EmailTemplateRenderer(templateEngine),
				userRepository,
				"http://localhost:3000"
		);
	}

	@Test
	void chamadoAbertoComPrioridadeAltaDeveNotificarTodosOsAdmins() {
		when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(
				admin("Ana Admin", "ana@fadex.org.br", RESPONSAVEL_ID),
				admin("Bruno Admin", "bruno@fadex.org.br", OUTRO_ADMIN_ID)
		));

		List<EmailMessage> messages = composer.compose(event(
				TicketNotificationType.CHAMADO_CRIADO,
				TicketPriority.ALTA,
				TicketStatus.ABERTO,
				null,
				SOLICITANTE_ID,
				null,
				"Chamado criado."
		));

		assertThat(messages).hasSize(2);
		assertThat(messages).extracting(EmailMessage::to)
				.containsExactlyInAnyOrder("ana@fadex.org.br", "bruno@fadex.org.br");
		assertThat(messages.getFirst().subject()).startsWith("[ALTA]");
		assertThat(messages.getFirst().text()).contains("prioridade ALTA");
		assertThat(messages.getFirst().html()).contains("Chamado aberto com prioridade ALTA");
	}

	@Test
	void chamadoAbertoComPrioridadeAltaPeloProprioAdminNaoDeveVoltarParaEle() {
		when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(
				admin("Ana Admin", "ana@fadex.org.br", SOLICITANTE_ID),
				admin("Bruno Admin", "bruno@fadex.org.br", OUTRO_ADMIN_ID)
		));

		List<EmailMessage> messages = composer.compose(event(
				TicketNotificationType.CHAMADO_CRIADO,
				TicketPriority.ALTA,
				TicketStatus.ABERTO,
				null,
				SOLICITANTE_ID,
				null,
				"Chamado criado."
		));

		assertThat(messages).extracting(EmailMessage::to).containsExactly("bruno@fadex.org.br");
	}

	@Test
	void chamadoNormalCriadoNaoDeveGerarEmail() {
		List<EmailMessage> messages = composer.compose(event(
				TicketNotificationType.CHAMADO_CRIADO,
				TicketPriority.MEDIA,
				TicketStatus.ABERTO,
				null,
				SOLICITANTE_ID,
				null,
				"Chamado criado."
		));

		assertThat(messages).isEmpty();
	}

	@Test
	void responsavelAtribuidoDeveNotificarOResponsavel() {
		List<EmailMessage> messages = composer.compose(event(
				TicketNotificationType.RESPONSAVEL_ATRIBUIDO,
				TicketPriority.MEDIA,
				TicketStatus.EM_ANDAMENTO,
				responsavel(),
				OUTRO_ADMIN_ID,
				TicketPriority.MEDIA,
				"Responsavel atribuido: Ana Admin."
		));

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst().to()).isEqualTo("ana@fadex.org.br");
		assertThat(messages.getFirst().subject()).contains("Voce e o responsavel pelo chamado");
		assertThat(messages.getFirst().html()).contains("Voce foi definido como responsavel");
	}

	@Test
	void adminQueSeAutoAtribuiNaoDeveReceberEmail() {
		List<EmailMessage> messages = composer.compose(event(
				TicketNotificationType.RESPONSAVEL_ATRIBUIDO,
				TicketPriority.MEDIA,
				TicketStatus.EM_ANDAMENTO,
				responsavel(),
				RESPONSAVEL_ID,
				TicketPriority.MEDIA,
				"Responsavel atribuido: Ana Admin."
		));

		assertThat(messages).isEmpty();
	}

	@Test
	void statusAlteradoDeveNotificarOSolicitante() {
		List<EmailMessage> messages = composer.compose(event(
				TicketNotificationType.STATUS_ALTERADO,
				TicketPriority.MEDIA,
				TicketStatus.EM_ANDAMENTO,
				responsavel(),
				RESPONSAVEL_ID,
				TicketPriority.MEDIA,
				"Status alterado de Aberto para Em andamento."
		));

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst().to()).isEqualTo("maria@fadex.org.br");
		assertThat(messages.getFirst().text()).contains("Status alterado de Aberto para Em andamento.");
	}

	@Test
	void chamadoResolvidoDeveTerAssuntoProprio() {
		List<EmailMessage> resolvido = composer.compose(event(
				TicketNotificationType.STATUS_ALTERADO,
				TicketPriority.MEDIA,
				TicketStatus.RESOLVIDO,
				responsavel(),
				RESPONSAVEL_ID,
				TicketPriority.MEDIA,
				"Status alterado de Em andamento para Resolvido."
		));
		List<EmailMessage> fechado = composer.compose(event(
				TicketNotificationType.STATUS_ALTERADO,
				TicketPriority.MEDIA,
				TicketStatus.FECHADO,
				responsavel(),
				RESPONSAVEL_ID,
				TicketPriority.MEDIA,
				"Status alterado de Resolvido para Fechado."
		));

		assertThat(resolvido.getFirst().subject()).startsWith("Seu chamado foi resolvido");
		assertThat(fechado.getFirst().subject()).startsWith("Seu chamado foi fechado");
	}

	@Test
	void comentarioDeAdminDeveNotificarOSolicitante() {
		List<EmailMessage> messages = composer.compose(event(
				TicketNotificationType.COMENTARIO_ADICIONADO,
				TicketPriority.MEDIA,
				TicketStatus.EM_ANDAMENTO,
				responsavel(),
				RESPONSAVEL_ID,
				TicketPriority.MEDIA,
				"Ja estamos verificando."
		));

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst().to()).isEqualTo("maria@fadex.org.br");
		assertThat(messages.getFirst().text()).contains("Ana Admin comentou");
		assertThat(messages.getFirst().text()).contains("Ja estamos verificando.");
	}

	@Test
	void comentarioDoSolicitanteDeveNotificarOResponsavel() {
		List<EmailMessage> messages = composer.compose(event(
				TicketNotificationType.COMENTARIO_ADICIONADO,
				TicketPriority.MEDIA,
				TicketStatus.EM_ANDAMENTO,
				responsavel(),
				SOLICITANTE_ID,
				TicketPriority.MEDIA,
				"Continua com erro."
		));

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst().to()).isEqualTo("ana@fadex.org.br");
		assertThat(messages.getFirst().text()).contains("Maria Solicitante comentou");
	}

	@Test
	void comentarioDoSolicitanteEmChamadoSemResponsavelNaoDeveGerarEmail() {
		List<EmailMessage> messages = composer.compose(event(
				TicketNotificationType.COMENTARIO_ADICIONADO,
				TicketPriority.MEDIA,
				TicketStatus.ABERTO,
				null,
				SOLICITANTE_ID,
				TicketPriority.MEDIA,
				"Alguem pode olhar?"
		));

		assertThat(messages).isEmpty();
	}

	@Test
	void remocaoDeResponsavelNaoDeveGerarEmail() {
		List<EmailMessage> messages = composer.compose(event(
				TicketNotificationType.RESPONSAVEL_REMOVIDO,
				TicketPriority.MEDIA,
				TicketStatus.ABERTO,
				null,
				RESPONSAVEL_ID,
				TicketPriority.MEDIA,
				"Atribuicao removida de Ana Admin."
		));

		assertThat(messages).isEmpty();
	}

	@Test
	void reclassificacaoParaAltaDeveAlertarOsAdmins() {
		when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(
				admin("Ana Admin", "ana@fadex.org.br", RESPONSAVEL_ID)
		));

		List<EmailMessage> messages = composer.compose(event(
				TicketNotificationType.CLASSIFICACAO_ATUALIZADA,
				TicketPriority.ALTA,
				TicketStatus.ABERTO,
				null,
				null,
				TicketPriority.BAIXA,
				"Classificacao atualizada."
		));

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst().to()).isEqualTo("ana@fadex.org.br");
		assertThat(messages.getFirst().subject()).startsWith("[ALTA]");
	}

	@Test
	void falhaDeJobDeIaDeveNotificarOsAdmins() {
		when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(
				admin("Ana Admin", "ana@fadex.org.br", RESPONSAVEL_ID)
		));

		List<EmailMessage> messages = composer.composeAiJobFailure("timeout ao chamar o modelo local");

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst().subject()).isEqualTo("Job de IA falhou");
		assertThat(messages.getFirst().text()).contains("timeout ao chamar o modelo local");
		assertThat(messages.getFirst().html()).contains("timeout ao chamar o modelo local");
	}

	@Test
	void deveComporEmailDeSenhaProvisoria() {
		EmailMessage message = composer.compose(new UserCreatedNotificationEvent(
				SOLICITANTE_ID,
				"Maria Solicitante",
				"maria@fadex.org.br",
				"SenhaProvisoria123"
		));

		assertThat(message.to()).isEqualTo("maria@fadex.org.br");
		assertThat(message.subject()).isEqualTo("Acesso provisorio ao Fadex Helpdesk");
		assertThat(message.text()).contains("SenhaProvisoria123");
		assertThat(message.html()).contains("SenhaProvisoria123");
	}

	@Test
	void deveEscaparTituloDeChamadoNoHtml() {
		List<EmailMessage> messages = composer.compose(new TicketNotificationEvent(
				TicketNotificationType.RESPONSAVEL_ATRIBUIDO,
				new TicketMinDto(
						UUID.fromString("44444444-4444-4444-4444-444444444444"),
						"<script>alert('x')</script>",
						TicketCategory.SISTEMAS,
						TicketPriority.MEDIA,
						TicketStatus.EM_ANDAMENTO,
						ClassificationOrigin.PENDENTE,
						new UserMinDto(SOLICITANTE_ID, "Maria Solicitante"),
						new UserMinDto(RESPONSAVEL_ID, "Ana Admin"),
						LocalDateTime.now(),
						LocalDateTime.now()
				),
				new NotificationRecipient(SOLICITANTE_ID, "Maria Solicitante", "maria@fadex.org.br"),
				responsavel(),
				OUTRO_ADMIN_ID,
				TicketPriority.MEDIA,
				"Responsavel atribuido."
		));

		assertThat(messages.getFirst().html()).doesNotContain("<script>alert");
		assertThat(messages.getFirst().html()).contains("&lt;script&gt;");
	}

	private TicketNotificationEvent event(
			TicketNotificationType type,
			TicketPriority priority,
			TicketStatus status,
			NotificationRecipient assignee,
			UUID actorId,
			TicketPriority previousPriority,
			String detail
	) {
		TicketMinDto ticket = new TicketMinDto(
				UUID.fromString("44444444-4444-4444-4444-444444444444"),
				"Erro ao acessar sistema",
				TicketCategory.SISTEMAS,
				priority,
				status,
				ClassificationOrigin.PENDENTE,
				new UserMinDto(SOLICITANTE_ID, "Maria Solicitante"),
				assignee == null ? null : new UserMinDto(assignee.id(), assignee.name()),
				assignee == null ? null : LocalDateTime.now(),
				LocalDateTime.now()
		);

		return new TicketNotificationEvent(
				type,
				ticket,
				new NotificationRecipient(SOLICITANTE_ID, "Maria Solicitante", "maria@fadex.org.br"),
				assignee,
				actorId,
				previousPriority,
				detail
		);
	}

	private NotificationRecipient responsavel() {
		return new NotificationRecipient(RESPONSAVEL_ID, "Ana Admin", "ana@fadex.org.br");
	}

	private User admin(String name, String email, UUID id) {
		User user = new User(name, email, "hash", Role.ADMIN, false);
		ReflectionTestUtils.setField(user, "id", id);

		return user;
	}
}
