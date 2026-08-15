package br.org.fadex.helpdesk.mail;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * Renderiza o HTML dos e-mails a partir dos templates de {@code resources/templates/email}.
 *
 * Todo conteudo vindo do banco entra por {@code th:text}, que escapa por padrao. Nenhum template
 * usa {@code th:utext}: titulo de chamado e texto de comentario sao texto livre digitado por gente.
 */
@Component
public class EmailTemplateRenderer {

	private static final String TEMPLATE_PREFIX = "email/";

	private final TemplateEngine templateEngine;

	public EmailTemplateRenderer(TemplateEngine templateEngine) {
		this.templateEngine = templateEngine;
	}

	public String render(String templateName, Map<String, Object> variables) {
		Context context = new Context();
		context.setVariables(variables);

		return templateEngine.process(TEMPLATE_PREFIX + templateName, context);
	}
}
