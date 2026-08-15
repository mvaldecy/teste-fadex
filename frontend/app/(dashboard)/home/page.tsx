"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { homeRouteForRole } from "@/src/routes/routes";
import { useSessionStore } from "@/src/stores/session.store";

/**
 * Rota antiga, mantida por compatibilidade de link. O redirect virou de
 * cliente porque o destino depende do papel, e o papel so existe na sessão
 * reidratada — no servidor não ha como saber para onde mandar.
 */
export default function HomePage() {
  const router = useRouter();
  const isHydrated = useSessionStore((state) => state.isHydrated);
  const role = useSessionStore((state) => state.role);

  useEffect(() => {
    if (isHydrated) {
      router.replace(homeRouteForRole(role));
    }
  }, [isHydrated, role, router]);

  return (
    <p className="text-sm text-slate-500">Redirecionando...</p>
  );
}
