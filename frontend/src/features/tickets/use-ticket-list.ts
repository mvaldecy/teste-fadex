"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { choicesService } from "@/src/services/choices.service";
import { toApiErrorMessage } from "@/src/services/api-error";
import { ticketsService } from "@/src/services/tickets.service";
import type {
  ChoicesResponse,
  CreateTicketRequest,
  TicketFilters,
  TicketSummary
} from "@/src/types/api";
import { buildChoiceLabelMap } from "./choice-labels";
import { initialTicketFilters } from "./use-ticket-workspace";

export function useTicketList() {
  const [choices, setChoices] = useState<ChoicesResponse | null>(null);
  const [tickets, setTickets] = useState<TicketSummary[]>([]);
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
    async (nextFilters = filters) => {
      setIsRefreshing(true);
      setError(null);

      try {
        const response = await ticketsService.list(nextFilters);
        setTickets(response.content);
      } catch (loadError) {
        setError(toApiErrorMessage(loadError));
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [filters]
  );

  const updateFilters = useCallback(
    (nextFilters: TicketFilters) => {
      const normalizedFilters = {
        ...initialTicketFilters,
        ...nextFilters,
        page: 0
      };

      setFilters(normalizedFilters);
      void loadTickets(normalizedFilters);
    },
    [loadTickets]
  );

  const createTicket = useCallback(
    async (payload: CreateTicketRequest) => {
      setIsCreating(true);

      try {
        await ticketsService.create(payload);
        await loadTickets();
        toast.success("Chamado criado.");

        return true;
      } catch (createError) {
        toast.error("Nao foi possivel criar o chamado.", {
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
      } catch (loadError) {
        setError(toApiErrorMessage(loadError));
      } finally {
        setIsLoading(false);
      }
    }

    void loadInitialData();
  }, []);

  return {
    choices,
    choiceLabels,
    tickets,
    filters,
    isLoading,
    isRefreshing,
    isCreating,
    error,
    loadTickets,
    updateFilters,
    createTicket
  };
}
