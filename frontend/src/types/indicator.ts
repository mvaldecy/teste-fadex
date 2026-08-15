import type {
  ClassificationOriginValue,
  TicketCategoryValue,
  TicketPriorityValue,
  TicketStatusValue
} from "./choice";
import type { UserSummary } from "./user";

/**
 * Payload de `GET /api/v1/indicators`, publicado pela frente IA.
 *
 * Os nomes seguem literalmente o `api.md` — o desenho anterior tinha assumido
 * um payload plano em portugues, e o contrato real e aninhado em quatro
 * camadas e em ingles.
 *
 * Os mapas por status, prioridade e categoria **omitem grupos sem ocorrencia**,
 * e toda estatistica de duracao vem com horas `null` quando `sampleSize` e 0.
 * Por isso o tipo mantem `null` explicito em vez de numero garantido.
 */
/**
 * Faixa do histograma de duracao. A ultima faixa e aberta a direita e vem com
 * `toHours` nulo — "96 horas ou mais".
 */
export type IndicatorHistogramBin = {
  fromHours: number;
  toHours: number | null;
  count: number;
};

export type IndicatorDurationStats = {
  sampleSize: number;
  averageHours: number | null;
  medianHours: number | null;
  p90Hours: number | null;
  /**
   * Opcional no contrato: o painel mantem o resumo numerico e simplesmente nao
   * desenha o grafico quando o campo nao vem.
   */
  histogram?: IndicatorHistogramBin[] | null;
};

export type IndicatorDurationGroup = {
  overall: IndicatorDurationStats;
  byPriority: Partial<Record<TicketPriorityValue, IndicatorDurationStats>>;
  byCategory: Partial<Record<TicketCategoryValue, IndicatorDurationStats>>;
};

export type IndicatorBacklogAging = {
  upToOneDay: number;
  oneToThreeDays: number;
  overThreeDays: number;
};

export type IndicatorSlaSlice = {
  evaluated: number;
  withinTarget: number;
  percentage: number | null;
};

export type IndicatorSla = {
  overall: IndicatorSlaSlice;
  byPriority: Partial<Record<TicketPriorityValue, IndicatorSlaSlice>>;
};

export type IndicatorOverview = {
  total: number;
  byStatus: Partial<Record<TicketStatusValue, number>>;
  byPriority: Partial<Record<TicketPriorityValue, number>>;
  byCategory: Partial<Record<TicketCategoryValue, number>>;
  openedToday: number;
  closedToday: number;
  openedThisWeek: number;
  closedThisWeek: number;
  openHighPriority: number;
};

export type IndicatorDurations = {
  closure: IndicatorDurationGroup;
  firstResponse: IndicatorDurationGroup;
  assignment: IndicatorDurationGroup;
  backlogAging: IndicatorBacklogAging;
  oldestOpenTicketHours: number | null;
  sla: IndicatorSla;
};

export type IndicatorAgreementRate = {
  evaluated: number;
  agreed: number;
  percentage: number | null;
};

export type IndicatorJobQueue = {
  pending: number;
  processing: number;
  failed: number;
  done: number;
  averageQueueToDoneSeconds: number | null;
};

export type IndicatorAi = {
  agreementRate: IndicatorAgreementRate;
  averageConfidence: number | null;
  originDistribution: Partial<Record<ClassificationOriginValue, number>>;
  jobQueue: IndicatorJobQueue;
  duplicatesDetected: number;
};

export type IndicatorAssigneeLoad = {
  user: UserSummary;
  openTickets: number;
};

export type IndicatorAssigneeClosure = {
  user: UserSummary;
  sampleSize: number;
  averageHours: number | null;
  medianHours: number | null;
};

export type IndicatorRequesterVolume = {
  user: UserSummary;
  tickets: number;
};

export type IndicatorWorkload = {
  openByAssignee: IndicatorAssigneeLoad[];
  closureTimeByAssignee: IndicatorAssigneeClosure[];
  topRequesters: IndicatorRequesterVolume[];
};

export type IndicatorsResponse = {
  generatedAt: string;
  overview: IndicatorOverview;
  durations: IndicatorDurations;
  ai: IndicatorAi;
  workload: IndicatorWorkload;
};
