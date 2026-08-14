"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { AppShell } from "@/src/components/layout/app-shell";
import { routes } from "@/src/routes/routes";
import { useSessionStore } from "@/src/stores/session.store";

export default function DashboardLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  const router = useRouter();
  const isHydrated = useSessionStore((state) => state.isHydrated);
  const isAuthenticated = useSessionStore((state) => state.isAuthenticated);

  useEffect(() => {
    if (isHydrated && !isAuthenticated) {
      router.replace(routes.login);
    }
  }, [isAuthenticated, isHydrated, router]);

  // Esperar a reidratacao e o que impede o redirect de disparar antes de o
  // sessionStorage carregar, que expulsaria o usuario a cada F5.
  if (!isHydrated) {
    return (
      <div className="grid min-h-screen place-items-center bg-slate-50 px-6 text-sm text-slate-500">
        Carregando sessao...
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  return <AppShell>{children}</AppShell>;
}
