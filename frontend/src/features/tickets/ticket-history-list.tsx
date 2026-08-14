import { Skeleton } from "@/src/components/ui/skeleton";
import type { TicketEventDto } from "@/src/types/api";

type TicketHistoryListProps = {
  error: string | null;
  events: TicketEventDto[];
  isLoading: boolean;
};

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(new Date(value));
}

export function TicketHistoryList({
  error,
  events,
  isLoading
}: TicketHistoryListProps) {
  if (isLoading) {
    return (
      <div className="grid gap-3">
        {Array.from({ length: 4 }).map((_, index) => (
          <Skeleton className="h-16 rounded-md" key={index} />
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

  if (events.length === 0) {
    return (
      <div className="rounded-md border border-dashed border-slate-300 p-6 text-sm text-slate-600">
        Nenhum evento registrado para este chamado.
      </div>
    );
  }

  return (
    <ol className="grid gap-0">
      {events.map((event, index) => (
        <li className="flex gap-4" key={event.id}>
          <div className="flex flex-col items-center">
            <span className="mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full bg-emerald-700" />
            {index < events.length - 1 ? (
              <span className="w-px flex-1 bg-slate-200" />
            ) : null}
          </div>

          <div className="pb-5">
            {/* A `description` do backend ja e o texto legivel; criar um mapa
                de label para `type` duplicaria regra de enum no frontend. */}
            <p className="text-sm leading-6 text-slate-950">
              {event.description}
            </p>
            <p className="mt-0.5 text-xs text-slate-500">
              {event.actor?.name ?? "Sistema"} em {formatDate(event.createdAt)}
            </p>
          </div>
        </li>
      ))}
    </ol>
  );
}
