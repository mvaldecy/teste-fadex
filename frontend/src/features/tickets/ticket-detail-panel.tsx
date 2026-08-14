import { Badge } from "@/src/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle
} from "@/src/components/ui/card";
import { Separator } from "@/src/components/ui/separator";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger
} from "@/src/components/ui/tabs";
import { Skeleton } from "@/src/components/ui/skeleton";
import type {
  CreateTicketCommentRequest,
  TicketCommentSummary,
  TicketDto
} from "@/src/types/api";
import {
  type ChoiceLabelMap,
  resolveChoiceLabel
} from "./choice-labels";
import { TicketCommentForm } from "./ticket-comment-form";
import { TicketCommentsList } from "./ticket-comments-list";

type TicketDetailPanelProps = {
  choiceLabels: ChoiceLabelMap | null;
  comments: TicketCommentSummary[];
  commentsError: string | null;
  isCreatingComment: boolean;
  isLoading: boolean;
  isLoadingComments: boolean;
  ticket: TicketDto | null;
  onCreateComment: (payload: CreateTicketCommentRequest) => Promise<boolean>;
};

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(new Date(value));
}

export function TicketDetailPanel({
  choiceLabels,
  comments,
  commentsError,
  isCreatingComment,
  isLoading,
  isLoadingComments,
  ticket,
  onCreateComment
}: TicketDetailPanelProps) {
  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <Skeleton className="h-7 w-64" />
          <Skeleton className="h-4 w-80" />
        </CardHeader>
        <CardContent className="grid gap-4">
          <Skeleton className="h-24 rounded-md" />
          <Skeleton className="h-40 rounded-md" />
        </CardContent>
      </Card>
    );
  }

  if (!ticket) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Detalhe do chamado</CardTitle>
          <CardDescription>
            Selecione um chamado da fila para ver descricao e comentarios.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <CardTitle className="text-xl leading-7">{ticket.title}</CardTitle>
            <CardDescription className="mt-2">
              Criado por {ticket.requester.name} em {formatDate(ticket.createdAt)}
            </CardDescription>
          </div>
          <Badge variant="secondary">
            {resolveChoiceLabel(choiceLabels?.statuses, ticket.status)}
          </Badge>
        </div>
      </CardHeader>

      <CardContent>
        <Tabs defaultValue="summary">
          <TabsList className="grid w-full grid-cols-3">
            <TabsTrigger value="summary">Resumo</TabsTrigger>
            <TabsTrigger value="comments">Comentarios</TabsTrigger>
            <TabsTrigger value="history">Historico</TabsTrigger>
          </TabsList>

          <TabsContent className="mt-5 grid gap-5" value="summary">
            <div className="flex flex-wrap gap-2">
              <Badge variant="outline">
                {resolveChoiceLabel(choiceLabels?.priorities, ticket.priority)}
              </Badge>
              <Badge variant="outline">
                {resolveChoiceLabel(choiceLabels?.categories, ticket.category)}
              </Badge>
              <Badge variant="outline">
                {resolveChoiceLabel(
                  choiceLabels?.classificationOrigins,
                  ticket.classificationOrigin
                )}
              </Badge>
            </div>

            <div className="grid gap-2 rounded-md bg-slate-50 p-4">
              <h2 className="text-sm font-semibold text-slate-950">Descricao</h2>
              <p className="whitespace-pre-wrap text-sm leading-6 text-slate-700">
                {ticket.description}
              </p>
            </div>

            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="font-medium text-slate-500">Solicitante</dt>
                <dd className="mt-1 text-slate-950">{ticket.requester.name}</dd>
              </div>
              <div>
                <dt className="font-medium text-slate-500">Responsavel</dt>
                <dd className="mt-1 text-slate-950">
                  {ticket.assignee?.name ?? "Sem responsavel"}
                </dd>
              </div>
              <div>
                <dt className="font-medium text-slate-500">Atualizado em</dt>
                <dd className="mt-1 text-slate-950">
                  {formatDate(ticket.updatedAt)}
                </dd>
              </div>
            </dl>
          </TabsContent>

          <TabsContent className="mt-5 grid gap-4" value="comments">
            <div>
              <h2 className="text-base font-semibold text-slate-950">
                Comentarios
              </h2>
              <p className="mt-1 text-sm text-slate-500">
                Acompanhe o andamento do atendimento.
              </p>
            </div>

            <TicketCommentForm
              isCreating={isCreatingComment}
              onCreateComment={onCreateComment}
            />

            <TicketCommentsList
              comments={comments}
              error={commentsError}
              isLoading={isLoadingComments}
            />
          </TabsContent>

          <TabsContent className="mt-5" value="history">
            <div className="rounded-md border border-dashed border-slate-300 p-6 text-sm text-slate-600">
              Historico de eventos sera conectado quando o contrato da API for
              definido.
            </div>
          </TabsContent>
        </Tabs>

        <div className="mt-5">
          <Separator />
          <p className="mt-4 text-xs text-slate-500">
            Estrutura preparada para separar informacoes conforme o chamado
            evoluir.
          </p>
        </div>
      </CardContent>
    </Card>
  );
}
