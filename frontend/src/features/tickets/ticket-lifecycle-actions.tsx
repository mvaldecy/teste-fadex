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

/**
 * Responsavel candidato com a carga atual. O numero vem de
 * `workload.openByAssignee` do `/indicators`, cruzado na tela de detalhe.
 */
export type AssigneeOption = UserSummary & {
  openTickets: number;
};

type TicketLifecycleActionsProps = {
  assignees: AssigneeOption[];
  choices: ChoicesResponse | null;
  isSubmitting: boolean;
  ticket: TicketDto;
  onAssign: (assigneeId: string) => Promise<boolean>;
  onChangeStatus: (status: TicketStatusValue) => Promise<boolean>;
  onUnassign: () => Promise<boolean>;
};

export function TicketLifecycleActions({
  assignees,
  choices,
  isSubmitting,
  ticket,
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

  const isClosed = ticket.status === "FECHADO";
  const hasStatusChange = status !== ticket.status;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Acoes do chamado</CardTitle>
        <CardDescription>
          {isClosed
            ? "Chamado fechado nao reabre."
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
                {choices?.ticketStatuses.map((choice) => (
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

          {/* Verificado contra o backend: `PATCH /assignee` num chamado que ja
              tem responsavel **nao** responde 409 — troca o responsavel e
              devolve 200. Por isso o seletor fica sempre visivel, em vez de
              obrigar a recusar antes de trocar. O 409 que existe de verdade e
              outro: o responsavel precisa ter papel de ADMIN, e por isso a
              lista de candidatos ja vem filtrada por `role=ADMIN`. */}
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
              disabled={
                isClosed ||
                isSubmitting ||
                !assigneeId ||
                assigneeId === ticket.assignee?.id
              }
              type="button"
              onClick={() => onAssign(assigneeId)}
            >
              {ticket.assignee ? "Trocar" : "Atribuir"}
            </Button>
          </div>

          {ticket.assignee ? (
            <div className="flex flex-wrap items-center gap-2">
              <p className="text-sm text-slate-600">
                Responsavel atual: {ticket.assignee.name}
              </p>
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
            </div>
          ) : null}
        </div>
      </CardContent>
    </Card>
  );
}
