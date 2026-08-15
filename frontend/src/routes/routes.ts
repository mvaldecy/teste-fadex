import type { RoleValue } from "@/src/types/api";

export const routes = {
  login: "/login",
  changePassword: "/trocar-senha",
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

/**
 * Nome do parametro que carrega o destino pretendido ate o login.
 */
export const redirectParamName = "redirect";

/**
 * Aceita apenas caminho interno.
 *
 * Sem esta checagem o parametro viraria redirecionamento aberto: um link com
 * `?redirect=https://site-falso` levaria o usuario para fora logo apos ele
 * digitar a senha. `//host` tambem e externo para o navegador, por isso a
 * segunda barra e barrada.
 *
 * As proprias telas de autenticacao sao descartadas para nao criar laco.
 */
export function sanitizeRedirect(value: string | null | undefined) {
  if (!value || !value.startsWith("/") || value.startsWith("//")) {
    return null;
  }

  const path = value.split("?")[0];

  if (path === routes.login || path === routes.changePassword) {
    return null;
  }

  return value;
}

/**
 * Rota de login preservando o destino pretendido.
 */
export function loginRouteWithRedirect(target: string | null | undefined) {
  const safeTarget = sanitizeRedirect(target);

  if (!safeTarget) {
    return routes.login;
  }

  return `${routes.login}?${redirectParamName}=${encodeURIComponent(safeTarget)}`;
}

/**
 * Rota de troca de senha obrigatoria preservando o destino pretendido.
 */
export function changePasswordRouteWithRedirect(
  target: string | null | undefined
) {
  const safeTarget = sanitizeRedirect(target);

  if (!safeTarget) {
    return routes.changePassword;
  }

  return `${routes.changePassword}?${redirectParamName}=${encodeURIComponent(safeTarget)}`;
}
