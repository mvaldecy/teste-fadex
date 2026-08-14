import { Skeleton } from "@/src/components/ui/skeleton";
import type { TicketCommentSummary } from "@/src/types/api";

type TicketCommentsListProps = {
  comments: TicketCommentSummary[];
  error: string | null;
  isLoading: boolean;
};

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(new Date(value));
}

export function TicketCommentsList({
  comments,
  error,
  isLoading
}: TicketCommentsListProps) {
  if (isLoading) {
    return (
      <div className="grid gap-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <Skeleton className="h-20 rounded-md" key={index} />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <p className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
        {error}
      </p>
    );
  }

  if (comments.length === 0) {
    return (
      <p className="rounded-md border border-dashed border-slate-300 p-4 text-sm text-slate-600">
        Nenhum comentario registrado para este chamado.
      </p>
    );
  }

  return (
    <div className="grid gap-3">
      {comments.map((comment) => (
        <article
          className="rounded-md border border-slate-200 bg-white p-4"
          key={comment.id}
        >
          <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm font-semibold text-slate-950">
              {comment.author.name}
            </p>
            <time className="text-xs text-slate-500">
              {formatDate(comment.createdAt)}
            </time>
          </div>
          <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-700">
            {comment.text}
          </p>
        </article>
      ))}
    </div>
  );
}
