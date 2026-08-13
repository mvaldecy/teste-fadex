import { z } from "zod";

const publicEnvSchema = z.object({
  NEXT_PUBLIC_APP_NAME: z.string().min(1).default("Fadex Helpdesk"),
  NEXT_PUBLIC_API_BASE_URL: z
    .url()
    .refine((value) => {
      const protocol = new URL(value).protocol;
      return protocol === "http:" || protocol === "https:";
    })
    .transform((value) => value.replace(/\/+$/, ""))
});

export type PublicEnv = {
  appName: string;
  apiBaseUrl: string;
};

export function getPublicEnv(
  env: Record<string, string | undefined> = process.env
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
