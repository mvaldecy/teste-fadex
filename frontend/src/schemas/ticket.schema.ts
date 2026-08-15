import { z } from "zod";
import { paginationParamsSchema } from "./pagination.schema";

const statusValues = [
  "ABERTO",
  "EM_ANDAMENTO",
  "RESOLVIDO",
  "FECHADO",
  "CANCELADO"
] as const;
const priorityValues = ["BAIXA", "MEDIA", "ALTA"] as const;
const categoryValues = [
  "ACESSO",
  "SISTEMAS",
  "INFRAESTRUTURA",
  "EQUIPAMENTOS",
  "FINANCEIRO",
  "RH",
  "OUTROS"
] as const;

const emptyToUndefined = (value: unknown) => (value === "" ? undefined : value);
const optionalTextSchema = z.preprocess(
  emptyToUndefined,
  z.string().trim().min(1).optional()
);

export const ticketFiltersSchema = paginationParamsSchema.extend({
  status: z.preprocess(emptyToUndefined, z.enum(statusValues).optional()),
  priority: z.preprocess(emptyToUndefined, z.enum(priorityValues).optional()),
  category: z.preprocess(emptyToUndefined, z.enum(categoryValues).optional()),
  requesterId: optionalTextSchema,
  assigneeId: optionalTextSchema,
  search: optionalTextSchema
});

export const createTicketSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, "Informe o título.")
    .max(160, "Use no maximo 160 caracteres."),
  description: z.string().trim().min(1, "Informe a descrição.")
});

export type TicketFiltersData = z.infer<typeof ticketFiltersSchema>;
export type CreateTicketData = z.infer<typeof createTicketSchema>;
