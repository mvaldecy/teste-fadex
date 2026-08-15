import type { TicketDto } from "@/src/types/api";
import { type ChoiceLabelMap, resolveChoiceLabel } from "./choice-labels";

type TicketDetailFieldsProps = {
  choiceLabels: ChoiceLabelMap | null;
  ticket: TicketDto;
};

type TicketField = {
  label: string;
  value: string;
};

function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(new Date(value));
}

/**
 * Ficha do chamado em campos rotulados.
 *
 * No detalhe o rotulo explicito comunica melhor que o badge colorido: quem
 * abre a tela le "Prioridade: Alta" sem precisar decorar o significado da cor.
 * O badge continua na listagem, onde o espaco e curto e a leitura e por
 * varredura.
 *
 * Campo ausente no contrato não vira linha vazia: `confidence` so aparece
 * quando o backend publica o valor.
 */
export function TicketDetailFields({
  choiceLabels,
  ticket
}: TicketDetailFieldsProps) {
  const fields: TicketField[] = [
    {
      label: "Status",
      value: resolveChoiceLabel(choiceLabels?.statuses, ticket.status)
    },
    {
      label: "Prioridade",
      value: resolveChoiceLabel(choiceLabels?.priorities, ticket.priority)
    },
    {
      label: "Categoria",
      value: resolveChoiceLabel(choiceLabels?.categories, ticket.category)
    },
    {
      label: "Origem da classificação",
      value: resolveChoiceLabel(
        choiceLabels?.classificationOrigins,
        ticket.classificationOrigin
      )
    },
    {
      label: "Solicitante",
      value: ticket.requester.name
    },
    {
      label: "Responsável",
      value: ticket.assignee?.name ?? "Sem responsável"
    },
    {
      label: "Criado em",
      value: formatDate(ticket.createdAt)
    },
    {
      label: "Atualizado em",
      value: formatDate(ticket.updatedAt)
    }
  ];

  if (typeof ticket.confidence === "number") {
    fields.splice(4, 0, {
      label: "Confiança da IA",
      value: `${(ticket.confidence * 100).toFixed(0)}%`
    });
  }

  return (
    <section className="grid gap-3">
      <h2 className="text-sm font-semibold uppercase tracking-[0.08em] text-slate-500">
        Ficha do chamado
      </h2>

      <dl className="grid gap-x-6 gap-y-4 rounded-md border border-slate-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-3">
        {fields.map((field) => (
          <div key={field.label}>
            <dt className="text-xs font-medium uppercase tracking-[0.06em] text-slate-500">
              {field.label}
            </dt>
            <dd className="mt-1 text-sm font-medium text-slate-950">
              {field.value}
            </dd>
          </div>
        ))}
      </dl>

      {ticket.classificationJustification ? (
        <p className="rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-600">
          <span className="font-medium text-slate-800">
            Justificativa da classificação:{" "}
          </span>
          {ticket.classificationJustification}
        </p>
      ) : null}
    </section>
  );
}
