package br.org.fadex.helpdesk.ai.indicator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Roda os indicadores sobre a base semeada, e nao sobre projecoes montadas a mao.
 *
 * Cobre o que teste de unidade com mock nao cobre: a projecao JPQL do {@code IndicatorRepository}
 * casa com o mapeamento da entidade, o seed grava mesmo {@code classification_reviewed_at}, e a
 * concordancia admin x IA sai com denominador maior que zero e percentual que nao e 100 — se o seed
 * so tivesse aceites, o numero nao mediria nada.
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
	void deveCalcularConfiancaMediaSobreABaseSemeada() {
		Double averageConfidence = indicatorService.getIndicators().ai().averageConfidence();

		assertThat(averageConfidence).isNotNull();
		assertThat(averageConfidence).isBetween(0.0, 1.0);
	}
}
