"use client";

import { Sparkles, Wand2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Badge } from "@/src/components/ui/badge";
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
import { Textarea } from "@/src/components/ui/textarea";
import type {
  ChoicesResponse,
  TicketCategoryValue,
  TicketDto,
  TicketPriorityValue
} from "@/src/types/api";
import { type ChoiceLabelMap, resolveChoiceLabel } from "./choice-labels";

type TicketClassificationCardProps = {
  choiceLabels: ChoiceLabelMap | null;
  choices: ChoicesResponse | null;
  hasTriageInProgress: boolean;
  isRequestingTriage: boolean;
  isSubmitting: boolean;
  ticket: TicketDto;
  onRequestTriage: () => Promise<boolean>;
  onUpdateClassification: (
    category: TicketCategoryValue,
    priority: TicketPriorityValue,
    classificationJustification?: string
  ) => Promise<boolean>;
};

export function TicketClassificationCard({
  choiceLabels,
  choices,
  hasTriageInProgress,
  isRequestingTriage,
  isSubmitting,
  ticket,
  onRequestTriage,
  onUpdateClassification
}: TicketClassificationCardProps) {
  const [category, setCategory] = useState<string>(ticket.category);
  const [priority, setPriority] = useState<string>(ticket.priority);
  const [justification, setJustification] = useState("");

  useEffect(() => {
    setCategory(ticket.category);
    setPriority(ticket.priority);
    setJustification("");
  }, [ticket.id, ticket.category, ticket.priority]);

  const hasSuggestion = Boolean(
    ticket.aiSuggestedCategory && ticket.aiSuggestedPriority
  );
  /**
   * A classificação vigente veio da IA e ninguém confirmou ainda.
   *
   * Aparece porque o desafio pede que a IA **sugira** e o ADMIN aceite ou
   * corrija — sem este aviso, uma classificação automática é indistinguível de
   * uma decidida por pessoa, e a fila de revisão fica invisível. Vale
   * especialmente aqui: a prioridade é o campo que o modelo mais erra, e é o
   * que define o prazo de SLA cobrado do chamado.
   */
  const aguardandoRevisao =
    ticket.classificationOrigin === "IA" && !ticket.classificationReviewedAt;
  const confidencePercent =
    typeof ticket.confidence === "number"
      ? Math.round(ticket.confidence * 100)
      : null;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Classificação</CardTitle>
        <CardDescription>
          Aceite a sugestão da IA ou corrija a classificação manualmente.
        </CardDescription>

        {aguardandoRevisao ? (
          <p className="mt-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-900">
            <strong>Classificado pela IA, ainda sem revisão.</strong> A
            categoria e a prioridade abaixo foram sugeridas automaticamente e
            valem até que um administrador confirme ou corrija — a prioridade
            define o prazo de SLA cobrado deste chamado.
          </p>
        ) : null}
      </CardHeader>

      <CardContent className="grid gap-5">
        <div className="grid gap-3 rounded-md bg-slate-50 p-4">
          <div className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-emerald-700" />
            <h3 className="text-sm font-semibold text-slate-950">
              Sugestão da IA
            </h3>
          </div>

          {/* O rotulo segue o estado: sem classificação ainda e um pedido, com
              classificação e um reprocessamento. O botao fica desabilitado
              quando já ha job ativo dos dois tipos, que e exatamente o caso em
              que o backend responde 409 — com o motivo visivel, em vez de
              sumir e deixar a pessoa sem entender. */}
          <div className="flex flex-wrap items-center gap-2">
            <Button
              disabled={isRequestingTriage || hasTriageInProgress}
              size="sm"
              title={
                hasTriageInProgress
                  ? "Já existe triagem em andamento para este chamado."
                  : undefined
              }
              type="button"
              variant="outline"
              onClick={() => onRequestTriage()}
            >
              <Wand2 className="h-4 w-4" />
              {isRequestingTriage
                ? "Enviando..."
                : ticket.classificationOrigin === "PENDENTE"
                  ? "Solicitar triagem"
                  : "Reprocessar com IA"}
            </Button>

            {hasTriageInProgress ? (
              <span className="text-xs text-slate-500">
                Triagem em andamento. O resultado chega assim que o worker
                processar.
              </span>
            ) : null}
          </div>

          {hasSuggestion ? (
            <>
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="outline">
                  {resolveChoiceLabel(
                    choiceLabels?.categories,
                    ticket.aiSuggestedCategory as string
                  )}
                </Badge>
                <Badge variant="outline">
                  {resolveChoiceLabel(
                    choiceLabels?.priorities,
                    ticket.aiSuggestedPriority as string
                  )}
                </Badge>
                {confidencePercent !== null ? (
                  <span className="text-xs text-slate-500">
                    Confiança de {confidencePercent}%
                  </span>
                ) : null}
              </div>

              {ticket.classificationJustification ? (
                <p className="text-sm leading-6 text-slate-700">
                  {ticket.classificationJustification}
                </p>
              ) : null}

              <div>
                <Button
                  disabled={isSubmitting}
                  size="sm"
                  type="button"
                  onClick={() =>
                    onUpdateClassification(
                      ticket.aiSuggestedCategory as TicketCategoryValue,
                      ticket.aiSuggestedPriority as TicketPriorityValue
                    )
                  }
                >
                  Aceitar sugestão
                </Button>
              </div>
            </>
          ) : (
            <p className="text-sm text-slate-600">
              Sem sugestão da IA para este chamado. Origem atual:{" "}
              {resolveChoiceLabel(
                choiceLabels?.classificationOrigins,
                ticket.classificationOrigin
              )}
              .
            </p>
          )}
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="grid gap-2">
            <Label htmlFor="ticket-category">Categoria</Label>
            <Select
              disabled={isSubmitting}
              value={category}
              onValueChange={setCategory}
            >
              <SelectTrigger id="ticket-category">
                <SelectValue placeholder="Categoria" />
              </SelectTrigger>
              <SelectContent>
                {choices?.ticketCategories.map((choice) => (
                  <SelectItem key={choice.value} value={choice.value}>
                    {choice.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-2">
            <Label htmlFor="ticket-priority">Prioridade</Label>
            <Select
              disabled={isSubmitting}
              value={priority}
              onValueChange={setPriority}
            >
              <SelectTrigger id="ticket-priority">
                <SelectValue placeholder="Prioridade" />
              </SelectTrigger>
              <SelectContent>
                {choices?.ticketPriorities.map((choice) => (
                  <SelectItem key={choice.value} value={choice.value}>
                    {choice.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        <div className="grid gap-2">
          <Label htmlFor="ticket-justification">Justificativa (opcional)</Label>
          <Textarea
            id="ticket-justification"
            placeholder="Explique a correcao da classificação."
            rows={3}
            value={justification}
            onChange={(event) => setJustification(event.target.value)}
          />
        </div>

        <div>
          <Button
            disabled={isSubmitting}
            type="button"
            variant="outline"
            onClick={() =>
              onUpdateClassification(
                category as TicketCategoryValue,
                priority as TicketPriorityValue,
                justification
              )
            }
          >
            Salvar classificação
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
