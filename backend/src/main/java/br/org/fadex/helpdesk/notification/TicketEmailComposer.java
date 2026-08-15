package br.org.fadex.helpdesk.notification;

import br.org.fadex.helpdesk.mail.EmailMessage;
import br.org.fadex.helpdesk.mail.EmailTemplateRenderer;
import br.org.fadex.helpdesk.model.enums.Role;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.ticket.TicketMinDto;
import br.org.fadex.helpdesk.model.user.User;
import br.org.fadex.helpdesk.notification.event.NotificationRecipient;
import br.org.fadex.helpdesk.notification.event.TicketNotificationEvent;
import br.org.fadex.helpdesk.notification.event.UserCreatedNotificationEvent;
import br.org.fadex.helpdesk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Traduz evento de dominio em mensagens de e-mail.
 *
 * Duas regras valem mais que a matriz de gatilhos:
 *
 * 1. Nunca notificar quem causou a acao — sem isso o ADMIN recebe e-mail do proprio comentario.
 *    A regra e de e-mail; no SSE o solicitante continua recebendo o proprio chamado.
 * 2. Criacao de chamado com prioridade normal nao gera e-mail. Sao poucos admins, e todo chamado
 *    virando e-mail treina as pessoas a ignorar a caixa.
 */
@Component
public class TicketEmailComposer {

	private static final String ACAO_ROTULO_CHAMADO = "Abrir chamado";
	private static final String ACAO_ROTULO_JOBS = "Abrir painel de jobs";
	private static final String ACAO_ROTULO_LOGIN = "Acessar o sistema";

	private final EmailTemplateRenderer templateRenderer;
	private final UserRepository userRepository;
	private final String frontendBaseUrl;

	public TicketEmailComposer(
			EmailTemplateRenderer templateRenderer,
			UserRepository userRepository,
			@Value("${app.frontend.base-url}") String frontendBaseUrl
	) {
		this.templateRenderer = templateRenderer;
		this.userRepository = userRepository;
		this.frontendBaseUrl = frontendBaseUrl;
	}

	public List<EmailMessage> compose(TicketNotificationEvent event) {
		if (event.becameHighPriority()) {
			return composeHighPriority(event);
		}

		return switch (event.type()) {
			case RESPONSAVEL_ATRIBUIDO -> composeAssigned(event);
			case STATUS_ALTERADO -> composeStatusChanged(event);
			case COMENTARIO_ADICIONADO -> composeComment(event);
			case CHAMADO_CRIADO, RESPONSAVEL_REMOVIDO, CLASSIFICACAO_ATUALIZADA -> List.of();
		};
	}

	public EmailMessage compose(UserCreatedNotificationEvent event) {
		Map<String, Object> variables = new HashMap<>();
		variables.put("titulo", "Seu acesso ao Fadex Helpdesk");
		variables.put("destinatarioNome", event.name());
		variables.put("senha", event.temporaryPassword());
		variables.put("acaoUrl", frontendBaseUrl + "/login");
		variables.put("acaoRotulo", ACAO_ROTULO_LOGIN);

		String text = """
				Ola, %s. Sua conta no Fadex Helpdesk foi criada.

				Senha provisoria: %s

				Use a senha no primeiro acesso; o sistema pede a troca em seguida.
				Acesse: %s
				""".formatted(event.name(), event.temporaryPassword(), frontendBaseUrl + "/login");

		return new EmailMessage(
				event.email(),
				"Acesso provisorio ao Fadex Helpdesk",
				text,
				templateRenderer.render("senha-provisoria", variables)
		);
	}

	public List<EmailMessage> composeAiJobFailure(String detail) {
		Map<String, Object> variables = new HashMap<>();
		variables.put("titulo", "Job de IA falhou");
		variables.put("detalhe", detail);
		variables.put("acaoUrl", frontendBaseUrl + "/admin/jobs");
		variables.put("acaoRotulo", ACAO_ROTULO_JOBS);

		String html = templateRenderer.render("job-ia-falhou", variables);
		String text = """
				Um job da triagem por IA falhou e precisa de atencao da operacao.

				%s

				Painel de jobs: %s
				""".formatted(detail, frontendBaseUrl + "/admin/jobs");

		List<EmailMessage> messages = new ArrayList<>();

		for (NotificationRecipient admin : findAdmins(null)) {
			messages.add(new EmailMessage(admin.email(), "Job de IA falhou", text, html));
		}

		return messages;
	}

	private List<EmailMessage> composeHighPriority(TicketNotificationEvent event) {
		TicketMinDto ticket = event.ticket();
		Map<String, Object> variables = ticketVariables(event);
		variables.put("titulo", "Chamado aberto com prioridade ALTA");
		variables.put("solicitanteNome", event.requester().name());

		String subject = "[ALTA] Chamado com prioridade alta: " + ticket.title();
		String text = """
				Um chamado com prioridade ALTA foi aberto por %s e precisa de atendimento.

				Chamado: %s
				Categoria: %s
				Prioridade: %s
				Status: %s

				Abrir chamado: %s
				""".formatted(
				event.requester().name(),
				ticket.title(),
				ticket.category().getLabel(),
				ticket.priority().getLabel(),
				ticket.status().getLabel(),
				ticketUrl(ticket)
		);
		String html = templateRenderer.render("chamado-alta-prioridade", variables);

		List<EmailMessage> messages = new ArrayList<>();

		for (NotificationRecipient admin : findAdmins(event.actorId())) {
			messages.add(new EmailMessage(admin.email(), subject, text, html));
		}

		return messages;
	}

	private List<EmailMessage> composeAssigned(TicketNotificationEvent event) {
		NotificationRecipient assignee = event.assignee();

		if (assignee == null || !assignee.isNot(event.actorId())) {
			return List.of();
		}

		TicketMinDto ticket = event.ticket();
		Map<String, Object> variables = ticketVariables(event);
		variables.put("titulo", "Voce foi definido como responsavel");
		variables.put("destinatarioNome", assignee.name());

		String text = """
				Ola, %s. O chamado abaixo passou a ser sua responsabilidade.

				Chamado: %s
				Categoria: %s
				Prioridade: %s
				Status: %s

				Abrir chamado: %s
				""".formatted(
				assignee.name(),
				ticket.title(),
				ticket.category().getLabel(),
				ticket.priority().getLabel(),
				ticket.status().getLabel(),
				ticketUrl(ticket)
		);

		return List.of(new EmailMessage(
				assignee.email(),
				"Voce e o responsavel pelo chamado: " + ticket.title(),
				text,
				templateRenderer.render("responsavel-atribuido", variables)
		));
	}

	/**
	 * O responsavel so entra na lista quando o chamado e cancelado: quem esta atendendo precisa
	 * saber que o chamado morreu, sob pena de continuar trabalhando nele. Nas demais mudancas de
	 * status o destinatario segue sendo apenas o solicitante, como ja estava publicado no contrato.
	 */
	private List<EmailMessage> composeStatusChanged(TicketNotificationEvent event) {
		TicketMinDto ticket = event.ticket();
		String titulo = switch (ticket.status()) {
			case RESOLVIDO -> "Seu chamado foi resolvido";
			case FECHADO -> "Seu chamado foi fechado";
			case CANCELADO -> "Seu chamado foi cancelado";
			default -> "O status do seu chamado mudou";
		};
		String detalhe = event.detail() == null
				? "O status do chamado agora e " + ticket.status().getLabel() + "."
				: event.detail();

		List<NotificationRecipient> recipients = new ArrayList<>();
		recipients.add(event.requester());

		if (ticket.status() == TicketStatus.CANCELADO && event.assignee() != null) {
			recipients.add(event.assignee());
		}

		List<EmailMessage> messages = new ArrayList<>();

		for (NotificationRecipient recipient : recipients) {
			if (!recipient.isNot(event.actorId())) {
				continue;
			}

			Map<String, Object> variables = ticketVariables(event);
			variables.put("titulo", titulo);
			variables.put("destinatarioNome", recipient.name());
			variables.put("detalhe", detalhe);

			String text = """
					Ola, %s. %s

					Chamado: %s
					Categoria: %s
					Prioridade: %s
					Status: %s

					Abrir chamado: %s
					""".formatted(
					recipient.name(),
					detalhe,
					ticket.title(),
					ticket.category().getLabel(),
					ticket.priority().getLabel(),
					ticket.status().getLabel(),
					ticketUrl(ticket)
			);

			messages.add(new EmailMessage(
					recipient.email(),
					titulo + ": " + ticket.title(),
					text,
					templateRenderer.render("status-alterado", variables)
			));
		}

		return messages;
	}

	/**
	 * Contraparte do autor: comentario do solicitante vai para o responsavel, comentario de quem
	 * atende vai para o solicitante.
	 *
	 * Chamado sem responsavel comentado pelo solicitante nao gera e-mail — a matriz nomeia o
	 * responsavel como contraparte, e nao ha a quem entregar.
	 */
	private List<EmailMessage> composeComment(TicketNotificationEvent event) {
		boolean authoredByRequester = !event.requester().isNot(event.actorId());
		NotificationRecipient recipient = authoredByRequester ? event.assignee() : event.requester();

		if (recipient == null || !recipient.isNot(event.actorId())) {
			return List.of();
		}

		TicketMinDto ticket = event.ticket();
		String autorNome = authoredByRequester
				? event.requester().name()
				: nomeDoAutor(event);

		Map<String, Object> variables = ticketVariables(event);
		variables.put("titulo", "Novo comentario no chamado");
		variables.put("destinatarioNome", recipient.name());
		variables.put("autorNome", autorNome);
		variables.put("comentario", event.detail());

		String text = """
				Ola, %s. %s comentou no chamado "%s".

				%s

				Abrir chamado: %s
				""".formatted(
				recipient.name(),
				autorNome,
				ticket.title(),
				event.detail(),
				ticketUrl(ticket)
		);

		return List.of(new EmailMessage(
				recipient.email(),
				"Novo comentario no chamado: " + ticket.title(),
				text,
				templateRenderer.render("comentario-adicionado", variables)
		));
	}

	private String nomeDoAutor(TicketNotificationEvent event) {
		NotificationRecipient assignee = event.assignee();

		if (assignee != null && !assignee.isNot(event.actorId())) {
			return assignee.name();
		}

		return userRepository.findById(event.actorId())
				.map(User::getName)
				.orElse("Equipe de atendimento");
	}

	private Map<String, Object> ticketVariables(TicketNotificationEvent event) {
		TicketMinDto ticket = event.ticket();
		Map<String, Object> variables = new HashMap<>();

		variables.put("chamadoTitulo", ticket.title());
		variables.put("categoria", ticket.category().getLabel());
		variables.put("prioridade", ticket.priority().getLabel());
		variables.put("status", ticket.status().getLabel());
		variables.put("acaoUrl", ticketUrl(ticket));
		variables.put("acaoRotulo", ACAO_ROTULO_CHAMADO);

		return variables;
	}

	private String ticketUrl(TicketMinDto ticket) {
		return frontendBaseUrl + "/tickets/" + ticket.id();
	}

	private List<NotificationRecipient> findAdmins(UUID excludedUserId) {
		List<NotificationRecipient> admins = new ArrayList<>();

		for (User admin : userRepository.findByRole(Role.ADMIN)) {
			NotificationRecipient recipient = NotificationRecipient.of(admin);

			if (recipient.isNot(excludedUserId)) {
				admins.add(recipient);
			}
		}

		return admins;
	}
}
