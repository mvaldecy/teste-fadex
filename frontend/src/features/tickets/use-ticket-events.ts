"use client";

import { useCallback } from "react";
import { useNotifications } from "@/src/features/notifications/use-notifications";
import type { NotificationEvent } from "@/src/types/api";

type UseTicketEventsOptions = {
  enabled: boolean;
  onTicketChanged: () => void;
  onCommentChanged: () => void;
};

/**
 * `CONEXAO_ESTABELECIDA` entra na lista de proposito. O contrato nao faz
 * replay de `Last-Event-ID`, entao reconectar sem recarregar deixaria a tela
 * exibindo o estado anterior a queda.
 */
const ticketEventNames = new Set([
  "CONEXAO_ESTABELECIDA",
  "CHAMADO_ATUALIZADO",
  "CHAMADO_ALTA_PRIORIDADE",
  "CLASSIFICACAO_CONCLUIDA"
]);

export function useTicketEvents({
  enabled,
  onTicketChanged,
  onCommentChanged
}: UseTicketEventsOptions) {
  const handleEvent = useCallback(
    (event: NotificationEvent) => {
      if (!ticketEventNames.has(event.name)) {
        return;
      }

      onTicketChanged();
      onCommentChanged();
    },
    [onCommentChanged, onTicketChanged]
  );

  useNotifications({ enabled, onEvent: handleEvent });
}
