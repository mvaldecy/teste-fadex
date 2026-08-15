"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useNotifications } from "@/src/features/notifications/use-notifications";
import { buildChoiceLabelMap } from "@/src/features/tickets/choice-labels";
import { toApiErrorMessage } from "@/src/services/api-error";
import { choicesService } from "@/src/services/choices.service";
import { indicatorsService } from "@/src/services/indicators.service";
import type {
  ChoicesResponse,
  IndicatorsResponse,
  NotificationEvent
} from "@/src/types/api";

/**
 * `CONEXAO_ESTABELECIDA` entra na lista porque o stream nao faz replay: apos
 * reconectar, a tela precisa recarregar para nao mostrar o estado anterior a
 * queda.
 *
 * `CHAMADO_ALTA_PRIORIDADE` tambem recarrega, mas o **alerta** visivel nao
 * mora aqui: fica no `HighPriorityAlerts`, montado no shell, para alcancar o
 * operador em qualquer tela.
 */
const reloadEventNames = new Set([
  "CONEXAO_ESTABELECIDA",
  "INDICADORES_ATUALIZADOS",
  "CHAMADO_ATUALIZADO",
  "CHAMADO_ALTA_PRIORIDADE",
  "CLASSIFICACAO_CONCLUIDA"
]);

export function useIndicators() {
  const [choices, setChoices] = useState<ChoicesResponse | null>(null);
  const [indicators, setIndicators] = useState<IndicatorsResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const choiceLabels = useMemo(
    () => (choices ? buildChoiceLabelMap(choices) : null),
    [choices]
  );

  const loadIndicators = useCallback(async () => {
    setError(null);

    try {
      setIndicators(await indicatorsService.get());
    } catch (loadError) {
      setError(toApiErrorMessage(loadError));
    }
  }, []);

  useEffect(() => {
    async function loadInitialData() {
      setIsLoading(true);
      setError(null);

      try {
        const [choicesResponse, indicatorsResponse] = await Promise.all([
          choicesService.getChoices(),
          indicatorsService.get()
        ]);

        setChoices(choicesResponse);
        setIndicators(indicatorsResponse);
      } catch (loadError) {
        setError(toApiErrorMessage(loadError));
      } finally {
        setIsLoading(false);
      }
    }

    void loadInitialData();
  }, []);

  const handleEvent = useCallback(
    (event: NotificationEvent) => {
      if (reloadEventNames.has(event.name)) {
        void loadIndicators();
      }
    },
    [loadIndicators]
  );

  useNotifications({ enabled: true, onEvent: handleEvent });

  return {
    choiceLabels,
    indicators,
    isLoading,
    error,
    loadIndicators
  };
}
