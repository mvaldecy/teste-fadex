"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { aiJobsService } from "@/src/services/ai-jobs.service";
import { toApiErrorMessage } from "@/src/services/api-error";
import { ticketsService } from "@/src/services/tickets.service";

/**
 * Solicitacao manual de triagem por IA.
 *
 * A tela consulta `GET /ai/jobs?ticketId=` para saber se ja ha job ativo. A
 * guarda do backend e **por tipo**: `409` so quando nenhum dos dois tipos pode
 * ser enfileirado. Por isso a checagem daqui e a mesma — havendo job ativo de
 * classificacao **e** de embedding, o botao fica desabilitado com o motivo
 * visivel, em vez de deixar o usuario tomar o conflito.
 *
 * Isto e experiencia de uso, nao autorizacao nem exclusao mutua real: entre a
 * leitura e o clique o estado pode mudar, e quem decide continua sendo o
 * backend.
 */
const activeStatuses = new Set(["PENDING", "PROCESSING"]);
const jobTypes = ["CLASSIFICATION", "EMBEDDING"] as const;

export function useTicketTriage(
  ticketId: string | null,
  enabled: boolean,
  onRequested: () => void
) {
  const [activeTypes, setActiveTypes] = useState<Set<string>>(new Set());
  const [isRequesting, setIsRequesting] = useState(false);

  const loadActiveJobs = useCallback(async () => {
    if (!ticketId || !enabled) {
      return;
    }

    try {
      const response = await aiJobsService.list({
        ticketId,
        page: 0,
        size: 20,
        sort: "createdAt,desc"
      });

      setActiveTypes(
        new Set(
          response.content
            .filter((job) => activeStatuses.has(job.status))
            .map((job) => job.type)
        )
      );
    } catch {
      // Nao saber o estado da fila nao pode bloquear a acao: sem o dado, o
      // botao segue habilitado e o backend responde por si.
      setActiveTypes(new Set());
    }
  }, [enabled, ticketId]);

  useEffect(() => {
    void loadActiveJobs();
  }, [loadActiveJobs]);

  const hasTriageInProgress = jobTypes.every((type) => activeTypes.has(type));

  const requestTriage = useCallback(async () => {
    if (!ticketId) {
      return false;
    }

    setIsRequesting(true);

    try {
      const jobs = await ticketsService.requestAiTriage(ticketId);

      // `202`: enfileirou, nao concluiu. O texto do toast evita prometer
      // resultado imediato — quem conclui e o worker.
      toast.success("Triagem enfileirada.", {
        description:
          jobs.length === 1
            ? "1 job foi criado. O resultado aparece assim que o worker processar."
            : `${jobs.length} jobs foram criados. O resultado aparece assim que o worker processar.`
      });

      await loadActiveJobs();
      onRequested();

      return true;
    } catch (requestError) {
      toast.error("Nao foi possivel solicitar a triagem.", {
        description: toApiErrorMessage(requestError)
      });

      // Conflito significa fila ocupada: reler o estado desabilita o botao em
      // vez de deixar o usuario repetir o erro.
      await loadActiveJobs();

      return false;
    } finally {
      setIsRequesting(false);
    }
  }, [loadActiveJobs, onRequested, ticketId]);

  return {
    hasTriageInProgress,
    isRequesting,
    requestTriage,
    loadActiveJobs
  };
}
