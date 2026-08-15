"use client";

import {
  Card,
  CardContent,
  CardHeader,
  CardTitle
} from "@/src/components/ui/card";
import { Skeleton } from "@/src/components/ui/skeleton";
import { IndicatorBreakdown } from "./indicator-breakdown";
import { IndicatorCard } from "./indicator-card";
import { IndicatorDurationCard } from "./indicator-duration-card";
import { IndicatorDurationTable } from "./indicator-duration-table";
import { IndicatorSlaCard } from "./indicator-sla-card";
import { useIndicators } from "./use-indicators";

function formatPercent(value: number) {
  return `${value.toFixed(1)}%`;
}

function formatHours(value: number) {
  return `${value.toFixed(1)} h`;
}

function formatDateTime(value?: string) {
  if (!value) {
    return null;
  }

  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(new Date(value));
}

export function DashboardPage() {
  const { choiceLabels, indicators, isAdmin, isLoading, error } =
    useIndicators();

  if (isLoading) {
    return (
      <div className="mx-auto grid max-w-7xl gap-6">
        <Skeleton className="h-20 rounded-lg" />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton className="h-28 rounded-lg" key={index} />
          ))}
        </div>
        <Skeleton className="h-64 rounded-lg" />
      </div>
    );
  }

  const overview = indicators?.overview;
  const durations = indicators?.durations;
  const ai = indicators?.ai;
  const workload = indicators?.workload;
  const generatedAt = formatDateTime(indicators?.generatedAt);

  const openByAssignee = workload?.openByAssignee ?? [];
  const topRequesters = workload?.topRequesters ?? [];
  const closureByAssignee = workload?.closureTimeByAssignee ?? [];

  const header = (
    <header className="border-b border-slate-200 pb-5">
      <p className="text-sm font-semibold uppercase tracking-[0.12em] text-emerald-700">
        Indicadores
      </p>
      <h1 className="mt-2 text-3xl font-semibold tracking-normal">Dashboard</h1>
      <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
        Visao operacional dos chamados, atualizada em tempo real.
        {generatedAt ? ` Apurado em ${generatedAt}.` : ""}
      </p>
    </header>
  );

  // Os indicadores sao restritos a ADMIN no backend. Dizer isso e melhor do
  // que pedir o dado e exibir o 403 — ou uma parede de "--".
  if (!isAdmin) {
    return (
      <div className="mx-auto grid max-w-7xl gap-6">
        {header}

        <p className="rounded-md border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600">
          Os indicadores gerenciais sao restritos a administradores. Acompanhe
          seus chamados pelo menu Chamados.
        </p>
      </div>
    );
  }

  return (
    <div className="mx-auto grid max-w-7xl gap-6">
      {header}

      {error ? (
        <p className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {error}
        </p>
      ) : null}

      <section className="grid gap-4">
        <h2 className="text-lg font-semibold">Panorama</h2>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <IndicatorCard
            label="Alta prioridade em aberto"
            tone="alert"
            value={overview?.openHighPriority}
          />
          <IndicatorCard label="Abertos hoje" value={overview?.openedToday} />
          <IndicatorCard label="Fechados hoje" value={overview?.closedToday} />
          <IndicatorCard
            label="Chamado mais antigo"
            formatValue={formatHours}
            value={durations?.oldestOpenTicketHours}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <IndicatorCard label="Total de chamados" value={overview?.total} />
          <IndicatorCard
            label="Abertos na semana"
            value={overview?.openedThisWeek}
          />
          <IndicatorCard
            label="Fechados na semana"
            value={overview?.closedThisWeek}
          />
          <IndicatorCard
            label="Dentro do SLA"
            formatValue={formatPercent}
            note={
              durations
                ? `${durations.sla.overall.withinTarget} de ${durations.sla.overall.evaluated} avaliados | ALTA 4h, MEDIA 24h, BAIXA 72h`
                : undefined
            }
            value={durations?.sla.overall.percentage}
          />
        </div>

        <div className="grid gap-4 lg:grid-cols-3">
          <IndicatorBreakdown
            data={overview?.byStatus}
            labels={choiceLabels?.statuses}
            title="Por status"
          />
          <IndicatorBreakdown
            data={overview?.byPriority}
            labels={choiceLabels?.priorities}
            title="Por prioridade"
          />
          <IndicatorBreakdown
            data={overview?.byCategory}
            labels={choiceLabels?.categories}
            title="Por categoria"
          />
        </div>
      </section>

      <section className="grid gap-4">
        <h2 className="text-lg font-semibold">Tempos de atendimento</h2>

        <div className="grid gap-4 lg:grid-cols-3">
          <IndicatorDurationCard
            duration={durations?.closure.overall}
            title="Tempo de fechamento"
          />
          <IndicatorDurationCard
            duration={durations?.firstResponse.overall}
            title="Tempo ate primeira resposta"
          />
          <IndicatorDurationCard
            duration={durations?.assignment.overall}
            title="Tempo ate atribuicao"
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <IndicatorCard
            label="Backlog ate 1 dia"
            value={durations?.backlogAging.upToOneDay}
          />
          <IndicatorCard
            label="Backlog 1 a 3 dias"
            value={durations?.backlogAging.oneToThreeDays}
          />
          <IndicatorCard
            label="Backlog acima de 3 dias"
            tone="alert"
            value={durations?.backlogAging.overThreeDays}
          />
        </div>

        <div className="grid gap-4 lg:grid-cols-3">
          <IndicatorSlaCard
            byPriority={durations?.sla.byPriority}
            labels={choiceLabels?.priorities}
          />
          <IndicatorDurationTable
            groups={durations?.closure.byPriority}
            labels={choiceLabels?.priorities}
            title="Fechamento por prioridade"
          />
          <IndicatorDurationTable
            groups={durations?.closure.byCategory}
            labels={choiceLabels?.categories}
            title="Fechamento por categoria"
          />
        </div>

        <div className="grid gap-4 lg:grid-cols-2">
          <IndicatorDurationTable
            groups={durations?.firstResponse.byPriority}
            labels={choiceLabels?.priorities}
            title="Primeira resposta por prioridade"
          />
          <IndicatorDurationTable
            groups={durations?.assignment.byPriority}
            labels={choiceLabels?.priorities}
            title="Atribuicao por prioridade"
          />
        </div>
      </section>

      <section className="grid gap-4">
        <h2 className="text-lg font-semibold">Triagem por IA</h2>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <IndicatorCard
            label="Concordancia admin x IA"
            formatValue={formatPercent}
            note={
              ai
                ? `${ai.agreementRate.agreed} de ${ai.agreementRate.evaluated} revisoes`
                : undefined
            }
            value={ai?.agreementRate.percentage}
          />
          <IndicatorCard
            label="Confianca media"
            formatValue={(value) => formatPercent(value * 100)}
            value={ai?.averageConfidence}
          />
          <IndicatorCard
            label="Duplicados detectados"
            value={ai?.duplicatesDetected}
          />
          <IndicatorCard
            label="Fila ate a conclusao"
            formatValue={(value) => `${value.toFixed(1)} s`}
            note="Espera na fila mais execucao do job"
            value={ai?.jobQueue.averageQueueToDoneSeconds}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <IndicatorCard label="Jobs pendentes" value={ai?.jobQueue.pending} />
          <IndicatorCard
            label="Jobs em processamento"
            value={ai?.jobQueue.processing}
          />
          <IndicatorCard label="Jobs concluidos" value={ai?.jobQueue.done} />
          <IndicatorCard
            label="Jobs com falha"
            tone="alert"
            value={ai?.jobQueue.failed}
          />
        </div>

        <div className="grid gap-4 lg:grid-cols-2">
          <IndicatorBreakdown
            data={ai?.originDistribution}
            labels={choiceLabels?.classificationOrigins}
            title="Origem da classificacao"
          />
        </div>
      </section>

      {openByAssignee.length > 0 ||
      topRequesters.length > 0 ||
      closureByAssignee.length > 0 ? (
        <section className="grid gap-4">
          <h2 className="text-lg font-semibold">Distribuicao por pessoa</h2>

          <div className="grid gap-4 lg:grid-cols-3">
            {openByAssignee.length > 0 ? (
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">
                    Carga por responsavel
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <ul className="grid gap-2 text-sm">
                    {openByAssignee.map((item) => (
                      <li
                        className="flex items-center justify-between gap-3"
                        key={item.user.id}
                      >
                        <span className="truncate text-slate-700">
                          {item.user.name}
                        </span>
                        <span className="shrink-0 tabular-nums font-medium text-slate-950">
                          {item.openTickets} em aberto
                        </span>
                      </li>
                    ))}
                  </ul>
                </CardContent>
              </Card>
            ) : null}

            {closureByAssignee.length > 0 ? (
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">
                    Tempo de fechamento por responsavel
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <ul className="grid gap-2 text-sm">
                    {closureByAssignee.map((item) => (
                      <li
                        className="flex items-center justify-between gap-3"
                        key={item.user.id}
                      >
                        <span className="truncate text-slate-700">
                          {item.user.name}
                        </span>
                        <span className="shrink-0 tabular-nums font-medium text-slate-950">
                          {typeof item.medianHours === "number"
                            ? `${item.medianHours.toFixed(1)} h`
                            : "--"}
                          <span className="ml-1 text-xs font-normal text-slate-500">
                            ({item.sampleSize})
                          </span>
                        </span>
                      </li>
                    ))}
                  </ul>
                </CardContent>
              </Card>
            ) : null}

            {topRequesters.length > 0 ? (
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Top solicitantes</CardTitle>
                </CardHeader>
                <CardContent>
                  <ul className="grid gap-2 text-sm">
                    {topRequesters.map((item) => (
                      <li
                        className="flex items-center justify-between gap-3"
                        key={item.user.id}
                      >
                        <span className="truncate text-slate-700">
                          {item.user.name}
                        </span>
                        <span className="shrink-0 tabular-nums font-medium text-slate-950">
                          {item.tickets} chamados
                        </span>
                      </li>
                    ))}
                  </ul>
                </CardContent>
              </Card>
            ) : null}
          </div>
        </section>
      ) : null}
    </div>
  );
}
