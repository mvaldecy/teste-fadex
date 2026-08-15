import Link from "next/link";
import { Badge } from "@/src/components/ui/badge";
import { Skeleton } from "@/src/components/ui/skeleton";
import { routes } from "@/src/routes/routes";
import type { SimilarTicketDto } from "@/src/types/api";
import { type ChoiceLabelMap, resolveChoiceLabel } from "./choice-labels";

type TicketSimilarListProps = {
  choiceLabels: ChoiceLabelMap | null;
  error: string | null;
  isLoading: boolean;
  similarTickets: SimilarTicketDto[];
};

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(new Date(value));
}

/**
 * `similarity` pode vir `null` em vinculo gravado antes da migration V6. Mostrar
 * "0%" nesse caso diria "nada parecido", o oposto do que o vinculo significa,
 * entao a ausencia e dita com todas as letras.
 */
function formatSimilarity(similarity: number | null) {
  if (typeof similarity !== "number" || !Number.isFinite(similarity)) {
    return "Similaridade nao registrada";
  }

  return `${Math.round(similarity * 100)}% de similaridade`;
}

export function TicketSimilarList({
  choiceLabels,
  error,
  isLoading,
  similarTickets
}: TicketSimilarListProps) {
  if (isLoading) {
    return (
      <div className="grid gap-3">
        {Array.from({ length: 3 }).map((_, index) => (
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

  if (similarTickets.length === 0) {
    return (
      <div className="rounded-md border border-dashed border-slate-300 p-6 text-sm text-slate-600">
        Nenhum chamado semelhante detectado. A deteccao roda no job de embedding
        de cada chamado.
      </div>
    );
  }

  return (
    <ul className="grid gap-3">
      {similarTickets.map((similar) => (
        <li
          className="rounded-md border border-slate-200 p-4 transition-colors hover:border-emerald-300"
          key={similar.id}
        >
          <div className="flex flex-wrap items-start justify-between gap-3">
            <Link
              className="text-sm font-medium text-slate-950 hover:text-emerald-700 hover:underline"
              href={routes.ticketDetails(similar.id)}
            >
              {similar.title}
            </Link>

            <span className="shrink-0 text-xs font-medium tabular-nums text-emerald-700">
              {formatSimilarity(similar.similarity)}
            </span>
          </div>

          <div className="mt-2 flex flex-wrap items-center gap-2">
            <Badge variant="secondary">
              {resolveChoiceLabel(choiceLabels?.statuses, similar.status)}
            </Badge>
            <Badge variant="outline">
              {resolveChoiceLabel(choiceLabels?.priorities, similar.priority)}
            </Badge>
            <Badge variant="outline">
              {resolveChoiceLabel(choiceLabels?.categories, similar.category)}
            </Badge>
            <span className="text-xs text-slate-500">
              Aberto em {formatDate(similar.createdAt)}
            </span>
          </div>
        </li>
      ))}
    </ul>
  );
}
