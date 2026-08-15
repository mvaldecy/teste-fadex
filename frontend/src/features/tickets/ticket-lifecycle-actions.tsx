"use client";

import { UserMinus } from "lucide-react";
import { useEffect, useState } from "react";
import { Button } from "@/src/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle
} from "@/src/components/ui/card";
import { Label } from "@/src/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/src/components/ui/select";
import type {
  ChoicesResponse,
  TicketDto,
  TicketStatusValue,
  UserSummary
} from "@/src/types/api";
import type { TicketStatusTransitions } from "@/src/services/ticket-status-transitions.service";
import {
  isTerminalStatus,
  selectableStatusesFrom
} from "./ticket-status-transitions";

/**
 * Responsavel candidato com a carga atual. O numero vem de
 * `workload.openByAssignee` do `/indicators`, cruzado na tela de detalhe.
 */
export type AssigneeOption = UserSummary & {
  openTickets: number;
};

type TicketLifecycleActionsProps = {
  assignees: AssigneeOption[];
  /**
   * Usuario autenticado. Desde a mudanca do contrato, `DELETE
   * /tickets/{id}/assignee` responde `403` para quem nao e o responsavel
   * atual — entao a acao so existe para ele.
   */
  currentUserId: string | null;
  choices: ChoicesResponse | null;
  isSubmitting: boolean;
  ticket: TicketDto;
  transitions: TicketStatusTransitions | null;
  onAssign: (assigneeId: string) => Promise<boolean>;
  onChangeStatus: (status: TicketStatusValue) => Promise<boolean>;
  onUnassign: () => Promise<boolean>;
};

export function TicketLifecycleActions({
  assignees,
  currentUserId,
  choices,
  isSubmitting,
  ticket,
  transitions,
  onAssign,
  onChangeStatus,
  onUnassign
}: TicketLifecycleActionsProps) {
  const [status, setStatus] = useState<string>(ticket.status);
  const [assigneeId, setAssigneeId] = useState<string>(
    ticket.assignee?.id ?? ""
  );

  useEffect(() => {
    setStatus(ticket.status);
    setAssigneeId(ticket.assignee?.id ?? "");
  }, [ticket.id, ticket.status, ticket.assignee?.id]);

  // Estado terminal vem da matriz do servidor, e nao de uma lista de status
  // aqui: FECHADO e CANCELADO nao tem saida, e o proximo terminal que aparecer
  // ja entra coberto.
  const isClosed = isTerminalStatus(transitions, ticket.status);
  const hasStatusChange = status !== ticket.status;

  // Somente as transicoes que o backend aceita a partir do status atual. Sem
  // isso a tela ofereceria, por exemplo, reabrir chamado FECHADO — que sempre
  // responde 409. Enquanto a matriz nao chegou, so o status atual aparece.
  const selectableStatuses = new Set(
    selectableStatusesFrom(transitions, ticket.status)
  );

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Acoes do chamado</CardTitle>
        <CardDescription>
          {isClosed
            ? `Chamado ${ticket.status === "CANCELADO" ? "cancelado" : "fechado"} nao reabre.`
            : "Atualize o andamento e a responsabilidade pelo atendimento."}
        </CardDescription>
      </CardHeader>

      <CardContent className="grid gap-4 sm:grid-cols-2">
        <div className="grid gap-2">
          <Label htmlFor="ticket-status">Status</Label>
          <div className="flex gap-2">
            <Select
              disabled={isClosed || isSubmitting}
              value={status}
              onValueChange={setStatus}
            >
              <SelectTrigger id="ticket-status">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                {choices?.ticketStatuses
                  .filter((choice) => selectableStatuses.has(choice.value))
                  .map((choice) => (
                    <SelectItem key={choice.value} value={choice.value}>
                      {choice.label}
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>

            <Button
              disabled={isClosed || isSubmitting || !hasStatusChange}
              type="button"
              onClick={() => onChangeStatus(status as TicketStatusValue)}
            >
              Salvar
            </Button>
          </div>
        </div>

        <div className="grid gap-2">
          <Label htmlFor="ticket-assignee">Responsavel</Label>

          {/* Atribuir e recusar sao mutuamente exclusivos, por decisao de
              produto implementada em `TicketService.updateAssignee`: atribuir
              chamado que ja tem responsavel responde 409. Trocar de pessoa e
              recusar primeiro e atribuir depois.

              O ciclo anterior desta frente afirmou o contrario a partir de um
              teste que nao provava o que dizia — os dois `PATCH` observados
              rodaram em chamado **sem** responsavel. Fica o registro para nao
              se repetir a leitura.

              O outro 409 do endpoint, responsavel sem papel de ADMIN, e evitado
              na origem: a lista de candidatos vem de `GET /users?role=ADMIN`. */}
          {ticket.assignee ? (
            <div className="flex flex-wrap items-center gap-2">
              <p className="text-sm text-slate-950" id="ticket-assignee">
                {ticket.assignee.name}
              </p>
              {/* Permissao, nao estado: um ADMIN que nao e o responsavel
                  nunca vai poder recusar este chamado, entao o controle nao
                  aparece para ele em vez de aparecer desabilitado. */}
              {ticket.assignee.id === currentUserId ? (
                <Button
                  disabled={isClosed || isSubmitting}
                  size="sm"
                  type="button"
                  variant="outline"
                  onClick={() => onUnassign()}
                >
                  <UserMinus className="h-4 w-4" />
                  Recusar atribuicao
                </Button>
              ) : null}
            </div>
          ) : (
            <div className="flex gap-2">
              <Select
                disabled={isClosed || isSubmitting}
                value={assigneeId}
                onValueChange={setAssigneeId}
              >
                <SelectTrigger id="ticket-assignee">
                  <SelectValue placeholder="Selecione o responsavel" />
                </SelectTrigger>
                <SelectContent>
                  {assignees.map((assignee) => (
                    <SelectItem key={assignee.id} value={assignee.id}>
                      {assignee.name} ({assignee.openTickets} em aberto)
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              <Button
                disabled={isClosed || isSubmitting || !assigneeId}
                type="button"
                onClick={() => onAssign(assigneeId)}
              >
                Atribuir
              </Button>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
