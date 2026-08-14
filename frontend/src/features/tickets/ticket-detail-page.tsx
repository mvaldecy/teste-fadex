"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { Button } from "@/src/components/ui/button";
import { routes } from "@/src/routes/routes";
import { TicketDetailPanel } from "./ticket-detail-panel";
import { useTicketComments } from "./use-ticket-comments";
import { useTicketDetail } from "./use-ticket-detail";
import { useTicketEvents } from "./use-ticket-events";

export function TicketDetailPage() {
  const params = useParams<{ id: string }>();
  const ticketId = params.id ?? null;
  const detail = useTicketDetail(ticketId);
  const comments = useTicketComments(ticketId);

  useTicketEvents({
    enabled: false,
    onTicketChanged: () => void detail.loadTicket(),
    onCommentChanged: () => void comments.loadComments()
  });

  return (
    <div className="mx-auto grid max-w-7xl gap-6">
      <header className="flex flex-col gap-4 border-b border-slate-200 pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.12em] text-emerald-700">
            Chamado
          </p>
          <h1 className="mt-2 text-3xl font-semibold tracking-normal">
            Detalhes do chamado
          </h1>
        </div>

        <Button asChild variant="outline">
          <Link href={routes.tickets}>Voltar para chamados</Link>
        </Button>
      </header>

      {detail.error ? (
        <p className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {detail.error}
        </p>
      ) : null}

      <TicketDetailPanel
        choiceLabels={detail.choiceLabels}
        comments={comments.comments}
        commentsError={comments.error}
        isCreatingComment={comments.isCreating}
        isLoading={detail.isLoading}
        isLoadingComments={comments.isLoading}
        ticket={detail.ticket}
        onCreateComment={comments.createComment}
      />
    </div>
  );
}
