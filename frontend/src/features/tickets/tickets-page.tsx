"use client";

import { TicketCreateDialog } from "./ticket-create-dialog";
import { TicketFilterBar } from "./ticket-filter-bar";
import { TicketList } from "./ticket-list";
import { useTicketEvents } from "./use-ticket-events";
import { useTicketList } from "./use-ticket-list";

export function TicketsPage() {
  const tickets = useTicketList();

  useTicketEvents({
    enabled: false,
    onTicketChanged: () => void tickets.loadTickets(),
    onCommentChanged: () => void tickets.loadTickets()
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

      <TicketList
        choiceLabels={tickets.choiceLabels}
        isLoading={tickets.isLoading}
        tickets={tickets.tickets}
      />
    </div>
  );
}
