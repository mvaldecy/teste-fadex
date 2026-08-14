import type { UserSummary } from "./user";

/**
 * Payload unico de `GET /api/v1/indicators`, com todas as camadas.
 *
 * Todo campo agregado e opcional de proposito: camada 4 e percentis estao na
 * linha de corte do documento de frentes. Se a frente IA nao entregar, o card
 * correspondente nao renderiza, em vez de a pagina quebrar.
 */
export type IndicatorDuration = {
  media?: number | null;
  mediana?: number | null;
  p90?: number | null;
};

export type IndicatorAging = {
  ate1Dia?: number;
  de1A3Dias?: number;
  acima3Dias?: number;
};

export type IndicatorJobQueue = {
  pendentes?: number;
  falhos?: number;
  tempoMedioProcessamentoSegundos?: number | null;
};

export type IndicatorAssigneeLoad = {
  responsavel: UserSummary;
  abertos: number;
  tempoMedioFechamentoHoras?: number | null;
};

export type IndicatorRequesterTotal = {
  solicitante: UserSummary;
  total: number;
};

export type IndicatorsResponse = {
  totalPorStatus?: Record<string, number>;
  totalPorPrioridade?: Record<string, number>;
  totalPorCategoria?: Record<string, number>;
  abertosHoje?: number;
  fechadosHoje?: number;
  abertosNaSemana?: number;
  fechadosNaSemana?: number;
  altaPrioridadeEmAberto?: number;
  tempoFechamentoHoras?: IndicatorDuration;
  tempoPrimeiraRespostaHoras?: IndicatorDuration;
  tempoAtribuicaoHoras?: IndicatorDuration;
  agingBacklog?: IndicatorAging;
  idadeChamadoMaisAntigoHoras?: number | null;
  percentualDentroDoSla?: number | null;
  concordanciaIaPercentual?: number | null;
  confiancaMediaIa?: number | null;
  distribuicaoClassificacao?: Record<string, number>;
  filaJobs?: IndicatorJobQueue;
  duplicadosDetectados?: number | null;
  cargaPorResponsavel?: IndicatorAssigneeLoad[];
  topSolicitantes?: IndicatorRequesterTotal[];
};
