import type { RoleValue } from "@/src/types/api";

export const routes = {
  login: "/login",
  home: "/home",
  dashboard: "/dashboard",
  tickets: "/tickets",
  ticketDetails: (ticketId: string) => `/tickets/${ticketId}`,
  users: "/usuarios",
  adminJobs: "/admin/jobs"
} as const;

/**
 * Tela inicial de cada papel.
 *
 * O dashboard e ADMIN — `GET /api/v1/indicators` responde `403` para
 * SOLICITANTE —, entao mandar todo mundo para la depois do login faria o
 * solicitante cair numa tela que a guarda devolve no instante seguinte. Para
 * ele, a casa e a lista de chamados.
 */
export function homeRouteForRole(role: RoleValue | null) {
  return role === "ADMIN" ? routes.dashboard : routes.tickets;
}
