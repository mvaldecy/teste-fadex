"use client";

import { useCallback } from "react";
import { toast } from "sonner";
import { PaginationBar } from "@/src/components/layout/pagination-bar";
import { RealtimeBadge } from "./realtime-badge";
import { TicketCreateDialog } from "./ticket-create-dialog";
import { TicketFilterBar } from "./ticket-filter-bar";
import { TicketList } from "./ticket-list";
import type { TicketEventSignal } from "./ticket-event-signal";
import { useRealtimeFeedback } from "./use-realtime-feedback";
import { useTicketEvents } from "./use-ticket-events";
import { useTicketList } from "./use-ticket-list";

export function TicketsPage() {
  const tickets = useTicketList();
  const realtime = useRealtimeFeedback();

  const loadTickets = tickets.loadTickets;
  const registerUpdate = realtime.registerUpdate;

  /**
   * Recarrega e **avisa**. O aviso e a parte que faltava: a listagem ja
   * recarregava ao receber o evento, so que em silencio.
   *
   * `CONEXAO_ESTABELECIDA` recarrega sem aviso — e resincronizacao de
   * reconexao, nao mudanca de chamado.
   */
  const handleTicketEvent = useCallback(
    (signal: TicketEventSignal) => {
      void loadTickets();

      if (signal.name === "CONEXAO_ESTABELECIDA") {
        return;
      }

      registerUpdate(signal.ticketId);

      // O assunto do chamado vem primeiro: quem esta olhando a fila quer saber
      // *qual* chamado mudou. "Listagem atualizada em tempo real" descrevia o
      // mecanismo — informacao sobre o sistema, nao sobre o trabalho.
      toast.info(signal.title ?? "Um chamado da fila mudou", {
        description: "Atualizado agora na listagem.",
        // `id` por chamado: uma rajada de eventos do mesmo chamado substitui o
        // proprio aviso em vez de empilhar.
        id: signal.ticketId
          ? `listagem-${signal.ticketId}`
          : "listagem-atualizada",
        duration: 6000
      });
    },
    [loadTickets, registerUpdate]
  );

  useTicketEvents({
    enabled: true,
    onTicketChanged: handleTicketEvent,
    // A recarga da listagem ja cobre comentario: `TicketSummary` nao expoe
    // contagem de comentarios, entao nao ha segunda chamada a fazer.
    onCommentChanged: () => undefined
  });

  return (
    <div className="mx-auto grid max-w-7xl gap-6">
      <header className="flex flex-col gap-4 border-b border-slate-200 pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.12em] text-emerald-700">
            Chamados
          </p>
          <h1 className="mt-2 text-3xl font-semibold tracking-normal">
            Listagem de chamados
          </h1>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
            Consulte, filtre e acompanhe os chamados registrados.
          </p>
          <RealtimeBadge className="mt-3" updatedAt={realtime.updatedAt} />
        </div>

        <TicketCreateDialog
          isCreating={tickets.isCreating}
          onCreateTicket={tickets.createTicket}
        />
      </header>

      <TicketFilterBar
        choices={tickets.choices}
        filters={tickets.filters}
        isRefreshing={tickets.isRefreshing}
        onChangeFilters={tickets.updateFilters}
      />

      {tickets.error ? (
        <p className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {tickets.error}
        </p>
      ) : null}

      <div>
        <TicketList
          choiceLabels={tickets.choiceLabels}
          highlightedTicketIds={realtime.highlightedIds}
          isLoading={tickets.isLoading}
          tickets={tickets.tickets}
          totalElements={tickets.totalElements}
        />

        <PaginationBar
          isDisabled={tickets.isRefreshing}
          page={tickets.filters.page ?? 0}
          totalElements={tickets.totalElements}
          totalPages={tickets.totalPages}
          onPageChange={tickets.changePage}
        />
      </div>
    </div>
  );
}
