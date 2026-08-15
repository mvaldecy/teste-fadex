"use client";

import { RefreshCw } from "lucide-react";
import Link from "next/link";
import { PaginationBar } from "@/src/components/layout/pagination-bar";
import { Badge } from "@/src/components/ui/badge";
import { Button } from "@/src/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle
} from "@/src/components/ui/card";
import { Skeleton } from "@/src/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/src/components/ui/table";
import { routes } from "@/src/routes/routes";
import type { AiJobDto } from "@/src/types/api";
import {
  aiJobStatusLabels,
  aiJobTypeLabels,
  resolveAiJobLabel
} from "./ai-job-labels";
import { useAiJobs } from "./use-ai-jobs";

function formatDate(value?: string | null) {
  if (!value) {
    return "--";
  }

  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(new Date(value));
}

function StatusBadge({ status }: { status: string }) {
  return (
    <Badge variant={status === "FAILED" ? "destructive" : "outline"}>
      {resolveAiJobLabel(aiJobStatusLabels, status)}
    </Badge>
  );
}

export function AiJobsPage() {
  const {
    jobs,
    page,
    totalPages,
    totalElements,
    isLoading,
    retryingJobId,
    error,
    retryJob,
    goToPage
  } = useAiJobs();

  function renderRetryButton(job: AiJobDto) {
    // Verificado contra o backend: reprocessar job que não esta em FAILED
    // responde 409 ("Apenas jobs com falha podem ser retentados"). Oferecer o
    // botao ativo convidaria a uma ação que a API sempre recusa.
    const canRetry = job.status === "FAILED";

    return (
      <Button
        disabled={retryingJobId === job.id || !canRetry}
        size="sm"
        title={canRetry ? undefined : "Apenas jobs com falha são retentados."}
        type="button"
        variant="outline"
        onClick={() => retryJob(job.id)}
      >
        <RefreshCw className="h-4 w-4" />
        {retryingJobId === job.id ? "Reprocessando..." : "Reprocessar"}
      </Button>
    );
  }

  return (
    <div className="mx-auto grid max-w-7xl gap-6">
      <header className="border-b border-slate-200 pb-5">
        <p className="text-sm font-semibold uppercase tracking-[0.12em] text-emerald-700">
          Administracao
        </p>
        <h1 className="mt-2 text-3xl font-semibold tracking-normal">
          Jobs de IA
        </h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
          Fila de classificação e embedding, com reprocessamento manual.
        </p>
      </header>

      {error ? (
        <p className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {error}
        </p>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Fila de jobs</CardTitle>
          <CardDescription>
            {isLoading
              ? "Carregando jobs."
              : `${totalElements} jobs encontrados.`}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="grid gap-3">
              {Array.from({ length: 3 }).map((_, index) => (
                <Skeleton className="h-16 rounded-md" key={index} />
              ))}
            </div>
          ) : jobs.length === 0 ? (
            <div className="rounded-md border border-dashed border-slate-300 p-6 text-sm text-slate-600">
              Nenhum job na fila.
            </div>
          ) : (
            <>
              <div className="hidden rounded-md border border-slate-200 md:block">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Tipo</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Tentativas</TableHead>
                      <TableHead>Criado em</TableHead>
                      <TableHead>Erro</TableHead>
                      <TableHead className="text-right">Ações</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {jobs.map((job) => (
                      <TableRow key={job.id}>
                        <TableCell className="font-medium">
                          {resolveAiJobLabel(aiJobTypeLabels, job.type)}
                          {job.ticketId ? (
                            <Link
                              className="mt-0.5 block text-xs font-normal text-emerald-700 hover:underline"
                              href={routes.ticketDetails(job.ticketId)}
                            >
                              Ver chamado
                            </Link>
                          ) : null}
                        </TableCell>
                        <TableCell>
                          <StatusBadge status={job.status} />
                        </TableCell>
                        <TableCell className="tabular-nums">
                          {job.attempts}
                        </TableCell>
                        <TableCell>{formatDate(job.createdAt)}</TableCell>
                        <TableCell className="max-w-xs">
                          <span className="line-clamp-2 text-xs text-slate-600">
                            {job.lastError ?? "--"}
                          </span>
                        </TableCell>
                        <TableCell className="text-right">
                          {renderRetryButton(job)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>

              <div className="grid gap-3 md:hidden">
                {jobs.map((job) => (
                  <div
                    className="grid gap-2 rounded-md border border-slate-200 p-4"
                    key={job.id}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <p className="text-sm font-medium text-slate-950">
                        {resolveAiJobLabel(aiJobTypeLabels, job.type)}
                      </p>
                      <StatusBadge status={job.status} />
                    </div>

                    <p className="text-xs text-slate-500">
                      {job.attempts} tentativas | {formatDate(job.createdAt)}
                    </p>

                    {job.lastError ? (
                      <p className="text-xs text-slate-600">{job.lastError}</p>
                    ) : null}

                    <div className="flex items-center gap-2">
                      {renderRetryButton(job)}
                      {job.ticketId ? (
                        <Button asChild size="sm" variant="ghost">
                          <Link href={routes.ticketDetails(job.ticketId)}>
                            Ver chamado
                          </Link>
                        </Button>
                      ) : null}
                    </div>
                  </div>
                ))}
              </div>

              <PaginationBar
                isDisabled={isLoading}
                page={page}
                totalElements={totalElements}
                totalPages={totalPages}
                onPageChange={goToPage}
              />
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
