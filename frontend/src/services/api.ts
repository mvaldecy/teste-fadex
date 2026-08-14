import axios, { type InternalAxiosRequestConfig } from "axios";
import { getPublicEnv } from "@/src/config/public-env";
import type { AuthLoginResponse } from "@/src/types/api";
import {
  getApiAccessToken,
  getSessionRefreshHandlers,
  setApiAccessToken
} from "./api-token";

const publicEnv = getPublicEnv();

export const api = axios.create({
  baseURL: publicEnv.apiBaseUrl,
  headers: {
    Accept: "application/json"
  }
});

api.interceptors.request.use((config) => {
  const token = getApiAccessToken();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

type RetriableRequestConfig = InternalAxiosRequestConfig & {
  _hasRetried?: boolean;
};

/**
 * Promessa compartilhada de refresh.
 *
 * Sem ela, uma tela com varias requisicoes em paralelo recebendo `401` dispara
 * um refresh para cada uma e o proprio token e invalidado em cascata. Todas as
 * chamadas que falharem na mesma janela esperam o mesmo refresh.
 */
let refreshPromise: Promise<string | null> | null = null;

/**
 * O refresh usa `axios` puro em vez do `authService`, que importa este modulo.
 * Chamar o service aqui fecharia um ciclo de import entre os dois arquivos.
 */
async function requestRefreshedToken(refreshToken: string) {
  const response = await axios.post<AuthLoginResponse>(
    `${publicEnv.apiBaseUrl}/auth/refresh`,
    { refreshToken },
    { headers: { Accept: "application/json" } }
  );

  return response.data;
}

async function runRefresh() {
  const handlers = getSessionRefreshHandlers();
  const refreshToken = handlers?.getRefreshToken() ?? null;

  if (!handlers || !refreshToken) {
    return null;
  }

  try {
    const session = await requestRefreshedToken(refreshToken);

    setApiAccessToken(session.accessToken);
    handlers.onRefreshed(session.accessToken, session.refreshToken);

    return session.accessToken;
  } catch {
    return null;
  }
}

/**
 * Exposto para o cliente SSE, que usa `fetch` puro e por isso nao passa pelo
 * interceptor. Sem isto, o stream morre no vencimento do token e nunca mais
 * volta, enquanto as chamadas REST seguem renovando normalmente.
 */
export async function refreshAccessToken() {
  refreshPromise = refreshPromise ?? runRefresh();
  const nextToken = await refreshPromise;
  refreshPromise = null;

  return nextToken;
}

api.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error) || error.response?.status !== 401) {
      return Promise.reject(error);
    }

    const originalRequest = error.config as RetriableRequestConfig | undefined;

    // Requisicoes de auth nao entram no ciclo de refresh: um 401 no proprio
    // login ou refresh e resposta legitima, nao sessao expirada.
    if (
      !originalRequest ||
      originalRequest._hasRetried ||
      originalRequest.url?.includes("/auth/")
    ) {
      return Promise.reject(error);
    }

    originalRequest._hasRetried = true;

    const nextToken = await refreshAccessToken();

    if (!nextToken) {
      getSessionRefreshHandlers()?.onSessionExpired();
      return Promise.reject(error);
    }

    originalRequest.headers.Authorization = `Bearer ${nextToken}`;

    return api(originalRequest);
  }
);
