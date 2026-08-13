import type { LoginFormData } from "@/src/schemas/auth.schema";

export const loginDefaultValues = {
  email: "admin@fadex.org.br",
  password: "admin123"
} satisfies LoginFormData;
