import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import type {
  ChangePasswordFormData,
  LoginFormData
} from "@/src/schemas/auth.schema";
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
  changePassword: (payload: ChangePasswordFormData) => Promise<boolean>;
  logout: () => void;
  clearError: () => void;
  markHydrated: () => void;
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

type SessionSetter = (partial: Partial<SessionState>) => void;

/**
 * Aplica uma resposta de autenticacao a store.
 *
 * Login e troca de senha devolvem exatamente o mesmo payload, e o passo que
 * não pode ser esquecido em nenhum dos dois e o `setApiAccessToken`: o token
 * vive em memória no cliente HTTP, não na store.
 */
function applySession(set: SessionSetter, session: AuthLoginResponse) {
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
}

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
          applySession(set, await authService.login(credentials));

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
      /**
       * Troca de senha obrigatoria.
       *
       * A resposta e uma sessão nova e completa — inclusive com o
       * `refreshToken`, que o login com `mustChangePassword` devolve nulo.
       * Aplicar a sessão inteira aqui e o que troca o token limitado pelo
       * token normal; sem isso todo endpoint seguinte responderia `403`.
       *
       * O erro não limpa a sessão: senha atual errada não pode expulsar o
       * usuário da tela em que ele precisa continuar.
       */
      changePassword: async (payload) => {
        set({ error: null, isLoading: true });

        try {
          applySession(set, await authService.changePassword(payload));

          return true;
        } catch (error) {
          set({
            error: toApiErrorMessage(error),
            isLoading: false
          });

          return false;
        }
      },
      logout: () => {
        // Encerra o stream antes de limpar o token: sem isto o cliente SSE
        // continuaria tentando reconectar com a sessão já encerrada.
        stopNotificationsStream();
        setApiAccessToken(null);
        set({
          ...clearedSession,
          error: null,
          isLoading: false
        });
      },
      clearError: () => set({ error: null }),
      // Ação própria em vez de `useSessionStore.setState` no
      // `onRehydrateStorage`: para storage sincrono o zustand roda a
      // reidratacao inteira **dentro** do `create`, quando a const
      // `useSessionStore` ainda esta na zona morta temporal. Referencia-la ali
      // lancava `ReferenceError`, que o thenable interno engolia — a sessão
      // reidratava, `isHydrated` ficava `false` e a guarda de rota travava em
      // "Carregando sessão..." para sempre. Verificado no navegador.
      markHydrated: () => set({ isHydrated: true })
    }),
    {
      name: sessionStorageKey,
      // `sessionStorage` e não `localStorage`: o token expira em uma hora e
      // manter a sessão viva entre sessoes do navegador so aumentaria a janela
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
      // `initialState` e o estado que o zustand entrega antes de ler o
      // storage. Ele importa no ramo de erro: quando a leitura falha, `state`
      // vem indefinido, e sem um fallback a guarda de rota ficaria travada em
      // "Carregando sessão..." exatamente no cenario em que o usuário mais
      // precisa chegar ao login.
      onRehydrateStorage: (initialState) => (state, error) => {
        const settledState = state ?? initialState;

        // O token vive em memória no cliente HTTP; reidratar a store sem
        // reidratar o cliente deixaria a sessão valida na UI e ausente na API.
        setApiAccessToken(state?.accessToken ?? null);

        if (error) {
          console.error("Falha ao reidratar a sessão.", error);
        }

        settledState?.markHydrated();
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
