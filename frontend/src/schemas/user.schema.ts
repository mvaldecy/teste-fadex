import { z } from "zod";
import { paginationParamsSchema } from "./pagination.schema";

const roleValues = ["ADMIN", "SOLICITANTE"] as const;
const emptyToUndefined = (value: unknown) => (value === "" ? undefined : value);

const optionalTextSchema = z.preprocess(
  emptyToUndefined,
  z.string().trim().min(1).optional()
);

export const createUserFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Informe o nome")
    .max(120, "O nome deve ter no maximo 120 caracteres"),
  email: z
    .email("Informe um e-mail valido")
    .max(180, "O e-mail deve ter no maximo 180 caracteres"),
  role: z.enum(roleValues, "Informe o perfil")
});

export const userFiltersSchema = paginationParamsSchema.extend({
  id: optionalTextSchema,
  role: z.preprocess(emptyToUndefined, z.enum(roleValues).optional()),
  name: optionalTextSchema,
  email: optionalTextSchema,
  search: optionalTextSchema
});

export type CreateUserFormData = z.infer<typeof createUserFormSchema>;
export type UserFiltersData = z.infer<typeof userFiltersSchema>;
