"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { useNotifications } from "@/src/features/notifications/use-notifications";
import { aiJobsService } from "@/src/services/ai-jobs.service";
import { toApiErrorMessage } from "@/src/services/api-error";
import type { AiJobDto, NotificationEvent } from "@/src/types/api";

const reloadEventNames = new Set([
  "CONEXAO_ESTABELECIDA",
  "JOB_IA_FALHOU",
  "CLASSIFICACAO_CONCLUIDA"
]);

export function useAiJobs() {
  const [jobs, setJobs] = useState<AiJobDto[]>([]);
  const [isFixture, setIsFixture] = useState(false);
  const [fixtureReason, setFixtureReason] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [retryingJobId, setRetryingJobId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadJobs = useCallback(async () => {
    setError(null);

    try {
      const result = await aiJobsService.list({
        page: 0,
        size: 30,
        sort: "createdAt,desc"
      });

      setJobs(result.data);
      setIsFixture(result.isFixture);
      setFixtureReason(result.fixtureReason);
    } catch (loadError) {
      setError(toApiErrorMessage(loadError));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadJobs();
  }, [loadJobs]);

  const retryJob = useCallback(
    async (jobId: string) => {
      setRetryingJobId(jobId);

      try {
        await aiJobsService.retry(jobId);
        toast.success("Job reenviado para processamento.");
        await loadJobs();

        return true;
      } catch (retryError) {
        toast.error("Nao foi possivel reprocessar o job.", {
          description: toApiErrorMessage(retryError)
        });

        return false;
      } finally {
        setRetryingJobId(null);
      }
    },
    [loadJobs]
  );

  const handleEvent = useCallback(
    (event: NotificationEvent) => {
      if (event.name === "JOB_IA_FALHOU") {
        toast.error("Um job de IA falhou.");
      }

      if (reloadEventNames.has(event.name)) {
        void loadJobs();
      }
    },
    [loadJobs]
  );

  useNotifications({ enabled: true, onEvent: handleEvent });

  return {
    jobs,
    isFixture,
    fixtureReason,
    isLoading,
    retryingJobId,
    error,
    loadJobs,
    retryJob
  };
}
