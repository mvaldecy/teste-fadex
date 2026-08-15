package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Roda os indicadores sobre a base semeada, e nao sobre projecoes montadas a mao.
 *
 * Cobre o que teste de unidade com mock nao cobre: a projecao JPQL do {@code IndicatorRepository}
 * casa com o mapeamento da entidade, o seed grava mesmo {@code classification_reviewed_at}, e a
 * concordancia admin x IA sai com denominador maior que zero e percentual que nao e 100 — se o seed
 * so tivesse aceites, o numero nao mediria nada.
 *
 * A assercao de percentual menor que 100 depende do conteudo do seed: exige pelo menos um chamado
 * com origem MANUAL cuja sugestao diverge da classificacao vigente. E de proposito que ela dependa
 * disso — e essa divergencia que torna a metrica informativa. Se este teste falhar depois de uma
 * mudanca em {@code DevTicketSeeder}, o defeito provavel esta no seed, nao no calculo.
 */
@SpringBootTest(properties = {
		"app.seed.enabled=true",
		// Base propria: o seed escreve fora de transacao de teste e ficaria visivel para os demais
		// testes, que compartilham a instancia H2 em memoria do perfil de teste.
		"spring.datasource.url=jdbc:h2:mem:fadex_helpdesk_seed;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@ActiveProfiles("test")
class IndicatorSeedIntegrationTest {

	@Autowired
	private IndicatorService indicatorService;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void deveCalcularIndicadoresSobreABaseSemeada() {
		IndicatorsDto indicators = indicatorService.getIndicators();

		assertThat(indicators.generatedAt()).isNotNull();
		assertThat(indicators.overview().total()).isPositive();
		assertThat(indicators.overview().byStatus()).isNotEmpty();
		assertThat(indicators.durations().closure().overall().sampleSize()).isPositive();
	}

	@Test
	void deveMedirConcordanciaAdminIaComAmostraReal() {
		AgreementRateDto agreementRate = indicatorService.getIndicators().ai().agreementRate();

		assertThat(agreementRate.evaluated()).isPositive();
		assertThat(agreementRate.percentage()).isNotNull();
		assertThat(agreementRate.percentage()).isLessThan(100.0);
	}

	@Test
	void deveManterChamadosPendentesForaDoDenominadorDaConcordancia() {
		AiIndicatorsDto ai = indicatorService.getIndicators().ai();

		// As cobaias de triagem existem no seed e sao visiveis na distribuicao de origem...
		assertThat(ai.originDistribution().get(ClassificationOrigin.PENDENTE)).isGreaterThanOrEqualTo(4L);

		// ...mas nao entram na concordancia: sem sugestao registrada nao ha o que aceitar. Se
		// entrassem, a taxa cairia sozinha a cada chamado novo aberto e deixaria de medir o acerto
		// da IA.
		long comSugestaoERevisado = ai.agreementRate().evaluated();
		assertThat(comSugestaoERevisado).isPositive();
		assertThat(ai.agreementRate().agreed()).isLessThanOrEqualTo(comSugestaoERevisado);
	}

	/**
	 * O histograma existe para o grafico de distribuicao: tempo de atendimento tem parede em zero e
	 * cauda longa, e so a distribuicao mostra isso — media e mediana lado a lado nao mostram.
	 *
	 * A assercao que importa e a soma: se as faixas nao somarem o {@code sampleSize}, o grafico e o
	 * numero ao lado dele contam historias diferentes.
	 */
	@Test
	void deveMontarHistogramaNosTresBlocosDeDuracaoSobreABaseSemeada() {
		DurationIndicatorsDto durations = indicatorService.getIndicators().durations();

		for (DurationStatsDto overall : List.of(
				durations.closure().overall(),
				durations.firstResponse().overall(),
				durations.assignment().overall()
		)) {
			assertThat(overall.histogram()).hasSize(6);
			assertThat(overall.histogram()).extracting(DurationBucketDto::fromHours)
					.containsExactly(0, 4, 8, 24, 48, 96);
			assertThat(overall.histogram().getLast().toHours()).isNull();

			long total = overall.histogram().stream().mapToLong(DurationBucketDto::count).sum();
			assertThat(total).isEqualTo(overall.sampleSize());
		}

		assertThat(durations.closure().overall().sampleSize()).isPositive();
	}

	/**
	 * Contrato de serializacao combinado com o frontend: a chave {@code histogram} existe no recorte
	 * geral e **nao existe** nos recortes por prioridade e por categoria. Ausencia de chave, e nao
	 * lista vazia, para o front nao desenhar um grafico de amostra que nao comporta um.
	 */
	@Test
	void deveOmitirOHistogramaNosRecortesPorPrioridadeECategoria() {
		JsonNode durations = objectMapper.valueToTree(indicatorService.getIndicators().durations());
		JsonNode closure = durations.path("closure");

		assertThat(closure.path("overall").has("histogram")).isTrue();
		assertThat(closure.path("byPriority").isEmpty()).isFalse();

		for (JsonNode stats : closure.path("byPriority")) {
			assertThat(stats.has("histogram")).isFalse();
		}

		for (JsonNode stats : closure.path("byCategory")) {
			assertThat(stats.has("histogram")).isFalse();
		}

		JsonNode bucket = closure.path("overall").path("histogram").get(0);
		assertThat(bucket.path("fromHours").asInt()).isZero();
		assertThat(bucket.path("toHours").asInt()).isEqualTo(4);
		assertThat(bucket.has("count")).isTrue();
	}

	@Test
	void deveCalcularConfiancaMediaSobreABaseSemeada() {
		Double averageConfidence = indicatorService.getIndicators().ai().averageConfidence();

		assertThat(averageConfidence).isNotNull();
		assertThat(averageConfidence).isBetween(0.0, 1.0);
	}
}
