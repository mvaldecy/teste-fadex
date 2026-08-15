"use client";

import { isAxiosError } from "axios";
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

  /**
   * `errorTitle` e por ação porque "Não foi possível concluir a ação" não
   * ajuda ninguem a entender o que falhou.
   *
   * O `409` recebe tratamento próprio: além da mensagem do backend — que já
   * explica a regra, seja "o chamado já possui responsável" ou "o responsável
   * precisa ter papel de administrador" — a tela recarrega. Conflito quase
   * sempre significa que o estado mudou por outra pessoa, e insistir sobre
   * dado velho so produziria o mesmo erro de novo.
   */
  const runAction = useCallback(
    async (
      action: (id: string) => Promise<unknown>,
      successMessage: string,
      errorTitle: string
    ) => {
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
        const isConflict =
          isAxiosError(actionError) && actionError.response?.status === 409;

        toast.error(errorTitle, {
          description: toApiErrorMessage(actionError)
        });

        if (isConflict) {
          onChanged();
        }

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
        "Status atualizado.",
        "Não foi possível alterar o status."
      ),
    [runAction]
  );

  const assign = useCallback(
    (assigneeId: string) =>
      runAction(
        (id) => ticketsService.assign(id, { assigneeId }),
        "Responsável atribuido.",
        "Não foi possível atribuir o responsável."
      ),
    [runAction]
  );

  const unassign = useCallback(
    () =>
      runAction(
        (id) => ticketsService.unassign(id),
        "Atribuição removida.",
        "Não foi possível remover a atribuição."
      ),
    [runAction]
  );

  /**
   * Sem otimismo de tela: cancelamento e irreversivel, entao o estado so muda
   * depois do `200` do servidor, como nas demais ações deste hook.
   */
  const cancel = useCallback(
    () =>
      runAction(
        (id) => ticketsService.cancel(id),
        "Chamado cancelado.",
        "Não foi possível cancelar o chamado."
      ),
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
        "Classificação atualizada.",
        "Não foi possível atualizar a classificação."
      ),
    [runAction]
  );

  return {
    isSubmitting,
    changeStatus,
    assign,
    unassign,
    cancel,
    updateClassification
  };
}
