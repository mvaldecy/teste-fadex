import { z } from "zod";

export const loginFormSchema = z.object({
  email: z.email("Informe um e-mail valido"),
  password: z.string().min(1, "Informe a senha")
});

export type LoginFormData = z.infer<typeof loginFormSchema>;

/**
 * Espelha as regras publicadas em `docs/backend/api.md` para
 * `POST /api/v1/auth/change-password`: minimo 8, maximo 72 e confirmacao
 * igual. Validar aqui evita um ida e volta até a API para erro previsivel.
 */
export const changePasswordFormSchema = z
  .object({
    currentPassword: z.string().min(1, "Informe a senha atual"),
    newPassword: z
      .string()
      .min(8, "A nova senha precisa de ao menos 8 caracteres")
      .max(72, "A nova senha pode ter no maximo 72 caracteres"),
    confirmPassword: z.string().min(1, "Confirme a nova senha")
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    path: ["confirmPassword"],
    message: "A confirmacao precisa ser igual a nova senha"
  });

export type ChangePasswordFormData = z.infer<typeof changePasswordFormSchema>;
