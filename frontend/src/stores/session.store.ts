import { create } from "zustand";
import type { LoginFormData } from "@/src/schemas/auth.schema";
import { toApiErrorMessage } from "@/src/services/api-error";
import { setApiAccessToken } from "@/src/services/api-token";
import { authService } from "@/src/services/auth.service";
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
  tokenType: AuthLoginResponse["tokenType"] | null;
  expiresIn: number | null;
  isAuthenticated: boolean;
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
  tokenType: null,
  expiresIn: null,
  isAuthenticated: false
};

export const useSessionStore = create<SessionState>()((set) => ({
  ...clearedSession,
  isLoading: false,
  error: null,
  login: async (credentials) => {
    set({ error: null, isLoading: true });

    try {
      const session = await authService.login(credentials);

      setApiAccessToken(session.accessToken);
      set({
        accessToken: session.accessToken,
        tokenType: session.tokenType,
        expiresIn: session.expiresIn,
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
    setApiAccessToken(null);
    set({
      ...clearedSession,
      error: null,
      isLoading: false
    });
  },
  clearError: () => set({ error: null })
}));
