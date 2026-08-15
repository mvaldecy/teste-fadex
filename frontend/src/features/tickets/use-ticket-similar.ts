"use client";

import { useCallback, useEffect, useState } from "react";
import { toApiErrorMessage } from "@/src/services/api-error";
import { ticketsService } from "@/src/services/tickets.service";
import type { NearestTicketDto, SimilarTicketDto } from "@/src/types/api";

/**
 * Chamados semelhantes do detalhe.
 *
 * `enabled` existe porque o endpoint e restrito a ADMIN: para os demais papeis
 * a aba nem aparece, e pedir o dado so produziria um `403` no console.
 */
export function useTicketSimilar(ticketId: string | null, enabled: boolean) {
  const [similarTickets, setSimilarTickets] = useState<SimilarTicketDto[]>([]);
  const [nearestTickets, setNearestTickets] = useState<NearestTicketDto[]>([]);
  const [isLoading, setIsLoading] = useState(enabled);
  const [error, setError] = useState<string | null>(null);

  const loadSimilar = useCallback(async () => {
    if (!ticketId || !enabled) {
      return;
    }

    setError(null);

    try {
      // As duas listas juntas: a de vinculos diz o que o sistema afirma ser
      // duplicata, o ranking diz o que ele considerou e descartou. Pedir so a
      // primeira e o que fazia a aba vazia parecer defeito.
      const [similar, nearest] = await Promise.all([
        ticketsService.listSimilar(ticketId),
        ticketsService.listNearest(ticketId)
      ]);

      setSimilarTickets(similar);
      setNearestTickets(nearest);
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
    nearestTickets,
    isLoading,
    error,
    loadSimilar
  };
}
