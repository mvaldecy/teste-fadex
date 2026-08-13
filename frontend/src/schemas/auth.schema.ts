import { z } from "zod";

export const loginFormSchema = z.object({
  email: z.email("Informe um e-mail valido"),
  password: z.string().min(1, "Informe a senha")
});

export type LoginFormData = z.infer<typeof loginFormSchema>;
