"use client";

import { useRouter } from "next/navigation";
import { useCallback } from "react";
import { toast } from "sonner";
import { routes } from "@/src/routes/routes";
import type { NotificationEvent } from "@/src/types/api";
import { useNotifications } from "./use-notifications";

type HighPriorityPayload = {
  id?: string;
  title?: string;
};

/**
 * O payload do evento e o `TicketMinDto`, mas o contrato do stream nao promete
 * campo nenhum, entao a leitura e defensiva: sem `id` o alerta ainda aparece,
 * so sem o atalho para o chamado.
 */
function readPayload(payload: unknown): HighPriorityPayload {
  if (!payload || typeof payload !== "object") {
    return {};
  }

  const { id, title } = payload as Record<string, unknown>;

  return {
    id: typeof id === "string" ? id : undefined,
    title: typeof title === "string" ? title : undefined
  };
}

/**
 * Alerta de chamado com prioridade ALTA — requisito obrigatorio do desafio.
 *
 * Fica montado no shell, e nao numa tela: o backend emite
 * `CHAMADO_ALTA_PRIORIDADE` para o publico de ADMIN assim que a prioridade
 * passa a ser ALTA (classificacao da IA ou revisao manual), e o alerta precisa
 * chegar ao operador esteja ele em qual pagina estiver.
 */
export function HighPriorityAlerts() {
  const router = useRouter();

  const handleEvent = useCallback(
    (event: NotificationEvent) => {
      if (event.name !== "CHAMADO_ALTA_PRIORIDADE") {
        return;
      }

      const { id, title } = readPayload(event.payload);

      toast.warning("Chamado de prioridade ALTA", {
        description: title ?? "Um chamado passou para prioridade alta.",
        duration: 15000,
        action: id
          ? {
              label: "Abrir",
              onClick: () => router.push(routes.ticketDetails(id))
            }
          : undefined
      });
    },
    [router]
  );

  useNotifications({ enabled: true, onEvent: handleEvent });

  return null;
}
