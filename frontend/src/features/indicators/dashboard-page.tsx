"use client";

import { AlertTriangle } from "lucide-react";
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
import { useIndicators } from "./use-indicators";

function formatPercent(value: number) {
  return `${value.toFixed(1)}%`;
}

function formatHours(value: number) {
  return `${value.toFixed(1)} h`;
}

export function DashboardPage() {
  const {
    choiceLabels,
    indicators,
    isFixture,
    fixtureReason,
    isLoading,
    error
  } = useIndicators();

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

  const hasAssigneeLoad = (indicators?.cargaPorResponsavel?.length ?? 0) > 0;
  const hasTopRequesters = (indicators?.topSolicitantes?.length ?? 0) > 0;

  return (
    <div className="mx-auto grid max-w-7xl gap-6">
      <header className="border-b border-slate-200 pb-5">
        <p className="text-sm font-semibold uppercase tracking-[0.12em] text-emerald-700">
          Indicadores
        </p>
        <h1 className="mt-2 text-3xl font-semibold tracking-normal">
          Dashboard
        </h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
          Visao operacional dos chamados, atualizada em tempo real.
        </p>
      </header>

      {error ? (
        <p className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {error}
        </p>
      ) : null}

      {isFixture ? (
        <p className="flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-900">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>
            Dados de exemplo: os numeros abaixo nao vem do banco.{" "}
            {fixtureReason} O endpoint provavelmente ainda nao foi publicado
            pela frente API.
          </span>
        </p>
      ) : null}

      <section className="grid gap-4">
        <h2 className="text-lg font-semibold">Panorama</h2>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <IndicatorCard
            label="Alta prioridade em aberto"
            tone="alert"
            value={indicators?.altaPrioridadeEmAberto}
          />
          <IndicatorCard
            label="Abertos hoje"
            value={indicators?.abertosHoje}
          />
          <IndicatorCard
            label="Fechados hoje"
            value={indicators?.fechadosHoje}
          />
          <IndicatorCard
            label="Chamado mais antigo"
            formatValue={formatHours}
            value={indicators?.idadeChamadoMaisAntigoHoras}
          />
        </div>

        <div className="grid gap-4 lg:grid-cols-3">
          <IndicatorBreakdown
            data={indicators?.totalPorStatus}
            labels={choiceLabels?.statuses}
            title="Por status"
          />
          <IndicatorBreakdown
            data={indicators?.totalPorPrioridade}
            labels={choiceLabels?.priorities}
            title="Por prioridade"
          />
          <IndicatorBreakdown
            data={indicators?.totalPorCategoria}
            labels={choiceLabels?.categories}
            title="Por categoria"
          />
        </div>
      </section>

      <section className="grid gap-4">
        <h2 className="text-lg font-semibold">Tempos de atendimento</h2>

        <div className="grid gap-4 lg:grid-cols-3">
          <IndicatorDurationCard
            duration={indicators?.tempoFechamentoHoras}
            title="Tempo de fechamento"
          />
          <IndicatorDurationCard
            duration={indicators?.tempoPrimeiraRespostaHoras}
            title="Tempo ate primeira resposta"
          />
          <IndicatorDurationCard
            duration={indicators?.tempoAtribuicaoHoras}
            title="Tempo ate atribuicao"
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <IndicatorCard
            label="Backlog ate 1 dia"
            value={indicators?.agingBacklog?.ate1Dia}
          />
          <IndicatorCard
            label="Backlog 1 a 3 dias"
            value={indicators?.agingBacklog?.de1A3Dias}
          />
          <IndicatorCard
            label="Backlog acima de 3 dias"
            value={indicators?.agingBacklog?.acima3Dias}
          />
          <IndicatorCard
            label="Dentro do SLA"
            formatValue={formatPercent}
            note="ALTA 4h, MEDIA 24h, BAIXA 72h"
            value={indicators?.percentualDentroDoSla}
          />
        </div>
      </section>

      <section className="grid gap-4">
        <h2 className="text-lg font-semibold">Triagem por IA</h2>

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <IndicatorCard
            label="Concordancia admin x IA"
            formatValue={formatPercent}
            note="Sugestoes aceitas sem correcao"
            value={indicators?.concordanciaIaPercentual}
          />
          <IndicatorCard
            label="Confianca media"
            formatValue={(value) => formatPercent(value * 100)}
            value={indicators?.confiancaMediaIa}
          />
          <IndicatorCard
            label="Jobs pendentes"
            value={indicators?.filaJobs?.pendentes}
          />
          <IndicatorCard
            label="Jobs com falha"
            tone="alert"
            value={indicators?.filaJobs?.falhos}
          />
        </div>

        <div className="grid gap-4 lg:grid-cols-3">
          <IndicatorBreakdown
            data={indicators?.distribuicaoClassificacao}
            labels={choiceLabels?.classificationOrigins}
            title="Origem da classificacao"
          />
          <IndicatorCard
            label="Duplicados detectados"
            value={indicators?.duplicadosDetectados}
          />
          <IndicatorCard
            label="Tempo medio de processamento"
            formatValue={(value) => `${value.toFixed(1)} s`}
            value={indicators?.filaJobs?.tempoMedioProcessamentoSegundos}
          />
        </div>
      </section>

      {hasAssigneeLoad || hasTopRequesters ? (
        <section className="grid gap-4">
          <h2 className="text-lg font-semibold">Distribuicao por pessoa</h2>

          <div className="grid gap-4 lg:grid-cols-2">
            {hasAssigneeLoad ? (
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">
                    Carga por responsavel
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <ul className="grid gap-2 text-sm">
                    {indicators?.cargaPorResponsavel?.map((item) => (
                      <li
                        className="flex items-center justify-between gap-3"
                        key={item.responsavel.id}
                      >
                        <span className="truncate text-slate-700">
                          {item.responsavel.name}
                        </span>
                        <span className="shrink-0 tabular-nums font-medium text-slate-950">
                          {item.abertos} em aberto
                        </span>
                      </li>
                    ))}
                  </ul>
                </CardContent>
              </Card>
            ) : null}

            {hasTopRequesters ? (
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Top solicitantes</CardTitle>
                </CardHeader>
                <CardContent>
                  <ul className="grid gap-2 text-sm">
                    {indicators?.topSolicitantes?.map((item) => (
                      <li
                        className="flex items-center justify-between gap-3"
                        key={item.solicitante.id}
                      >
                        <span className="truncate text-slate-700">
                          {item.solicitante.name}
                        </span>
                        <span className="shrink-0 tabular-nums font-medium text-slate-950">
                          {item.total} chamados
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
