import { Badge } from "@/src/components/ui/badge";
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
import { cn } from "@/src/lib/utils";
import type { TicketSummary } from "@/src/types/api";
import {
  type ChoiceLabelMap,
  resolveChoiceLabel
} from "./choice-labels";
import { TicketActions } from "./ticket-actions";

type TicketListProps = {
  choiceLabels: ChoiceLabelMap | null;
  /**
   * Chamados que o stream acabou de mudar. O destaque e temporario e vive no
   * `useRealtimeFeedback`: aqui a lista so pinta o que recebe.
   */
  highlightedTicketIds?: ReadonlySet<string>;
  isLoading: boolean;
  tickets: TicketSummary[];
};

const highlightRowClass = "bg-emerald-50/80";

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(new Date(value));
}

export function TicketList({
  choiceLabels,
  highlightedTicketIds,
  isLoading,
  tickets
}: TicketListProps) {
  function isHighlighted(ticketId: string) {
    return highlightedTicketIds?.has(ticketId) ?? false;
  }
  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Fila de chamados</CardTitle>
          <CardDescription>Carregando chamados registrados.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton className="h-28 rounded-md" key={index} />
          ))}
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Fila de chamados</CardTitle>
        <CardDescription>{tickets.length} chamados encontrados.</CardDescription>
      </CardHeader>
      <CardContent>
        {tickets.length === 0 ? (
          <div className="rounded-md border border-dashed border-slate-300 p-6 text-sm text-slate-600">
            Nenhum chamado encontrado para os filtros atuais.
          </div>
        ) : (
          <>
            <div className="hidden rounded-md border border-slate-200 md:block">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Chamado</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Prioridade</TableHead>
                    <TableHead>Categoria</TableHead>
                    <TableHead>Criado em</TableHead>
                    <TableHead className="w-[120px] text-right">Acoes</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {tickets.map((ticket) => (
                    <TableRow
                      className={cn(
                        "transition-colors",
                        isHighlighted(ticket.id) && highlightRowClass
                      )}
                      key={ticket.id}
                    >
                      <TableCell>
                        <div>
                          <p className="font-medium text-slate-950">
                            {ticket.title}
                          </p>
                          <p className="mt-1 text-xs text-slate-500">
                            Solicitante: {ticket.requester.name}
                          </p>
                          {isHighlighted(ticket.id) ? (
                            <p className="mt-1 text-xs font-semibold text-emerald-700">
                              Atualizado agora
                            </p>
                          ) : null}
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant="secondary">
                          {resolveChoiceLabel(
                            choiceLabels?.statuses,
                            ticket.status
                          )}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline">
                          {resolveChoiceLabel(
                            choiceLabels?.priorities,
                            ticket.priority
                          )}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline">
                          {resolveChoiceLabel(
                            choiceLabels?.categories,
                            ticket.category
                          )}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-sm text-slate-600">
                        {formatDate(ticket.createdAt)}
                      </TableCell>
                      <TableCell className="text-right">
                        <TicketActions ticketId={ticket.id} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <div className="grid gap-3 md:hidden">
              {tickets.map((ticket) => (
                <article
                  className={cn(
                    "grid gap-3 rounded-md border border-slate-200 bg-white p-4 transition-colors",
                    isHighlighted(ticket.id) && highlightRowClass
                  )}
                  key={ticket.id}
                >
                  <div className="flex flex-col gap-2">
                    <div>
                      <h2 className="text-sm font-semibold leading-6 text-slate-950">
                        {ticket.title}
                      </h2>
                      <p className="mt-1 text-xs text-slate-500">
                        Solicitante: {ticket.requester.name}
                      </p>
                    </div>
                    <span className="text-xs text-slate-500">
                      {formatDate(ticket.createdAt)}
                      {isHighlighted(ticket.id) ? (
                        <span className="ml-2 font-semibold text-emerald-700">
                          Atualizado agora
                        </span>
                      ) : null}
                    </span>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    <Badge variant="secondary">
                      {resolveChoiceLabel(choiceLabels?.statuses, ticket.status)}
                    </Badge>
                    <Badge variant="outline">
                      {resolveChoiceLabel(
                        choiceLabels?.priorities,
                        ticket.priority
                      )}
                    </Badge>
                    <Badge variant="outline">
                      {resolveChoiceLabel(
                        choiceLabels?.categories,
                        ticket.category
                      )}
                    </Badge>
                  </div>

                  <div className="flex justify-end">
                    <TicketActions ticketId={ticket.id} />
                  </div>
                </article>
              ))}
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
