import { create } from "zustand";
import type { LoginFormData } from "@/src/schemas/auth.schema";

export type SessionUser = {
  email: string;
  name: string;
  role: "ADMIN" | "SOLICITANTE";
};

type SessionState = {
  user: SessionUser | null;
  isAuthenticated: boolean;
  simulateLogin: (credentials: LoginFormData) => void;
  logout: () => void;
};

function getNameFromEmail(email: string) {
  return email.split("@")[0] || "usuario";
}

function getRoleFromEmail(email: string): SessionUser["role"] {
  return email.toLowerCase().startsWith("admin") ? "ADMIN" : "SOLICITANTE";
}

export const useSessionStore = create<SessionState>()((set) => ({
  user: null,
  isAuthenticated: false,
  simulateLogin: (credentials) =>
    set({
      user: {
        email: credentials.email,
        name: getNameFromEmail(credentials.email),
        role: getRoleFromEmail(credentials.email)
      },
      isAuthenticated: true
    }),
  logout: () => set({ user: null, isAuthenticated: false })
}));
