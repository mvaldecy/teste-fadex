"use client";

import { useCallback, useEffect, useState } from "react";
import { toApiErrorMessage } from "@/src/services/api-error";
import { ticketsService } from "@/src/services/tickets.service";
import type { SimilarTicketDto } from "@/src/types/api";

/**
 * Chamados semelhantes do detalhe.
 *
 * `enabled` existe porque o endpoint e restrito a ADMIN: para os demais papeis
 * a aba nem aparece, e pedir o dado so produziria um `403` no console.
 */
export function useTicketSimilar(ticketId: string | null, enabled: boolean) {
  const [similarTickets, setSimilarTickets] = useState<SimilarTicketDto[]>([]);
  const [isLoading, setIsLoading] = useState(enabled);
  const [error, setError] = useState<string | null>(null);

  const loadSimilar = useCallback(async () => {
    if (!ticketId || !enabled) {
      return;
    }

    setError(null);

    try {
      setSimilarTickets(await ticketsService.listSimilar(ticketId));
    } catch (loadError) {
      setError(toApiErrorMessage(loadError));
    } finally {
      setIsLoading(false);
    }
  }, [enabled, ticketId]);

  useEffect(() => {
    void loadSimilar();
  }, [loadSimilar]);

  return {
    similarTickets,
    isLoading,
    error,
    loadSimilar
  };
}
