package br.org.fadex.helpdesk.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateRendererTest {

	private EmailTemplateRenderer renderer;

	@BeforeEach
	void setUp() {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.HTML);
		resolver.setCharacterEncoding("UTF-8");

		TemplateEngine templateEngine = new SpringTemplateEngine();
		templateEngine.setTemplateResolver(resolver);

		renderer = new EmailTemplateRenderer(templateEngine);
	}

	@Test
	void deveRenderizarLayoutCompartilhadoComConteudoDaNotificacao() {
		String html = renderer.render("responsavel-atribuido", variaveis(new HashMap<>()));

		assertThat(html).contains("Fadex Helpdesk");
		assertThat(html).contains("Voce foi definido como responsavel");
		assertThat(html).contains("Maria Admin");
		assertThat(html).contains("Erro ao acessar sistema");
		assertThat(html).contains("http://localhost:3000/chamados/1");
		assertThat(html).contains("Mensagem automatica do Fadex Helpdesk.");
	}

	@Test
	void deveEscaparConteudoVindoDoBanco() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("chamadoTitulo", "<script>alert('x')</script>");

		String html = renderer.render("responsavel-atribuido", variaveis(variables));

		assertThat(html).doesNotContain("<script>alert");
		assertThat(html).contains("&lt;script&gt;");
	}

	@Test
	void deveEscaparTextoDeComentario() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("autorNome", "Joao");
		variables.put("comentario", "Testei com <b>negrito</b> & aspas \"assim\".");

		String html = renderer.render("comentario-adicionado", variaveis(variables));

		assertThat(html).doesNotContain("<b>negrito</b>");
		assertThat(html).contains("&lt;b&gt;negrito&lt;/b&gt;");
		assertThat(html).contains("&amp;");
	}

	@Test
	void deveRenderizarSenhaProvisoria() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("senha", "Abc12345");

		String html = renderer.render("senha-provisoria", variaveis(variables));

		assertThat(html).contains("Abc12345");
		assertThat(html).contains("Seu acesso ao Fadex Helpdesk");
	}

	private Map<String, Object> variaveis(Map<String, Object> overrides) {
		Map<String, Object> variables = new HashMap<>();
		variables.put("titulo", "Chamado atualizado");
		variables.put("destinatarioNome", "Maria Admin");
		variables.put("solicitanteNome", "Joao Solicitante");
		variables.put("chamadoTitulo", "Erro ao acessar sistema");
		variables.put("categoria", "Sistemas");
		variables.put("prioridade", "Alta");
		variables.put("status", "Em andamento");
		variables.put("detalhe", "O status do chamado mudou.");
		variables.put("acaoUrl", "http://localhost:3000/chamados/1");
		variables.put("acaoRotulo", "Abrir chamado");
		variables.putAll(overrides);

		return variables;
	}
}
