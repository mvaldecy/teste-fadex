import type { AuthLoginRequest, AuthLoginResponse } from "@/src/types/api";
import { api } from "./api";

async function login(payload: AuthLoginRequest) {
  const response = await api.post<AuthLoginResponse>("/auth/login", payload);
  return response.data;
}

export const authService = {
  login
};
