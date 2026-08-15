import type { TicketStatusValue } from "@/src/types/api";
import { api } from "./api";

/**
 * Matriz de transicoes de status, por status de origem.
 *
 * `Partial` de proposito: um status novo no backend chega aqui antes de existir
 * no tipo do front, e ler `transitions[status]` de uma chave ausente precisa
 * ser um caso previsto, nao um `undefined` disfarcado de array.
 */
export type TicketStatusTransitions = Partial<
  Record<TicketStatusValue, TicketStatusValue[]>
>;

/**
 * Fonte unica da regra de fluxo: e a mesma matriz que o service usa para
 * recusar transicao invalida com `409`. A tela le daqui em vez de repetir a
 * regra e sair de sincronia.
 */
async function get() {
  const response = await api.get<TicketStatusTransitions>(
    "/ticket-status-transitions"
  );

  return response.data;
}

export const ticketStatusTransitionsService = { get };
