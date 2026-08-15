"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { routes } from "@/src/routes/routes";
import { useSessionStore } from "@/src/stores/session.store";

type AdminRouteGuardProps = {
  children: React.ReactNode;
};

/**
 * Guarda de rota das telas de ADMIN.
 *
 * Esconder o item do menu nao e controle de acesso: quem digita a URL chega na
 * tela e leva 403 da API, o que aparece como erro tecnico em vez de "voce nao
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
      router.replace(routes.dashboard);
    }
  }, [isAdmin, isHydrated, router]);

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
