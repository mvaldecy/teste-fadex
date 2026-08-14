"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
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

const reloadEventNames = new Set([
  "CONEXAO_ESTABELECIDA",
  "INDICADORES_ATUALIZADOS",
  "CHAMADO_ATUALIZADO",
  "CLASSIFICACAO_CONCLUIDA"
]);

export function useIndicators() {
  const [choices, setChoices] = useState<ChoicesResponse | null>(null);
  const [indicators, setIndicators] = useState<IndicatorsResponse | null>(null);
  const [isFixture, setIsFixture] = useState(false);
  const [fixtureReason, setFixtureReason] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const choiceLabels = useMemo(
    () => (choices ? buildChoiceLabelMap(choices) : null),
    [choices]
  );

  const loadIndicators = useCallback(async () => {
    setError(null);

    try {
      const result = await indicatorsService.get();
      setIndicators(result.data);
      setIsFixture(result.isFixture);
      setFixtureReason(result.fixtureReason);
    } catch (loadError) {
      setError(toApiErrorMessage(loadError));
    }
  }, []);

  useEffect(() => {
    async function loadInitialData() {
      setIsLoading(true);
      setError(null);

      try {
        const [choicesResponse, indicatorsResult] = await Promise.all([
          choicesService.getChoices(),
          indicatorsService.get()
        ]);

        setChoices(choicesResponse);
        setIndicators(indicatorsResult.data);
        setIsFixture(indicatorsResult.isFixture);
        setFixtureReason(indicatorsResult.fixtureReason);
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
      // Alerta de prioridade ALTA e requisito obrigatorio do desafio: alem de
      // recarregar, precisa avisar o ADMIN na hora.
      if (event.name === "CHAMADO_ALTA_PRIORIDADE") {
        toast.warning("Chamado de prioridade ALTA registrado.");
        void loadIndicators();
        return;
      }

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
    isFixture,
    fixtureReason,
    isLoading,
    error,
    loadIndicators
  };
}
