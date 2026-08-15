import type {
  AuthChangePasswordRequest,
  AuthLoginRequest,
  AuthLoginResponse,
  AuthRefreshRequest
} from "@/src/types/api";
import { api } from "./api";

async function login(payload: AuthLoginRequest) {
  const response = await api.post<AuthLoginResponse>("/auth/login", payload);
  return response.data;
}

async function refresh(payload: AuthRefreshRequest) {
  const response = await api.post<AuthLoginResponse>("/auth/refresh", payload);
  return response.data;
}

/**
 * Troca de senha obrigatoria. Usa o token limitado devolvido no login: ele so
 * abre este endpoint, e a resposta e uma sessao completa, ja com refresh
 * token.
 */
async function changePassword(payload: AuthChangePasswordRequest) {
  const response = await api.post<AuthLoginResponse>(
    "/auth/change-password",
    payload
  );

  return response.data;
}

export const authService = {
  changePassword,
  login,
  refresh
};
