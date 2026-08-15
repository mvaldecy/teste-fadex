import { z } from "zod";

const defaultApiBaseUrl = "http://localhost:8080/api/v1";

const publicEnvSchema = z.object({
  NEXT_PUBLIC_APP_NAME: z.string().min(1).default("Fadex Helpdesk"),
  NEXT_PUBLIC_API_BASE_URL: z
    .string()
    .min(1)
    .default(defaultApiBaseUrl)
    .refine((value) => {
      try {
        const protocol = new URL(value).protocol;
        return protocol === "http:" || protocol === "https:";
      } catch {
        return false;
      }
    })
    .transform((value) => value.replace(/\/+$/, ""))
});

export type PublicEnv = {
  appName: string;
  apiBaseUrl: string;
};

/**
 * Acessos **literais** a `process.env.NEXT_PUBLIC_*`.
 *
 * O Next so substitui no bundle do navegador a expressao literal
 * `process.env.NEXT_PUBLIC_X`. Uma referencia solta a `process.env` some no
 * empacotamento: o `safeParse` nao encontrava nada e o `.default` assumia,
 * fazendo o navegador chamar `localhost:8080` mesmo com outra URL
 * configurada. Verificado com build: o valor configurado nao aparecia em
 * `.next/static`.
 */
const inlinedPublicEnv = {
  NEXT_PUBLIC_APP_NAME: process.env.NEXT_PUBLIC_APP_NAME,
  NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL
};

export function getPublicEnv(
  env: Record<string, string | undefined> = inlinedPublicEnv
): PublicEnv {
  const parsed = publicEnvSchema.safeParse(env);

  if (!parsed.success) {
    throw new Error("Configuracao publica invalida");
  }

  return {
    appName: parsed.data.NEXT_PUBLIC_APP_NAME,
    apiBaseUrl: parsed.data.NEXT_PUBLIC_API_BASE_URL
  };
}
