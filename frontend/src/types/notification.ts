/**
 * Nomes de evento definidos pelo backend em `NotificationEventName`.
 * O contrato do stream esta em `docs/backend/api.md`, secao Notificacoes.
 */
export const notificationEventNames = [
  "CONEXAO_ESTABELECIDA",
  "CHAMADO_ATUALIZADO",
  "CHAMADO_ALTA_PRIORIDADE",
  "INDICADORES_ATUALIZADOS",
  "CLASSIFICACAO_CONCLUIDA",
  "JOB_IA_FALHOU"
] as const;

export type NotificationEventName = (typeof notificationEventNames)[number];

/**
 * O nome fica aberto a `string` de proposito: um evento novo no backend nao
 * pode derrubar o parser nem exigir deploy do frontend para ser ignorado.
 */
export type NotificationEvent = {
  name: NotificationEventName | string;
  payload: unknown;
};

export type NotificationListener = (event: NotificationEvent) => void;
