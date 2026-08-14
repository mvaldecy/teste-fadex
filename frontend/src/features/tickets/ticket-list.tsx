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
import type { TicketSummary } from "@/src/types/api";
import {
  type ChoiceLabelMap,
  resolveChoiceLabel
} from "./choice-labels";
import { TicketActions } from "./ticket-actions";

type TicketListProps = {
  choiceLabels: ChoiceLabelMap | null;
  isLoading: boolean;
  tickets: TicketSummary[];
};

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(new Date(value));
}

export function TicketList({
  choiceLabels,
  isLoading,
  tickets
}: TicketListProps) {
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
                    <TableRow key={ticket.id}>
                      <TableCell>
                        <div>
                          <p className="font-medium text-slate-950">
                            {ticket.title}
                          </p>
                          <p className="mt-1 text-xs text-slate-500">
                            Solicitante: {ticket.requester.name}
                          </p>
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
                  className="grid gap-3 rounded-md border border-slate-200 bg-white p-4"
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
