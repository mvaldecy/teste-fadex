"use client";

import { useCallback, useState } from "react";
import { toast } from "sonner";
import { toApiErrorMessage } from "@/src/services/api-error";
import { ticketsService } from "@/src/services/tickets.service";
import type {
  TicketCategoryValue,
  TicketPriorityValue,
  TicketStatusValue
} from "@/src/types/api";

export function useTicketActions(
  ticketId: string | null,
  onChanged: () => void
) {
  const [isSubmitting, setIsSubmitting] = useState(false);

  const runAction = useCallback(
    async (action: (id: string) => Promise<unknown>, successMessage: string) => {
      if (!ticketId) {
        return false;
      }

      setIsSubmitting(true);

      try {
        await action(ticketId);
        toast.success(successMessage);
        onChanged();

        return true;
      } catch (actionError) {
        toast.error("Nao foi possivel concluir a acao.", {
          description: toApiErrorMessage(actionError)
        });

        return false;
      } finally {
        setIsSubmitting(false);
      }
    },
    [onChanged, ticketId]
  );

  const changeStatus = useCallback(
    (status: TicketStatusValue) =>
      runAction(
        (id) => ticketsService.updateStatus(id, { status }),
        "Status atualizado."
      ),
    [runAction]
  );

  const assign = useCallback(
    (assigneeId: string) =>
      runAction(
        (id) => ticketsService.assign(id, { assigneeId }),
        "Responsavel atribuido."
      ),
    [runAction]
  );

  const unassign = useCallback(
    () =>
      runAction((id) => ticketsService.unassign(id), "Atribuicao removida."),
    [runAction]
  );

  const updateClassification = useCallback(
    (
      category: TicketCategoryValue,
      priority: TicketPriorityValue,
      classificationJustification?: string
    ) =>
      runAction(
        (id) =>
          ticketsService.updateClassification(id, {
            category,
            priority,
            classificationJustification:
              classificationJustification?.trim() || undefined
          }),
        "Classificacao atualizada."
      ),
    [runAction]
  );

  return {
    isSubmitting,
    changeStatus,
    assign,
    unassign,
    updateClassification
  };
}
