"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { choicesService } from "@/src/services/choices.service";
import { toApiErrorMessage } from "@/src/services/api-error";
import { ticketsService } from "@/src/services/tickets.service";
import type { ChoicesResponse, TicketDto } from "@/src/types/api";
import { buildChoiceLabelMap } from "./choice-labels";

export function useTicketDetail(ticketId: string | null) {
  const [choices, setChoices] = useState<ChoicesResponse | null>(null);
  const [ticket, setTicket] = useState<TicketDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const choiceLabels = useMemo(
    () => (choices ? buildChoiceLabelMap(choices) : null),
    [choices]
  );

  const loadTicket = useCallback(async () => {
    if (!ticketId) {
      setTicket(null);
      setError("Chamado nao informado.");
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const [choicesResponse, ticketResponse] = await Promise.all([
        choicesService.getChoices(),
        ticketsService.getById(ticketId)
      ]);

      setChoices(choicesResponse);
      setTicket(ticketResponse);
    } catch (loadError) {
      setError(toApiErrorMessage(loadError));
    } finally {
      setIsLoading(false);
    }
  }, [ticketId]);

  /**
   * Recarga sem estado de carregamento, para atualizacao vinda de SSE ou de
   * uma acao. Usar `loadTicket` aqui faria o painel piscar o skeleton a cada
   * evento recebido.
   */
  const refreshTicket = useCallback(async () => {
    if (!ticketId) {
      return;
    }

    try {
      const ticketResponse = await ticketsService.getById(ticketId);
      setTicket(ticketResponse);
    } catch (loadError) {
      setError(toApiErrorMessage(loadError));
    }
  }, [ticketId]);

  useEffect(() => {
    void loadTicket();
  }, [loadTicket]);

  return {
    choices,
    choiceLabels,
    ticket,
    isLoading,
    error,
    loadTicket,
    refreshTicket
  };
}
