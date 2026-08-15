package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.ai.job.AiJobRepository;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.repository.TicketLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IndicatorServiceTest {

	private static final ZoneId ZONE = ZoneId.of("America/Fortaleza");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 12, 0);

	private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

	@Mock
	private IndicatorRepository indicatorRepository;

	@Mock
	private AiJobRepository aiJobRepository;

	@Mock
	private TicketLinkRepository ticketLinkRepository;

	@Test
	void deveContarChamadosPorStatusPrioridadeECategoria() {
		givenProjections(
				aberto(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(2)),
				aberto(TicketPriority.ALTA, TicketCategory.SISTEMAS, NOW.minusHours(3)),
				fechado(TicketPriority.BAIXA, TicketCategory.ACESSO, NOW.minusHours(10), NOW.minusHours(5))
		);

		OverviewIndicatorsDto overview = service().getIndicators().overview();

		assertThat(overview.total()).isEqualTo(3);
		assertThat(overview.byStatus()).containsEntry(TicketStatus.ABERTO, 2L);
		assertThat(overview.byPriority()).containsEntry(TicketPriority.ALTA, 2L);
		assertThat(overview.byCategory()).containsEntry(TicketCategory.ACESSO, 2L);
	}

	@Test
	void deveOmitirGrupoVazioEmVezDeZerar() {
		givenProjections(aberto(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(2)));

		OverviewIndicatorsDto overview = service().getIndicators().overview();

		assertThat(overview.byCategory()).doesNotContainKey(TicketCategory.FINANCEIRO);
		assertThat(overview.byStatus()).doesNotContainKey(TicketStatus.FECHADO);
	}

	@Test
	void deveContarAltaPrioridadeEmAberto() {
		givenProjections(
				aberto(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(2)),
				aberto(TicketPriority.ALTA, TicketCategory.SISTEMAS, NOW.minusHours(3)),
				fechado(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(10), NOW.minusHours(9))
		);

		assertThat(service().getIndicators().overview().openHighPriority()).isEqualTo(2);
	}

	@Test
	void deveContarAberturasEFechamentosDoDiaEDaSemana() {
		givenProjections(
				aberto(TicketPriority.MEDIA, TicketCategory.ACESSO, NOW.minusHours(2)),
				fechado(TicketPriority.MEDIA, TicketCategory.ACESSO, NOW.minusDays(1), NOW.minusHours(1)),
				aberto(TicketPriority.MEDIA, TicketCategory.ACESSO, NOW.minusDays(20))
		);

		OverviewIndicatorsDto overview = service().getIndicators().overview();

		assertThat(overview.openedToday()).isEqualTo(1);
		assertThat(overview.closedToday()).isEqualTo(1);
		assertThat(overview.openedThisWeek()).isEqualTo(2);
		assertThat(overview.closedThisWeek()).isEqualTo(1);
	}

	@Test
	void deveClassificarAgingDoBacklogEmTresBuckets() {
		givenProjections(
				aberto(TicketPriority.BAIXA, TicketCategory.ACESSO, NOW.minusHours(6)),
				aberto(TicketPriority.BAIXA, TicketCategory.ACESSO, NOW.minusHours(40)),
				aberto(TicketPriority.BAIXA, TicketCategory.ACESSO, NOW.minusHours(200))
		);

		BacklogAgingDto aging = service().getIndicators().durations().backlogAging();

		assertThat(aging.upToOneDay()).isEqualTo(1);
		assertThat(aging.oneToThreeDays()).isEqualTo(1);
		assertThat(aging.overThreeDays()).isEqualTo(1);
	}

	@Test
	void deveCalcularIdadeDoChamadoAbertoMaisAntigo() {
		givenProjections(
				aberto(TicketPriority.BAIXA, TicketCategory.ACESSO, NOW.minusHours(6)),
				aberto(TicketPriority.BAIXA, TicketCategory.ACESSO, NOW.minusHours(200))
		);

		assertThat(service().getIndicators().durations().oldestOpenTicketHours()).isEqualTo(200.0);
	}

	@Test
	void deveDevolverIdadeNulaQuandoNaoHaChamadoAberto() {
		givenProjections(fechado(TicketPriority.BAIXA, TicketCategory.ACESSO, NOW.minusHours(6), NOW.minusHours(1)));

		assertThat(service().getIndicators().durations().oldestOpenTicketHours()).isNull();
	}

	@Test
	void deveCalcularTempoDeFechamentoGeralEPorPrioridade() {
		givenProjections(
				fechado(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(10), NOW.minusHours(8)),
				fechado(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(20), NOW.minusHours(16)),
				aberto(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(1))
		);

		DurationGroupDto closure = service().getIndicators().durations().closure();

		assertThat(closure.overall().sampleSize()).isEqualTo(2);
		assertThat(closure.overall().averageHours()).isEqualTo(3.0);
		assertThat(closure.overall().medianHours()).isEqualTo(3.0);
		assertThat(closure.byPriority().get(TicketPriority.ALTA).sampleSize()).isEqualTo(2);
	}

	@Test
	void deveDevolverEstatisticaVaziaQuandoNaoHaAmostra() {
		givenProjections(aberto(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(1)));

		DurationStatsDto closure = service().getIndicators().durations().closure().overall();

		assertThat(closure.sampleSize()).isZero();
		assertThat(closure.averageHours()).isNull();
		assertThat(closure.medianHours()).isNull();
		assertThat(closure.p90Hours()).isNull();
	}

	@Test
	void deveExcluirDoDenominadorDeSlaChamadoAbertoAindaDentroDoAlvo() {
		givenProjections(
				aberto(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(1)),
				fechado(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(10), NOW.minusHours(8))
		);

		SlaIndicatorsDto sla = service().getIndicators().durations().sla();

		assertThat(sla.overall().evaluated()).isEqualTo(1);
		assertThat(sla.overall().withinTarget()).isEqualTo(1);
		assertThat(sla.overall().percentage()).isEqualTo(100.0);
	}

	@Test
	void deveContarComoViolacaoChamadoAbertoQueJaEstourouOAlvo() {
		givenProjections(aberto(TicketPriority.ALTA, TicketCategory.ACESSO, NOW.minusHours(10)));

		SlaIndicatorsDto sla = service().getIndicators().durations().sla();

		assertThat(sla.overall().evaluated()).isEqualTo(1);
		assertThat(sla.overall().withinTarget()).isZero();
		assertThat(sla.byPriority().get(TicketPriority.ALTA).percentage()).isEqualTo(0.0);
	}

	@Test
	void deveEncerrarOCronometroDeSlaEmChamadoResolvidoAindaNaoFechado() {
		givenProjections(resolvido(TicketPriority.ALTA, NOW.minusHours(100), NOW.minusHours(98)));

		SlaIndicatorsDto sla = service().getIndicators().durations().sla();

		assertThat(sla.overall().evaluated()).isEqualTo(1);
		assertThat(sla.overall().withinTarget()).isEqualTo(1);
	}

	@Test
	void deveDevolverPercentualDeSlaNuloSemAmostra() {
		givenProjections(aberto(TicketPriority.BAIXA, TicketCategory.ACESSO, NOW.minusHours(1)));

		assertThat(service().getIndicators().durations().sla().overall().percentage()).isNull();
	}

	@Test
	void deveCalcularConcordanciaApenasSobreChamadosRevisados() {
		givenProjections(
				revisado(TicketCategory.ACESSO, TicketPriority.MEDIA, TicketCategory.ACESSO, TicketPriority.MEDIA),
				revisado(TicketCategory.RH, TicketPriority.ALTA, TicketCategory.ACESSO, TicketPriority.MEDIA),
				revisado(TicketCategory.RH, TicketPriority.BAIXA, TicketCategory.RH, TicketPriority.BAIXA),
				naoRevisado(TicketCategory.SISTEMAS, TicketPriority.ALTA, TicketCategory.SISTEMAS, TicketPriority.ALTA),
				semSugestao()
		);

		AgreementRateDto agreement = service().getIndicators().ai().agreementRate();

		assertThat(agreement.evaluated()).isEqualTo(3);
		assertThat(agreement.agreed()).isEqualTo(2);
		assertThat(agreement.percentage()).isEqualTo(66.7);
	}

	@Test
	void naoDeveContarChamadoNaoRevisadoComoAceite() {
		givenProjections(
				naoRevisado(TicketCategory.SISTEMAS, TicketPriority.ALTA, TicketCategory.SISTEMAS, TicketPriority.ALTA)
		);

		AgreementRateDto agreement = service().getIndicators().ai().agreementRate();

		assertThat(agreement.evaluated()).isZero();
		assertThat(agreement.agreed()).isZero();
		assertThat(agreement.percentage()).isNull();
	}

	@Test
	void deveIgnorarConfiancaNulaNaMedia() {
		givenProjections(
				comConfianca(0.8),
				comConfianca(0.6),
				comConfianca(null)
		);

		assertThat(service().getIndicators().ai().averageConfidence()).isEqualTo(0.7);
	}

	@Test
	void deveDevolverConfiancaMediaNulaQuandoNenhumChamadoTemSugestao() {
		givenProjections(semSugestao());

		assertThat(service().getIndicators().ai().averageConfidence()).isNull();
	}

	@Test
	void deveDistribuirOrigemDaClassificacao() {
		givenProjections(
				revisado(TicketCategory.ACESSO, TicketPriority.MEDIA, TicketCategory.ACESSO, TicketPriority.MEDIA),
				semSugestao()
		);

		assertThat(service().getIndicators().ai().originDistribution())
				.containsEntry(ClassificationOrigin.IA, 1L)
				.containsEntry(ClassificationOrigin.PENDENTE, 1L);
	}

	@Test
	void deveContarDuplicadosDetectados() {
		givenProjections(semSugestao());
		when(ticketLinkRepository.count()).thenReturn(3L);

		assertThat(service().getIndicators().ai().duplicatesDetected()).isEqualTo(3);
	}

	@Test
	void deveAgruparCargaAbertaPorResponsavel() {
		UUID ocupado = UUID.randomUUID();
		UUID tranquilo = UUID.randomUUID();
		givenProjections(
				abertoComResponsavel(ocupado, "Ana"),
				abertoComResponsavel(ocupado, "Ana"),
				abertoComResponsavel(ocupado, "Ana"),
				abertoComResponsavel(tranquilo, "Bruno")
		);

		WorkloadIndicatorsDto workload = service().getIndicators().workload();

		assertThat(workload.openByAssignee()).hasSize(2);
		assertThat(workload.openByAssignee().getFirst().openTickets()).isEqualTo(3);
		assertThat(workload.openByAssignee().getFirst().user().name()).isEqualTo("Ana");
	}

	@Test
	void deveLimitarTopSolicitantesACinco() {
		List<TicketIndicatorProjection> projections = new java.util.ArrayList<>();
		for (int index = 0; index < 7; index++) {
			projections.add(comSolicitante(UUID.randomUUID(), "Solicitante " + index));
		}
		when(indicatorRepository.findAllProjections()).thenReturn(projections);

		assertThat(service().getIndicators().workload().topRequesters()).hasSize(5);
	}

	@Test
	void deveMedirTempoDeFechamentoPorResponsavel() {
		UUID responsavel = UUID.randomUUID();
		givenProjections(
				fechadoComResponsavel(responsavel, "Ana", NOW.minusHours(10), NOW.minusHours(8)),
				fechadoComResponsavel(responsavel, "Ana", NOW.minusHours(20), NOW.minusHours(16))
		);

		List<AssigneeClosureDto> closureTime = service().getIndicators().workload().closureTimeByAssignee();

		assertThat(closureTime).hasSize(1);
		assertThat(closureTime.getFirst().sampleSize()).isEqualTo(2);
		assertThat(closureTime.getFirst().averageHours()).isEqualTo(3.0);
	}

	private IndicatorService service() {
		return new IndicatorService(indicatorRepository, aiJobRepository, ticketLinkRepository, clock);
	}

	private void givenProjections(TicketIndicatorProjection... projections) {
		when(indicatorRepository.findAllProjections()).thenReturn(List.of(projections));
	}

	private TicketIndicatorProjection aberto(
			TicketPriority priority,
			TicketCategory category,
			LocalDateTime createdAt
	) {
		return projection(TicketStatus.ABERTO, priority, category, ClassificationOrigin.PENDENTE,
				null, null, null, null, null, createdAt, null, null, null, null);
	}

	private TicketIndicatorProjection fechado(
			TicketPriority priority,
			TicketCategory category,
			LocalDateTime createdAt,
			LocalDateTime closedAt
	) {
		return projection(TicketStatus.FECHADO, priority, category, ClassificationOrigin.PENDENTE,
				null, null, null, null, null, createdAt, null, null, closedAt, null);
	}

	private TicketIndicatorProjection resolvido(
			TicketPriority priority,
			LocalDateTime createdAt,
			LocalDateTime resolvedAt
	) {
		return new TicketIndicatorProjection(
				UUID.randomUUID(),
				TicketStatus.RESOLVIDO,
				priority,
				TicketCategory.ACESSO,
				ClassificationOrigin.PENDENTE,
				null,
				null,
				null,
				UUID.randomUUID(),
				"Solicitante",
				null,
				null,
				createdAt,
				null,
				null,
				resolvedAt,
				null,
				null
		);
	}

	private TicketIndicatorProjection abertoComResponsavel(UUID assigneeId, String assigneeName) {
		return projection(TicketStatus.ABERTO, TicketPriority.MEDIA, TicketCategory.ACESSO,
				ClassificationOrigin.PENDENTE, null, null, null, null, null,
				NOW.minusHours(2), assigneeId, assigneeName, null, null);
	}

	private TicketIndicatorProjection fechadoComResponsavel(
			UUID assigneeId,
			String assigneeName,
			LocalDateTime createdAt,
			LocalDateTime closedAt
	) {
		return projection(TicketStatus.FECHADO, TicketPriority.MEDIA, TicketCategory.ACESSO,
				ClassificationOrigin.PENDENTE, null, null, null, null, null,
				createdAt, assigneeId, assigneeName, closedAt, null);
	}

	private TicketIndicatorProjection comSolicitante(UUID requesterId, String requesterName) {
		return projection(TicketStatus.ABERTO, TicketPriority.MEDIA, TicketCategory.ACESSO,
				ClassificationOrigin.PENDENTE, null, null, null, requesterId, requesterName,
				NOW.minusHours(2), null, null, null, null);
	}

	private TicketIndicatorProjection revisado(
			TicketCategory category,
			TicketPriority priority,
			TicketCategory suggestedCategory,
			TicketPriority suggestedPriority
	) {
		return projection(TicketStatus.ABERTO, priority, category, ClassificationOrigin.IA,
				suggestedCategory, suggestedPriority, 0.8, null, null,
				NOW.minusHours(2), null, null, null, NOW.minusHours(1));
	}

	private TicketIndicatorProjection naoRevisado(
			TicketCategory category,
			TicketPriority priority,
			TicketCategory suggestedCategory,
			TicketPriority suggestedPriority
	) {
		return projection(TicketStatus.ABERTO, priority, category, ClassificationOrigin.IA,
				suggestedCategory, suggestedPriority, 0.8, null, null,
				NOW.minusHours(2), null, null, null, null);
	}

	private TicketIndicatorProjection semSugestao() {
		return projection(TicketStatus.ABERTO, TicketPriority.MEDIA, TicketCategory.ACESSO,
				ClassificationOrigin.PENDENTE, null, null, null, null, null,
				NOW.minusHours(2), null, null, null, null);
	}

	private TicketIndicatorProjection comConfianca(Double confidence) {
		return projection(TicketStatus.ABERTO, TicketPriority.MEDIA, TicketCategory.ACESSO,
				ClassificationOrigin.IA, TicketCategory.ACESSO, TicketPriority.MEDIA, confidence, null, null,
				NOW.minusHours(2), null, null, null, null);
	}

	private TicketIndicatorProjection projection(
			TicketStatus status,
			TicketPriority priority,
			TicketCategory category,
			ClassificationOrigin origin,
			TicketCategory suggestedCategory,
			TicketPriority suggestedPriority,
			Double confidence,
			UUID requesterId,
			String requesterName,
			LocalDateTime createdAt,
			UUID assigneeId,
			String assigneeName,
			LocalDateTime closedAt,
			LocalDateTime classificationReviewedAt
	) {
		LocalDateTime resolvedAt = closedAt;
		return new TicketIndicatorProjection(
				UUID.randomUUID(),
				status,
				priority,
				category,
				origin,
				suggestedCategory,
				suggestedPriority,
				confidence,
				requesterId == null ? UUID.randomUUID() : requesterId,
				requesterName == null ? "Solicitante" : requesterName,
				assigneeId,
				assigneeName,
				createdAt,
				null,
				null,
				resolvedAt,
				closedAt,
				classificationReviewedAt
		);
	}
}
