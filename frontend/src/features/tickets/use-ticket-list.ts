"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [filters, setFilters] = useState<TicketFilters>(initialTicketFilters);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * Sequencia da ultima requisicao disparada.
   *
   * Com filtro reativo as buscas saem em rajada e nada garante que as
   * respostas voltem na ordem em que sairam. Sem esta guarda, a resposta de
   * uma busca antiga chegando depois sobrescreveria a lista da busca atual.
   */
  const requestIdRef = useRef(0);

  const choiceLabels = useMemo(
    () => (choices ? buildChoiceLabelMap(choices) : null),
    [choices]
  );

  const loadTickets = useCallback(
    async (nextFilters = filters) => {
      requestIdRef.current += 1;
      const requestId = requestIdRef.current;

      setIsRefreshing(true);
      setError(null);

      try {
        const response = await ticketsService.list(nextFilters);

        if (requestId !== requestIdRef.current) {
          return;
        }

        setTickets(response.content);
        setTotalPages(response.totalPages);
        setTotalElements(response.totalElements);
      } catch (loadError) {
        if (requestId !== requestIdRef.current) {
          return;
        }

        setError(toApiErrorMessage(loadError));
      } finally {
        if (requestId === requestIdRef.current) {
          setIsLoading(false);
          setIsRefreshing(false);
        }
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

  /**
   * Troca de página preserva os filtros — so a página muda.
   *
   * E o oposto de `updateFilters`, que zera a página de proposito: filtrar com
   * a página 3 herdada de outra consulta cai numa faixa que o novo resultado
   * pode nem ter.
   */
  const changePage = useCallback(
    (nextPage: number) => {
      const nextFilters = { ...filters, page: nextPage };

      setFilters(nextFilters);
      void loadTickets(nextFilters);
    },
    [filters, loadTickets]
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
        requestIdRef.current += 1;
        const requestId = requestIdRef.current;

        const [choicesResponse, ticketsResponse] = await Promise.all([
          choicesService.getChoices(),
          ticketsService.list(initialTicketFilters)
        ]);

        setChoices(choicesResponse);

        if (requestId !== requestIdRef.current) {
          return;
        }

        setTickets(ticketsResponse.content);
        setTotalPages(ticketsResponse.totalPages);
        setTotalElements(ticketsResponse.totalElements);
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
    totalPages,
    totalElements,
    filters,
    isLoading,
    isRefreshing,
    isCreating,
    error,
    loadTickets,
    changePage,
    updateFilters,
    createTicket
  };
}
