"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { homeRouteForRole } from "@/src/routes/routes";
import { useSessionStore } from "@/src/stores/session.store";

type AdminRouteGuardProps = {
  children: React.ReactNode;
};

/**
 * Guarda de rota das telas de ADMIN.
 *
 * Esconder o item do menu não e controle de acesso: quem digita a URL chega na
 * tela e leva 403 da API, o que aparece como erro tecnico em vez de "você não
 * tem acesso". A autorizacao de verdade continua sendo do backend; aqui e so
 * evitar que o SOLICITANTE veja uma tela que nunca vai carregar.
 */
export function AdminRouteGuard({ children }: AdminRouteGuardProps) {
  const router = useRouter();
  const isHydrated = useSessionStore((state) => state.isHydrated);
  const role = useSessionStore((state) => state.role);
  const isAdmin = role === "ADMIN";

  useEffect(() => {
    if (isHydrated && !isAdmin) {
      // `homeRouteForRole` e não `routes.dashboard`: o dashboard também e uma
      // rota guardada, e devolver para la faria o não-ADMIN quicar em laco.
      router.replace(homeRouteForRole(role));
    }
  }, [isAdmin, isHydrated, role, router]);

  if (!isHydrated) {
    return null;
  }

  if (!isAdmin) {
    return (
      <p className="rounded-md border border-amber-300 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-900">
        Esta area e restrita a administradores. Redirecionando para o
        dashboard...
      </p>
    );
  }

  return <>{children}</>;
}
