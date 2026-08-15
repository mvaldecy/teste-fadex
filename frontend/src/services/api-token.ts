/**
 * Guarda o access token em memória e serve de ponto de registro para os
 * handlers de sessão.
 *
 * O interceptor de `api.ts` precisa ler o refresh token e precisa avisar a
 * store quando a sessão expira, mas importar a store criaria ciclo entre
 * modulos. A store registra os handlers aqui e o interceptor apenas os consome.
 */
let apiAccessToken: string | null = null;

export type SessionRefreshHandlers = {
  getRefreshToken: () => string | null;
  onRefreshed: (accessToken: string, refreshToken: string | null) => void;
  onSessionExpired: () => void;
};

let sessionRefreshHandlers: SessionRefreshHandlers | null = null;

export function getApiAccessToken() {
  return apiAccessToken;
}

export function setApiAccessToken(token: string | null) {
  apiAccessToken = token;
}

export function setSessionRefreshHandlers(handlers: SessionRefreshHandlers) {
  sessionRefreshHandlers = handlers;
}

export function getSessionRefreshHandlers() {
  return sessionRefreshHandlers;
}
