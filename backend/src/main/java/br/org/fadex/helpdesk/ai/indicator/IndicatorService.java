package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.ai.job.AiJob;
import br.org.fadex.helpdesk.ai.job.AiJobRepository;
import br.org.fadex.helpdesk.ai.job.AiJobStatus;
import br.org.fadex.helpdesk.model.enums.ClassificationOrigin;
import br.org.fadex.helpdesk.model.enums.TicketCategory;
import br.org.fadex.helpdesk.model.enums.TicketPriority;
import br.org.fadex.helpdesk.model.enums.TicketStatus;
import br.org.fadex.helpdesk.model.user.UserMinDto;
import br.org.fadex.helpdesk.repository.TicketLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calcula as quatro camadas de indicadores a partir de uma unica projecao de leitura.
 *
 * Toda a agregacao acontece em memoria (decisao D5 do design): mediana e p90 usam o mesmo codigo em
 * qualquer camada, adicionar metrica nao adiciona query, e a suite roda em H2 sem SQL especifico de
 * Postgres. A carga cresce linear com o numero de chamados, o que e irrelevante no volume de uma
 * central interna; se deixar de ser, a saida e trocar a projecao por agregacao no banco atras desta
 * mesma interface, sem mudar o contrato do endpoint.
 */
@Service
public class IndicatorService {

	private static final int TOP_REQUESTERS_LIMIT = 5;
	private static final double SECONDS_PER_HOUR = 3600.0;
	private static final long ONE_DAY_HOURS = 24;
	private static final long THREE_DAYS_HOURS = 72;

	private final IndicatorRepository indicatorRepository;
	private final AiJobRepository aiJobRepository;
	private final TicketLinkRepository ticketLinkRepository;
	private final Clock clock;

	public IndicatorService(
			IndicatorRepository indicatorRepository,
			AiJobRepository aiJobRepository,
			TicketLinkRepository ticketLinkRepository,
			Clock clock
	) {
		this.indicatorRepository = indicatorRepository;
		this.aiJobRepository = aiJobRepository;
		this.ticketLinkRepository = ticketLinkRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public IndicatorsDto getIndicators() {
		LocalDateTime now = LocalDateTime.now(clock);
		List<TicketIndicatorProjection> projections = indicatorRepository.findAllProjections();

		OverviewIndicatorsDto overview = buildOverview(projections, now);
		DurationIndicatorsDto durations = buildDurations(projections, now);
		AiIndicatorsDto ai = buildAi(projections);
		WorkloadIndicatorsDto workload = buildWorkload(projections);

		IndicatorsDto response = new IndicatorsDto(now, overview, durations, ai, workload);

		return response;
	}

	private OverviewIndicatorsDto buildOverview(List<TicketIndicatorProjection> projections, LocalDateTime now) {
		LocalDate today = now.toLocalDate();
		LocalDate weekStart = today.with(DayOfWeek.MONDAY);

		Map<TicketStatus, Long> byStatus = countBy(projections, TicketIndicatorProjection::status);
		Map<TicketPriority, Long> byPriority = countBy(projections, TicketIndicatorProjection::priority);
		Map<TicketCategory, Long> byCategory = countBy(projections, TicketIndicatorProjection::category);

		long openedToday = projections.stream()
				.filter(projection -> today.equals(projection.createdAt().toLocalDate()))
				.count();
		long closedToday = projections.stream()
				.filter(TicketIndicatorProjection::isClosed)
				.filter(projection -> today.equals(projection.closedAt().toLocalDate()))
				.count();
		long openedThisWeek = projections.stream()
				.filter(projection -> !projection.createdAt().toLocalDate().isBefore(weekStart))
				.count();
		long closedThisWeek = projections.stream()
				.filter(TicketIndicatorProjection::isClosed)
				.filter(projection -> !projection.closedAt().toLocalDate().isBefore(weekStart))
				.count();
		long openHighPriority = projections.stream()
				.filter(TicketIndicatorProjection::isOpen)
				.filter(projection -> projection.priority() == TicketPriority.ALTA)
				.count();

		OverviewIndicatorsDto response = new OverviewIndicatorsDto(
				projections.size(),
				byStatus,
				byPriority,
				byCategory,
				openedToday,
				closedToday,
				openedThisWeek,
				closedThisWeek,
				openHighPriority
		);

		return response;
	}

	private DurationIndicatorsDto buildDurations(List<TicketIndicatorProjection> projections, LocalDateTime now) {
		DurationGroupDto closure = buildDurationGroup(projections, TicketIndicatorProjection::closedAt);
		DurationGroupDto firstResponse = buildDurationGroup(projections, TicketIndicatorProjection::firstResponseAt);
		DurationGroupDto assignment = buildDurationGroup(projections, TicketIndicatorProjection::assignedAt);
		BacklogAgingDto backlogAging = buildBacklogAging(projections, now);
		Double oldestOpenTicketHours = buildOldestOpenTicketHours(projections, now);
		SlaIndicatorsDto sla = buildSla(projections, now);

		DurationIndicatorsDto response = new DurationIndicatorsDto(
				closure,
				firstResponse,
				assignment,
				backlogAging,
				oldestOpenTicketHours,
				sla
		);

		return response;
	}

	private DurationGroupDto buildDurationGroup(
			List<TicketIndicatorProjection> projections,
			Function<TicketIndicatorProjection, LocalDateTime> endInstant
	) {
		List<TicketIndicatorProjection> measurable = projections.stream()
				.filter(projection -> endInstant.apply(projection) != null)
				.toList();

		DurationStatsDto overall = DurationStats.of(durations(measurable, endInstant));

		Map<TicketPriority, DurationStatsDto> byPriority = new EnumMap<>(TicketPriority.class);
		measurable.stream()
				.collect(Collectors.groupingBy(TicketIndicatorProjection::priority))
				.forEach((priority, group) -> byPriority.put(priority, DurationStats.of(durations(group, endInstant))));

		Map<TicketCategory, DurationStatsDto> byCategory = new EnumMap<>(TicketCategory.class);
		measurable.stream()
				.collect(Collectors.groupingBy(TicketIndicatorProjection::category))
				.forEach((category, group) -> byCategory.put(category, DurationStats.of(durations(group, endInstant))));

		DurationGroupDto response = new DurationGroupDto(overall, byPriority, byCategory);

		return response;
	}

	private List<Duration> durations(
			List<TicketIndicatorProjection> projections,
			Function<TicketIndicatorProjection, LocalDateTime> endInstant
	) {
		return projections.stream()
				.map(projection -> Duration.between(projection.createdAt(), endInstant.apply(projection)))
				.toList();
	}

	private BacklogAgingDto buildBacklogAging(List<TicketIndicatorProjection> projections, LocalDateTime now) {
		long upToOneDay = 0;
		long oneToThreeDays = 0;
		long overThreeDays = 0;

		for (TicketIndicatorProjection projection : projections) {
			if (!projection.isOpen()) {
				continue;
			}

			long ageHours = Duration.between(projection.createdAt(), now).toHours();

			if (ageHours <= ONE_DAY_HOURS) {
				upToOneDay++;
			} else if (ageHours <= THREE_DAYS_HOURS) {
				oneToThreeDays++;
			} else {
				overThreeDays++;
			}
		}

		BacklogAgingDto response = new BacklogAgingDto(upToOneDay, oneToThreeDays, overThreeDays);

		return response;
	}

	private Double buildOldestOpenTicketHours(List<TicketIndicatorProjection> projections, LocalDateTime now) {
		return projections.stream()
				.filter(TicketIndicatorProjection::isOpen)
				.map(projection -> Duration.between(projection.createdAt(), now))
				.max(Comparator.naturalOrder())
				.map(duration -> round(duration.toSeconds() / SECONDS_PER_HOUR, 1))
				.orElse(null);
	}

	private SlaIndicatorsDto buildSla(List<TicketIndicatorProjection> projections, LocalDateTime now) {
		Map<TicketPriority, long[]> tally = new EnumMap<>(TicketPriority.class);
		long evaluated = 0;
		long withinTarget = 0;

		for (TicketIndicatorProjection projection : projections) {
			// Chamado cancelado sai do numerador e do denominador: nao foi resolvido, mas tambem nao
			// esta pendente de ninguem. Sem este corte ele cairia no ramo de chamado em aberto, com
			// settledAt nulo, e viraria violacao permanente de SLA — piorando sozinho com o tempo,
			// que e o mesmo erro que a regra do chamado recem-criado ja evita.
			if (projection.isCanceled()) {
				continue;
			}

			// Encerrado inclui RESOLVIDO, nao so FECHADO: o cronometro do atendimento para quando o
			// trabalho termina, nao quando alguem lembra de fechar o chamado.
			LocalDateTime settledAt = projection.settledAt();
			LocalDateTime end = settledAt == null ? now : settledAt;
			Duration elapsed = Duration.between(projection.createdAt(), end);
			SlaOutcome outcome = SlaTarget.forPriority(projection.priority())
					.evaluate(elapsed, settledAt != null);

			if (outcome == SlaOutcome.NOT_EVALUABLE) {
				continue;
			}

			long[] slice = tally.computeIfAbsent(projection.priority(), priority -> new long[2]);
			slice[0]++;
			evaluated++;

			if (outcome == SlaOutcome.WITHIN) {
				slice[1]++;
				withinTarget++;
			}
		}

		Map<TicketPriority, SlaSliceDto> byPriority = new EnumMap<>(TicketPriority.class);
		tally.forEach((priority, slice) -> byPriority.put(priority, slice(slice[0], slice[1])));

		SlaIndicatorsDto response = new SlaIndicatorsDto(slice(evaluated, withinTarget), byPriority);

		return response;
	}

	private SlaSliceDto slice(long evaluated, long withinTarget) {
		Double percentage = evaluated == 0 ? null : round(withinTarget * 100.0 / evaluated, 1);

		return new SlaSliceDto(evaluated, withinTarget, percentage);
	}

	private AiIndicatorsDto buildAi(List<TicketIndicatorProjection> projections) {
		AgreementRateDto agreementRate = buildAgreementRate(projections);
		Double averageConfidence = projections.stream()
				.map(TicketIndicatorProjection::aiConfidence)
				.filter(confidence -> confidence != null)
				.mapToDouble(Double::doubleValue)
				.average()
				.stream()
				.mapToObj(average -> round(average, 2))
				.findFirst()
				.orElse(null);
		Map<ClassificationOrigin, Long> originDistribution =
				countBy(projections, TicketIndicatorProjection::classificationOrigin);

		AiIndicatorsDto response = new AiIndicatorsDto(
				agreementRate,
				averageConfidence,
				originDistribution,
				buildJobQueue(),
				ticketLinkRepository.count()
		);

		return response;
	}

	private AgreementRateDto buildAgreementRate(List<TicketIndicatorProjection> projections) {
		List<TicketIndicatorProjection> reviewed = projections.stream()
				.filter(TicketIndicatorProjection::hasSuggestion)
				.filter(TicketIndicatorProjection::isReviewed)
				.toList();
		long agreed = reviewed.stream()
				.filter(TicketIndicatorProjection::agreesWithSuggestion)
				.count();
		long evaluated = reviewed.size();
		Double percentage = evaluated == 0 ? null : round(agreed * 100.0 / evaluated, 1);

		AgreementRateDto response = new AgreementRateDto(evaluated, agreed, percentage);

		return response;
	}

	private JobQueueIndicatorsDto buildJobQueue() {
		List<AiJob> doneJobs = aiJobRepository.findByStatus(AiJobStatus.DONE);
		Double averageQueueToDoneSeconds = doneJobs.stream()
				.filter(job -> job.getCreatedAt() != null && job.getUpdatedAt() != null)
				.mapToDouble(job -> Duration.between(job.getCreatedAt(), job.getUpdatedAt()).toMillis() / 1000.0)
				.average()
				.stream()
				.mapToObj(average -> round(average, 1))
				.findFirst()
				.orElse(null);

		JobQueueIndicatorsDto response = new JobQueueIndicatorsDto(
				aiJobRepository.countByStatus(AiJobStatus.PENDING),
				aiJobRepository.countByStatus(AiJobStatus.PROCESSING),
				aiJobRepository.countByStatus(AiJobStatus.FAILED),
				aiJobRepository.countByStatus(AiJobStatus.DONE),
				averageQueueToDoneSeconds
		);

		return response;
	}

	private WorkloadIndicatorsDto buildWorkload(List<TicketIndicatorProjection> projections) {
		List<AssigneeLoadDto> openByAssignee = projections.stream()
				.filter(TicketIndicatorProjection::isOpen)
				.filter(projection -> projection.assigneeId() != null)
				.collect(Collectors.groupingBy(TicketIndicatorProjection::assigneeId, LinkedHashMap::new, Collectors.toList()))
				.values()
				.stream()
				.map(group -> new AssigneeLoadDto(assignee(group.getFirst()), group.size()))
				.sorted(Comparator.comparingLong(AssigneeLoadDto::openTickets).reversed())
				.toList();

		List<AssigneeClosureDto> closureTimeByAssignee = new ArrayList<>();
		projections.stream()
				.filter(TicketIndicatorProjection::isClosed)
				.filter(projection -> projection.assigneeId() != null)
				.collect(Collectors.groupingBy(TicketIndicatorProjection::assigneeId, LinkedHashMap::new, Collectors.toList()))
				.forEach((assigneeId, group) -> {
					DurationStatsDto stats = DurationStats.of(durations(group, TicketIndicatorProjection::closedAt));
					closureTimeByAssignee.add(new AssigneeClosureDto(
							assignee(group.getFirst()),
							stats.sampleSize(),
							stats.averageHours(),
							stats.medianHours()
					));
				});
		closureTimeByAssignee.sort(Comparator.comparingInt(AssigneeClosureDto::sampleSize).reversed());

		List<RequesterVolumeDto> topRequesters = projections.stream()
				.collect(Collectors.groupingBy(TicketIndicatorProjection::requesterId, LinkedHashMap::new, Collectors.toList()))
				.values()
				.stream()
				.map(group -> new RequesterVolumeDto(requester(group.getFirst()), group.size()))
				.sorted(Comparator.comparingLong(RequesterVolumeDto::tickets).reversed())
				.limit(TOP_REQUESTERS_LIMIT)
				.toList();

		WorkloadIndicatorsDto response = new WorkloadIndicatorsDto(
				openByAssignee,
				closureTimeByAssignee,
				topRequesters
		);

		return response;
	}

	private UserMinDto assignee(TicketIndicatorProjection projection) {
		return new UserMinDto(projection.assigneeId(), projection.assigneeName());
	}

	private UserMinDto requester(TicketIndicatorProjection projection) {
		return new UserMinDto(projection.requesterId(), projection.requesterName());
	}

	private <T> Map<T, Long> countBy(
			List<TicketIndicatorProjection> projections,
			Function<TicketIndicatorProjection, T> classifier
	) {
		// LinkedHashMap e groupingBy: grupo sem ocorrencia simplesmente nao aparece no mapa, em vez
		// de sair zerado. Zero e um valor medido; ausencia de chamado naquela fatia nao e.
		return projections.stream()
				.map(classifier)
				.filter(value -> value != null)
				.collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
	}

	private double round(double value, int decimals) {
		double factor = Math.pow(10, decimals);

		return Math.round(value * factor) / factor;
	}
}
