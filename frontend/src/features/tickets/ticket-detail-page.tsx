"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/src/components/ui/button";
import { cn } from "@/src/lib/utils";
import { routes } from "@/src/routes/routes";
import { indicatorsService } from "@/src/services/indicators.service";
import { usersService } from "@/src/services/users.service";
import { useSessionStore } from "@/src/stores/session.store";

import { RealtimeBadge } from "./realtime-badge";
import { TicketCancelAction } from "./ticket-cancel-action";
import { TicketClassificationCard } from "./ticket-classification-card";
import { TicketDetailPanel } from "./ticket-detail-panel";
import { TicketHistoryList } from "./ticket-history-list";
import { TicketSimilarList } from "./ticket-similar-list";
import {
  type AssigneeOption,
  TicketLifecycleActions
} from "./ticket-lifecycle-actions";
import {
  canCancelFrom,
  useTicketStatusTransitions
} from "./ticket-status-transitions";
import type { TicketEventSignal } from "./ticket-event-signal";
import { useRealtimeFeedback } from "./use-realtime-feedback";
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
  // `?aba=semelhantes` chega do selo da listagem: quem clicou ali quer ver os
  // semelhantes, nao o resumo do chamado.
  const aba = useSearchParams().get("aba");
  const role = useSessionStore((state) => state.role);
  const userId = useSessionStore((state) => state.user?.id ?? null);
  const isAdmin = role === "ADMIN";
  const transitions = useTicketStatusTransitions();
  const realtime = useRealtimeFeedback();

  const detail = useTicketDetail(ticketId);
  const comments = useTicketComments(ticketId);
  const history = useTicketHistory(ticketId);
  // Semelhantes e triagem são ADMIN: o hook nem chama a API para os demais.
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

  const registerUpdate = realtime.registerUpdate;
  const reloadComments = comments.loadComments;

  /**
   * Reage apenas ao chamado aberto. Um evento de **outro** chamado não muda
   * nada nesta tela: recarregar por ele so gastaria requisicao e, pior,
   * anunciaria uma atualizacao que o usuário não veria acontecer.
   *
   * `CONEXAO_ESTABELECIDA` não traz id e recarrega mesmo assim — e a
   * resincronizacao após reconexao, e ali o silencio e correto.
   */
  const handleTicketEvent = useCallback(
    (signal: TicketEventSignal) => {
      const isReconnect = signal.name === "CONEXAO_ESTABELECIDA";

      if (!isReconnect && signal.ticketId !== ticketId) {
        return;
      }

      reloadTicket();
      // A deteccao de duplicados e a fila de jobs andam junto da
      // classificação: sem recarregar aqui, a aba de semelhantes e o botao de
      // triagem ficariam mostrando o estado anterior ao evento.
      void reloadSimilar();
      void reloadTriageJobs();
      void reloadComments();

      if (isReconnect) {
        return;
      }

      registerUpdate(signal.ticketId);

      toast.info("Este chamado acabou de mudar", {
        description:
          signal.name === "CLASSIFICACAO_CONCLUIDA"
            ? "A triagem por IA terminou e já sugeriu categoria e prioridade."
            : "A tela já está mostrando o estado novo.",
        id: `detalhe-${ticketId}`,
        duration: 6000
      });
    },
    [
      registerUpdate,
      reloadComments,
      reloadSimilar,
      reloadTicket,
      reloadTriageJobs,
      ticketId
    ]
  );

  useTicketEvents({
    enabled: true,
    onTicketChanged: handleTicketEvent,
    // O evento e o mesmo para chamado e comentario; `handleTicketEvent` já
    // recarrega os dois.
    onCommentChanged: () => undefined
  });

  // Responsaveis possiveis são os ADMIN. So carrega para quem pode atribuir.
  //
  // A carga de cada um vem de `workload.openByAssignee`, e o cruzamento e
  // feito aqui de proposito: o mapa **omite** quem não tem chamado aberto, e
  // essa pessoa costuma ser a melhor escolha para receber o próximo. Ausencia
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
          // atribuição continua funcionando sem o número.
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
        // detalhe continua utilizavel, entao não vira erro de tela inteira.
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

  // Quem pode cancelar: ADMIN em qualquer chamado, SOLICITANTE no próprio e
  // apenas enquanto ABERTO — a mesma regra que o backend aplica em
  // `DELETE /tickets/{id}`. O que a matriz do servidor decide e se o **estado**
  // aceita cancelamento; o papel e camada de cima, e o servidor reconfere.
  //
  // Sem matriz carregada, `canCancelFrom` e falso e a ação some, em vez de
  // aparecer para tomar 409.
  const isOwner = detail.ticket?.requester.id === userId;
  const canCancel =
    detail.ticket !== null &&
    canCancelFrom(transitions, detail.ticket.status) &&
    (isAdmin || (isOwner && detail.ticket.status === "ABERTO"));

  const cancelSlot = canCancel ? (
    <TicketCancelAction
      isSubmitting={actions.isSubmitting}
      onCancel={actions.cancel}
    />
  ) : null;

  const actionsSlot =
    detail.ticket && (isAdmin || cancelSlot) ? (
      <div className="grid gap-4">
        {isAdmin ? (
          <TicketLifecycleActions
            assignees={assignees}
            currentUserId={userId}
            choices={detail.choices}
            isSubmitting={actions.isSubmitting}
            ticket={detail.ticket}
            transitions={transitions}
            onAssign={actions.assign}
            onChangeStatus={actions.changeStatus}
            onUnassign={actions.unassign}
          />
        ) : null}

        {cancelSlot}

        {isAdmin ? (
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
        ) : null}
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
          <RealtimeBadge className="mt-3" updatedAt={realtime.updatedAt} />
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

      <div
        className={cn(
          "rounded-lg transition-shadow",
          ticketId && realtime.highlightedIds.has(ticketId)
            ? "shadow-[0_0_0_3px_rgb(16_185_129_/_0.45)]"
            : "shadow-none"
        )}
      >
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
          initialTab={aba === "semelhantes" ? "similar" : undefined}
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
    </div>
  );
}
