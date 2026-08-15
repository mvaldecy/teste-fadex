package br.org.fadex.helpdesk.config;

import br.org.fadex.helpdesk.ai.job.AiJobStatus;
import br.org.fadex.helpdesk.ai.job.AiJobType;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que uma instalacao nova nasce com os jobs de IA dos chamados semeados enfileirados.
 *
 * Antes disso, o seed escrevia os chamados por SQL direto e nenhum deles gerava embedding: a
 * deteccao de duplicados ficava sem nada para comparar e {@code ticket_links} nascia — e continuava
 * — vazia. O teste roda sobre a base semeada de verdade, porque o defeito era exatamente a distancia
 * entre o seed e o caminho de dominio que enfileira os jobs.
 */
@SpringBootTest(properties = {
		"app.seed.enabled=true",
		// Base propria: o seed escreve fora de transacao de teste e ficaria visivel para os demais
		// testes, que compartilham a instancia H2 em memoria do perfil de teste.
		"spring.datasource.url=jdbc:h2:mem:fadex_helpdesk_seed_jobs;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@ActiveProfiles("test")
class DevTicketSeederAiJobsTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	@Qualifier("seedTickets")
	private CommandLineRunner seedTickets;

	@Test
	void deveEnfileirarEmbeddingParaTodoChamadoSemeado() {
		long tickets = count("select count(*) from tickets");
		long embeddingJobs = count(
				"select count(*) from ai_jobs where type = '" + AiJobType.EMBEDDING.name() + "'"
		);

		assertThat(tickets).isPositive();
		assertThat(embeddingJobs).isEqualTo(tickets);
	}

	@Test
	void deveEnfileirarClassificacaoApenasParaChamadoPendente() {
		long pendentes = count(
				"select count(*) from tickets where classification_origin = '"
						+ ClassificationOrigin.PENDENTE.name() + "'"
		);
		long classificationJobs = count(
				"select count(*) from ai_jobs where type = '" + AiJobType.CLASSIFICATION.name() + "'"
		);

		assertThat(pendentes).isPositive();
		assertThat(classificationJobs).isEqualTo(pendentes);
	}

	@Test
	void naoDeveEnfileirarClassificacaoParaChamadoJaClassificado() {
		// A sugestao da IA gravada no seed e o denominador da concordancia admin x IA. Reclassificar
		// esses chamados sobrescreveria a sugestao com o valor que passaria a valer no chamado, e a
		// taxa iria a 100% sem medir nada.
		long jobsDeChamadoClassificado = count(
				"""
				select count(*)
				from ai_jobs job
				join tickets ticket on ticket.id = job.ticket_id
				where job.type = '""" + AiJobType.CLASSIFICATION.name() + "'"
						+ " and ticket.classification_origin <> '" + ClassificationOrigin.PENDENTE.name() + "'"
		);

		assertThat(jobsDeChamadoClassificado).isZero();
	}

	@Test
	void deveEnfileirarTodoJobComoPendente() {
		long naoPendentes = count(
				"select count(*) from ai_jobs where status <> '" + AiJobStatus.PENDING.name() + "'"
		);

		assertThat(naoPendentes).isZero();
	}

	@Test
	void deveSerIdempotenteQuandoOSeedRodaDeNovo() throws Exception {
		long antes = count("select count(*) from ai_jobs");

		seedTickets.run();

		long depois = count("select count(*) from ai_jobs");

		assertThat(antes).isPositive();
		assertThat(depois).isEqualTo(antes);
	}

	private long count(String sql) {
		Long count = jdbcTemplate.queryForObject(sql, Long.class);

		return count == null ? 0 : count;
	}
}
