import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle
} from "@/src/components/ui/card";
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
import { TicketDetailFields } from "./ticket-detail-fields";

type TicketDetailPanelProps = {
  actionsSlot?: React.ReactNode;
  choiceLabels: ChoiceLabelMap | null;
  historySlot?: React.ReactNode;
  /**
   * Ausente para quem não e ADMIN, e ai a aba não existe. O endpoint de
   * semelhantes expoe título de chamado de outro solicitante, entao renderizar
   * a aba e tomar 403 seria mostrar uma porta que nunca abre.
   */
  similarSlot?: React.ReactNode;
  /** Aba aberta ao entrar. O link "semelhantes" da listagem chega apontando para a dele. */
  initialTab?: string;
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
  actionsSlot,
  choiceLabels,
  historySlot,
  similarSlot,
  initialTab,
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
            Selecione um chamado da fila para ver descrição e comentários.
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
          <div className="shrink-0 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-2">
            <p className="text-xs font-medium uppercase tracking-[0.06em] text-emerald-700">
              Status
            </p>
            <p className="mt-0.5 text-sm font-semibold text-emerald-900">
              {resolveChoiceLabel(choiceLabels?.statuses, ticket.status)}
            </p>
          </div>
        </div>
      </CardHeader>

      <CardContent>
        <Tabs defaultValue={initialTab ?? "summary"}>
          <TabsList
            className={
              similarSlot
                ? "grid w-full grid-cols-4"
                : "grid w-full grid-cols-3"
            }
          >
            <TabsTrigger value="summary">Resumo</TabsTrigger>
            <TabsTrigger value="comments">Comentários</TabsTrigger>
            <TabsTrigger value="history">Histórico</TabsTrigger>
            {similarSlot ? (
              <TabsTrigger value="similar">Semelhantes</TabsTrigger>
            ) : null}
          </TabsList>

          {/*
            A descrição vem primeiro de proposito: e o texto do solicitante, a
            informacao central da tela. Antes ela ficava depois das ações e da
            faixa de badges, e quem abria o chamado precisava rolar para ler o
            problema.
          */}
          <TabsContent className="mt-5 grid gap-6" value="summary">
            <section className="grid gap-3">
              <h2 className="text-sm font-semibold uppercase tracking-[0.08em] text-slate-500">
                Descrição do chamado
              </h2>
              <div className="rounded-md border-l-4 border-emerald-600 bg-slate-50 px-5 py-4">
                <p className="max-w-3xl whitespace-pre-wrap text-base leading-7 text-slate-800">
                  {ticket.description}
                </p>
              </div>
            </section>

            <TicketDetailFields choiceLabels={choiceLabels} ticket={ticket} />

            {actionsSlot}
          </TabsContent>

          <TabsContent className="mt-5 grid gap-4" value="comments">
            <div>
              <h2 className="text-base font-semibold text-slate-950">
                Comentários
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

          <TabsContent className="mt-5 grid gap-4" value="history">
            <div>
              <h2 className="text-base font-semibold text-slate-950">
                Histórico
              </h2>
              <p className="mt-1 text-sm text-slate-500">
                Mudancas de status, responsável e classificação do chamado.
              </p>
            </div>

            {historySlot}
          </TabsContent>

          {similarSlot ? (
            <TabsContent className="mt-5 grid gap-4" value="similar">
              <div>
                <h2 className="text-base font-semibold text-slate-950">
                  Chamados semelhantes
                </h2>
                <p className="mt-1 text-sm text-slate-500">
                  Detectados por embedding quando o chamado foi processado.
                </p>
              </div>

              {similarSlot}
            </TabsContent>
          ) : null}
        </Tabs>

      </CardContent>
    </Card>
  );
}
