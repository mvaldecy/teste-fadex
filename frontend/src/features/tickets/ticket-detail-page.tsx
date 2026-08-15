"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Button } from "@/src/components/ui/button";
import { routes } from "@/src/routes/routes";
import { indicatorsService } from "@/src/services/indicators.service";
import { usersService } from "@/src/services/users.service";
import { useSessionStore } from "@/src/stores/session.store";

import { TicketClassificationCard } from "./ticket-classification-card";
import { TicketDetailPanel } from "./ticket-detail-panel";
import { TicketHistoryList } from "./ticket-history-list";
import { TicketSimilarList } from "./ticket-similar-list";
import {
  type AssigneeOption,
  TicketLifecycleActions
} from "./ticket-lifecycle-actions";
import { useTicketActions } from "./use-ticket-actions";
import { useTicketComments } from "./use-ticket-comments";
import { useTicketDetail } from "./use-ticket-detail";
import { useTicketEvents } from "./use-ticket-events";
import { useTicketHistory } from "./use-ticket-history";
import { useTicketSimilar } from "./use-ticket-similar";
import { useTicketTriage } from "./use-ticket-triage";

export function TicketDetailPage() {
  const params = useParams<{ id: string }>();
  const ticketId = params.id ?? null;
  const role = useSessionStore((state) => state.role);
  const isAdmin = role === "ADMIN";

  const detail = useTicketDetail(ticketId);
  const comments = useTicketComments(ticketId);
  const history = useTicketHistory(ticketId);
  // Semelhantes e triagem sao ADMIN: o hook nem chama a API para os demais.
  const similar = useTicketSimilar(ticketId, isAdmin);
  const [assignees, setAssignees] = useState<AssigneeOption[]>([]);

  const refreshTicket = detail.refreshTicket;
  const reloadHistory = history.loadEvents;

  const reloadTicket = useCallback(() => {
    void refreshTicket();
    void reloadHistory();
  }, [refreshTicket, reloadHistory]);

  const actions = useTicketActions(ticketId, reloadTicket);
  const triage = useTicketTriage(ticketId, isAdmin, reloadTicket);

  const reloadSimilar = similar.loadSimilar;
  const reloadTriageJobs = triage.loadActiveJobs;

  useTicketEvents({
    enabled: true,
    onTicketChanged: () => {
      reloadTicket();
      // A deteccao de duplicados e a fila de jobs andam junto da
      // classificacao: sem recarregar aqui, a aba de semelhantes e o botao de
      // triagem ficariam mostrando o estado anterior ao evento.
      void reloadSimilar();
      void reloadTriageJobs();
    },
    onCommentChanged: () => void comments.loadComments()
  });

  // Responsaveis possiveis sao os ADMIN. So carrega para quem pode atribuir.
  //
  // A carga de cada um vem de `workload.openByAssignee`, e o cruzamento e
  // feito aqui de proposito: o mapa **omite** quem nao tem chamado aberto, e
  // essa pessoa costuma ser a melhor escolha para receber o proximo. Ausencia
  // vira zero, e a lista sai ordenada da menor carga para a maior.
  useEffect(() => {
    if (!isAdmin) {
      return;
    }

    let isActive = true;

    async function loadAssignees() {
      try {
        const [usersResponse, indicators] = await Promise.all([
          usersService.list({
            role: "ADMIN",
            page: 0,
            size: 50,
            sort: "name,asc"
          }),
          // A carga e um enfeite util: se os indicadores falharem, a
          // atribuicao continua funcionando sem o numero.
          indicatorsService.get().catch(() => null)
        ]);

        if (!isActive) {
          return;
        }

        const openByUserId = new Map(
          (indicators?.workload.openByAssignee ?? []).map((item) => [
            item.user.id,
            item.openTickets
          ])
        );

        setAssignees(
          usersResponse.content
            .map((user) => ({
              ...user,
              openTickets: openByUserId.get(user.id) ?? 0
            }))
            .sort(
              (first, second) =>
                first.openTickets - second.openTickets ||
                first.name.localeCompare(second.name)
            )
        );
      } catch {
        // A falha aqui so esvazia a lista de responsaveis; o restante do
        // detalhe continua utilizavel, entao nao vira erro de tela inteira.
        if (isActive) {
          setAssignees([]);
        }
      }
    }

    void loadAssignees();

    return () => {
      isActive = false;
    };
  }, [isAdmin]);

  const actionsSlot =
    isAdmin && detail.ticket ? (
      <div className="grid gap-4">
        <TicketLifecycleActions
          assignees={assignees}
          choices={detail.choices}
          isSubmitting={actions.isSubmitting}
          ticket={detail.ticket}
          onAssign={actions.assign}
          onChangeStatus={actions.changeStatus}
          onUnassign={actions.unassign}
        />

        <TicketClassificationCard
          choiceLabels={detail.choiceLabels}
          choices={detail.choices}
          hasTriageInProgress={triage.hasTriageInProgress}
          isRequestingTriage={triage.isRequesting}
          isSubmitting={actions.isSubmitting}
          ticket={detail.ticket}
          onRequestTriage={triage.requestTriage}
          onUpdateClassification={actions.updateClassification}
        />
      </div>
    ) : null;

  return (
    <div className="mx-auto grid max-w-7xl gap-6">
      <header className="flex flex-col gap-4 border-b border-slate-200 pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.12em] text-emerald-700">
            Chamado
          </p>
          <h1 className="mt-2 text-3xl font-semibold tracking-normal">
            Detalhes do chamado
          </h1>
        </div>

        <Button asChild variant="outline">
          <Link href={routes.tickets}>Voltar para chamados</Link>
        </Button>
      </header>

      {detail.error ? (
        <p className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {detail.error}
        </p>
      ) : null}

      <TicketDetailPanel
        actionsSlot={actionsSlot}
        choiceLabels={detail.choiceLabels}
        comments={comments.comments}
        commentsError={comments.error}
        historySlot={
          <TicketHistoryList
            error={history.error}
            events={history.events}
            isLoading={history.isLoading}
          />
        }
        isCreatingComment={comments.isCreating}
        isLoading={detail.isLoading}
        isLoadingComments={comments.isLoading}
        similarSlot={
          isAdmin ? (
            <TicketSimilarList
              choiceLabels={detail.choiceLabels}
              error={similar.error}
              isLoading={similar.isLoading}
              similarTickets={similar.similarTickets}
            />
          ) : undefined
        }
        ticket={detail.ticket}
        onCreateComment={comments.createComment}
      />
    </div>
  );
}
