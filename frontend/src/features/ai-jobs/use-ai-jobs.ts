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

const pageSize = 15;

export function useAiJobs() {
  const [jobs, setJobs] = useState<AiJobDto[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [retryingJobId, setRetryingJobId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadJobs = useCallback(async () => {
    setError(null);

    try {
      const result = await aiJobsService.list({
        page,
        size: pageSize,
        sort: "createdAt,desc"
      });

      setJobs(result.content);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (loadError) {
      setError(toApiErrorMessage(loadError));
    } finally {
      setIsLoading(false);
    }
  }, [page]);

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
    page,
    totalPages,
    totalElements,
    isLoading,
    retryingJobId,
    error,
    loadJobs,
    retryJob,
    goToPage: setPage
  };
}
