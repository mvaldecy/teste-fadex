import type { NotificationEvent } from "@/src/types/api";

/**
 * O que a tela consegue extrair de um evento do stream.
 *
 * `ticketId` e nulo quando o evento não aponta para um chamado especifico —
 * `CONEXAO_ESTABELECIDA` e o caso normal, e ali a tela recarrega sem anunciar
 * mudanca, porque nada mudou: e resincronizacao de reconexao.
 *
 * A leitura e defensiva de proposito. Os payloads não tem formato único:
 * `CHAMADO_ATUALIZADO` carrega o `TicketMinDto` (campo `id`) e
 * `CLASSIFICACAO_CONCLUIDA` carrega um mapa próprio (campo `ticketId`).
 */
export type TicketEventSignal = {
  name: string;
  ticketId: string | null;
  title: string | null;
};

function readString(source: Record<string, unknown>, key: string) {
  const value = source[key];

  return typeof value === "string" && value.length > 0 ? value : null;
}

export function toTicketEventSignal(
  event: NotificationEvent
): TicketEventSignal {
  if (!event.payload || typeof event.payload !== "object") {
    return { name: event.name, ticketId: null, title: null };
  }

  const payload = event.payload as Record<string, unknown>;

  return {
    name: event.name,
    ticketId: readString(payload, "ticketId") ?? readString(payload, "id"),
    title: readString(payload, "title")
  };
}
