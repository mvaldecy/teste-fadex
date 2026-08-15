import type { TicketStatusValue } from "@/src/types/api";

/**
 * Matriz de transicoes de status, espelhando
 * `model/ticket/TicketStatusTransition.java`.
 *
 * **Isto e duplicacao de regra do backend, e esta aqui por falta de contrato.**
 * `GET /api/v1/choices` expoe os valores do enum, mas nao as transicoes
 * validas, e sem elas a tela so tem duas saidas: oferecer transicao invalida e
 * deixar o usuario tomar 409, ou repetir a matriz. Repetir e o menor dano, com
 * o registro de que o certo e o backend publicar `allowedFrom` — a propria
 * classe Java diz que a matriz existe para "o front habilitar botoes".
 *
 * A checagem aqui e de experiencia de uso, nao de autorizacao: quem recusa a
 * transicao invalida continua sendo o backend, com `409`.
 */
const allowedTransitions: Record<TicketStatusValue, TicketStatusValue[]> = {
  ABERTO: ["EM_ANDAMENTO", "RESOLVIDO", "FECHADO"],
  EM_ANDAMENTO: ["ABERTO", "RESOLVIDO", "FECHADO"],
  RESOLVIDO: ["EM_ANDAMENTO", "FECHADO"],
  // Estado terminal: chamado fechado nao reabre.
  FECHADO: []
};

export function allowedStatusesFrom(from: TicketStatusValue) {
  return allowedTransitions[from] ?? [];
}

/**
 * O status atual entra na lista porque o seletor precisa exibir o proprio
 * valor selecionado — um `Select` sem a opcao corrente renderiza vazio.
 */
export function selectableStatusesFrom(from: TicketStatusValue) {
  return [from, ...allowedStatusesFrom(from)];
}
