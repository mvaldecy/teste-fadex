"use client";

import { useCallback } from "react";
import { useNotifications } from "@/src/features/notifications/use-notifications";
import type { NotificationEvent } from "@/src/types/api";
import {
  type TicketEventSignal,
  toTicketEventSignal
} from "./ticket-event-signal";

type UseTicketEventsOptions = {
  enabled: boolean;
  onTicketChanged: (signal: TicketEventSignal) => void;
  onCommentChanged: (signal: TicketEventSignal) => void;
};

/**
 * `CONEXAO_ESTABELECIDA` entra na lista de proposito. O contrato não faz
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

      // O sinal segue para a tela: sem o id do chamado no callback, a
      // atualizacao acontece em silencio e o usuário não percebe que a tela
      // reagiu — que era exatamente o problema relatado.
      const signal = toTicketEventSignal(event);

      onTicketChanged(signal);
      onCommentChanged(signal);
    },
    [onCommentChanged, onTicketChanged]
  );

  useNotifications({ enabled, onEvent: handleEvent });
}
