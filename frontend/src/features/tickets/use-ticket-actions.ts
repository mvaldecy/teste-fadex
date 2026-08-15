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
   * `errorTitle` e por acao porque "Nao foi possivel concluir a acao" nao
   * ajuda ninguem a entender o que falhou.
   *
   * O `409` recebe tratamento proprio: alem da mensagem do backend — que ja
   * explica a regra, seja "o chamado ja possui responsavel" ou "o responsavel
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
        "Nao foi possivel alterar o status."
      ),
    [runAction]
  );

  const assign = useCallback(
    (assigneeId: string) =>
      runAction(
        (id) => ticketsService.assign(id, { assigneeId }),
        "Responsavel atribuido.",
        "Nao foi possivel atribuir o responsavel."
      ),
    [runAction]
  );

  const unassign = useCallback(
    () =>
      runAction(
        (id) => ticketsService.unassign(id),
        "Atribuicao removida.",
        "Nao foi possivel remover a atribuicao."
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
        "Classificacao atualizada.",
        "Nao foi possivel atualizar a classificacao."
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
