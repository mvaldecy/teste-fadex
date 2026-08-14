import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import type { LoginFormData } from "@/src/schemas/auth.schema";
import { toApiErrorMessage } from "@/src/services/api-error";
import {
  setApiAccessToken,
  setSessionRefreshHandlers
} from "@/src/services/api-token";
import { authService } from "@/src/services/auth.service";
import { stopNotificationsStream } from "@/src/services/notifications-stream";
import type {
  AuthLoginResponse,
  AuthenticatedUser,
  RoleValue
} from "@/src/types/api";

export type SessionUser = AuthenticatedUser;

type SessionState = {
  user: SessionUser | null;
  role: RoleValue | null;
  accessToken: string | null;
  refreshToken: string | null;
  tokenType: AuthLoginResponse["tokenType"] | null;
  expiresIn: number | null;
  mustChangePassword: boolean;
  isAuthenticated: boolean;
  isHydrated: boolean;
  isLoading: boolean;
  error: string | null;
  login: (credentials: LoginFormData) => Promise<boolean>;
  logout: () => void;
  clearError: () => void;
};

const clearedSession = {
  user: null,
  role: null,
  accessToken: null,
  refreshToken: null,
  tokenType: null,
  expiresIn: null,
  mustChangePassword: false,
  isAuthenticated: false
};

const sessionStorageKey = "fadex-helpdesk-session";

export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      ...clearedSession,
      isHydrated: false,
      isLoading: false,
      error: null,
      login: async (credentials) => {
        set({ error: null, isLoading: true });

        try {
          const session = await authService.login(credentials);

          setApiAccessToken(session.accessToken);
          set({
            accessToken: session.accessToken,
            refreshToken: session.refreshToken,
            tokenType: session.tokenType,
            expiresIn: session.expiresIn,
            mustChangePassword: session.mustChangePassword,
            role: session.role,
            user: session.user,
            isAuthenticated: true,
            isLoading: false
          });

          return true;
        } catch (error) {
          setApiAccessToken(null);
          set({
            ...clearedSession,
            error: toApiErrorMessage(error),
            isLoading: false
          });

          return false;
        }
      },
      logout: () => {
        // Encerra o stream antes de limpar o token: sem isto o cliente SSE
        // continuaria tentando reconectar com a sessao ja encerrada.
        stopNotificationsStream();
        setApiAccessToken(null);
        set({
          ...clearedSession,
          error: null,
          isLoading: false
        });
      },
      clearError: () => set({ error: null })
    }),
    {
      name: sessionStorageKey,
      // `sessionStorage` e nao `localStorage`: o token expira em uma hora e
      // manter a sessao viva entre sessoes do navegador so aumentaria a janela
      // de exposicao. A funcao adiada evita tocar em `window` no prerender.
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        user: state.user,
        role: state.role,
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        tokenType: state.tokenType,
        expiresIn: state.expiresIn,
        mustChangePassword: state.mustChangePassword,
        isAuthenticated: state.isAuthenticated
      }),
      onRehydrateStorage: () => (state) => {
        // O token vive em memoria no cliente HTTP; reidratar a store sem
        // reidratar o cliente deixaria a sessao valida na UI e ausente na API.
        setApiAccessToken(state?.accessToken ?? null);
        useSessionStore.setState({ isHydrated: true });
      }
    }
  )
);

setSessionRefreshHandlers({
  getRefreshToken: () => useSessionStore.getState().refreshToken,
  onRefreshed: (accessToken, refreshToken) =>
    useSessionStore.setState({ accessToken, refreshToken }),
  onSessionExpired: () => useSessionStore.getState().logout()
});
