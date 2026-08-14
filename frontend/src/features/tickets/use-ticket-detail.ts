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

  useEffect(() => {
    void loadTicket();
  }, [loadTicket]);

  return {
    choiceLabels,
    ticket,
    isLoading,
    error,
    loadTicket
  };
}
