"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { AppShell } from "@/src/components/layout/app-shell";
import {
  changePasswordRouteWithRedirect,
  loginRouteWithRedirect
} from "@/src/routes/routes";
import { useSessionStore } from "@/src/stores/session.store";

export default function DashboardLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  const router = useRouter();
  const pathname = usePathname();
  const isHydrated = useSessionStore((state) => state.isHydrated);
  const isAuthenticated = useSessionStore((state) => state.isAuthenticated);
  const mustChangePassword = useSessionStore(
    (state) => state.mustChangePassword
  );

  useEffect(() => {
    if (!isHydrated) {
      return;
    }

    // O destino pretendido segue junto: sem ele, quem abre o link de um
    // chamado sem sessão autentica e cai no dashboard, perdendo o chamado.
    //
    // A busca vem de `window` e não de `useSearchParams` de proposito: o hook
    // no layout tiraria **todas** as telas do grupo da pre-renderizacao
    // estatica. Aqui a leitura acontece so no cliente, dentro do efeito.
    const intendedRoute = `${pathname}${window.location.search}`;

    if (!isAuthenticated) {
      router.replace(loginRouteWithRedirect(intendedRoute));
      return;
    }

    // Senha provisoria: o token do login so abre o endpoint de troca, entao
    // qualquer tela daqui responderia `403`.
    if (mustChangePassword) {
      router.replace(changePasswordRouteWithRedirect(intendedRoute));
    }
  }, [
    isAuthenticated,
    isHydrated,
    mustChangePassword,
    pathname,
    router
  ]);

  // Esperar a reidratacao e o que impede o redirect de disparar antes de o
  // sessionStorage carregar, que expulsaria o usuário a cada F5.
  if (!isHydrated) {
    return (
      <div className="grid min-h-screen place-items-center bg-slate-50 px-6 text-sm text-slate-500">
        Carregando sessão...
      </div>
    );
  }

  if (!isAuthenticated || mustChangePassword) {
    return null;
  }

  return <AppShell>{children}</AppShell>;
}
