/**
 * Unica excecao autorizada a regra de nao hardcodar label de enum no
 * frontend: `GET /api/v1/choices` expoe apenas os enums de dominio do chamado
 * e nao inclui os enums de job de IA.
 *
 * Fica isolado neste arquivo para ficar obvio o que remover quando o backend
 * passar a expor esses valores em choices.
 */
export const aiJobStatusLabels = new Map<string, string>([
  ["PENDING", "Pendente"],
  ["PROCESSING", "Processando"],
  ["DONE", "Concluido"],
  ["FAILED", "Falhou"]
]);

export const aiJobTypeLabels = new Map<string, string>([
  ["CLASSIFICATION", "Classificacao"],
  ["EMBEDDING", "Embedding"]
]);

export function resolveAiJobLabel(labels: Map<string, string>, value: string) {
  return labels.get(value) ?? value;
}
