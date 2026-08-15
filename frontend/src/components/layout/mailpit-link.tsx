import { Mail } from "lucide-react";
import { getPublicEnv } from "@/src/config/public-env";
import { cn } from "@/src/lib/utils";

type MailpitLinkProps = {
  className?: string;
  label?: string;
};

/**
 * Atalho para a caixa de e-mail da demonstracao.
 *
 * O sistema notifica por e-mail — senha provisoria, chamado atribuido, status
 * alterado — e sem o Mailpit a vista essas mensagens ficam invisiveis para quem
 * esta avaliando. Abre em aba nova porque a sessao do helpdesk fica na atual.
 *
 * Nao renderiza nada quando `NEXT_PUBLIC_MAILPIT_URL` nao esta configurada, que
 * e o caso de qualquer ambiente que nao seja o Compose local.
 */
export function MailpitLink({ className, label = "Caixa de e-mail" }: MailpitLinkProps) {
  const { mailpitUrl } = getPublicEnv();

  if (!mailpitUrl) {
    return null;
  }

  return (
    <a
      className={cn(
        "inline-flex h-9 items-center gap-2 rounded-md border border-slate-200 px-3 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-950",
        className
      )}
      href={mailpitUrl}
      rel="noreferrer"
      target="_blank"
      title="Abre o Mailpit em outra aba"
    >
      <Mail className="h-4 w-4 shrink-0" />
      <span className="hidden sm:inline">{label}</span>
    </a>
  );
}
