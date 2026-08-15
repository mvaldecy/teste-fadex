"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { choicesService } from "@/src/services/choices.service";
import { toApiErrorMessage } from "@/src/services/api-error";
import { ticketsService } from "@/src/services/tickets.service";
import type {
  ChoicesResponse,
  CreateTicketRequest,
  TicketDto,
  TicketFilters,
  TicketSummary
} from "@/src/types/api";
import { buildChoiceLabelMap } from "./choice-labels";

export const initialTicketFilters: TicketFilters = {
  page: 0,
  size: 10,
  sort: "createdAt,desc"
};

type LoadTicketsOptions = {
  filters?: TicketFilters;
  preferredTicketId?: string;
};

export function useTicketWorkspace() {
  const [choices, setChoices] = useState<ChoicesResponse | null>(null);
  const [tickets, setTickets] = useState<TicketSummary[]>([]);
  const [selectedTicket, setSelectedTicket] = useState<TicketDto | null>(null);
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  const [filters, setFilters] = useState<TicketFilters>(initialTicketFilters);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const choiceLabels = useMemo(
    () => (choices ? buildChoiceLabelMap(choices) : null),
    [choices]
  );

  const loadTickets = useCallback(
    async (options?: LoadTicketsOptions) => {
      const nextFilters = options?.filters ?? filters;

      setIsRefreshing(true);
      setError(null);

      try {
        const response = await ticketsService.list(nextFilters);
        setTickets(response.content);

        setSelectedTicketId((currentTicketId) => {
          if (options?.preferredTicketId) {
            return options.preferredTicketId;
          }

          if (currentTicketId) {
            return currentTicketId;
          }

          return response.content[0]?.id ?? null;
        });
      } catch (loadError) {
        setError(toApiErrorMessage(loadError));
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [filters]
  );

  const selectTicket = useCallback((ticketId: string) => {
    setSelectedTicketId(ticketId);
  }, []);

  const updateFilters = useCallback(
    (nextFilters: TicketFilters) => {
      const normalizedFilters = {
        ...initialTicketFilters,
        ...nextFilters,
        page: 0
      };

      setFilters(normalizedFilters);
      void loadTickets({ filters: normalizedFilters });
    },
    [loadTickets]
  );

  const createTicket = useCallback(
    async (payload: CreateTicketRequest) => {
      setIsCreating(true);

      try {
        const createdTicket = await ticketsService.create(payload);
        setSelectedTicket(createdTicket);
        setSelectedTicketId(createdTicket.id);
        await loadTickets({ preferredTicketId: createdTicket.id });
        toast.success("Chamado criado.");

        return true;
      } catch (createError) {
        toast.error("Não foi possível criar o chamado.", {
          description: toApiErrorMessage(createError)
        });

        return false;
      } finally {
        setIsCreating(false);
      }
    },
    [loadTickets]
  );

  useEffect(() => {
    async function loadInitialData() {
      setIsLoading(true);
      setError(null);

      try {
        const [choicesResponse, ticketsResponse] = await Promise.all([
          choicesService.getChoices(),
          ticketsService.list(initialTicketFilters)
        ]);

        setChoices(choicesResponse);
        setTickets(ticketsResponse.content);
        setSelectedTicketId(ticketsResponse.content[0]?.id ?? null);
      } catch (loadError) {
        setError(toApiErrorMessage(loadError));
      } finally {
        setIsLoading(false);
      }
    }

    void loadInitialData();
  }, []);

  useEffect(() => {
    async function loadSelectedTicket() {
      if (!selectedTicketId) {
        setSelectedTicket(null);
        return;
      }

      try {
        const ticket = await ticketsService.getById(selectedTicketId);
        setSelectedTicket(ticket);
      } catch (loadError) {
        setError(toApiErrorMessage(loadError));
      }
    }

    void loadSelectedTicket();
  }, [selectedTicketId]);

  return {
    choices,
    choiceLabels,
    tickets,
    selectedTicket,
    selectedTicketId,
    filters,
    isLoading,
    isRefreshing,
    isCreating,
    error,
    loadTickets,
    selectTicket,
    updateFilters,
    createTicket
  };
}
