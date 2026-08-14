import type { IndicatorsResponse } from "@/src/types/api";

/**
 * Dado fixo usado apenas enquanto `GET /api/v1/indicators` responder 404.
 *
 * Os numeros seguem a ordem de grandeza do seed de desenvolvimento (20
 * chamados, 6 usuarios) para a tela ter forma realista. Nunca e exibido sem o
 * aviso de "dados de exemplo" na pagina.
 */
export const indicatorsFixture: IndicatorsResponse = {
  totalPorStatus: {
    ABERTO: 8,
    EM_ANDAMENTO: 5,
    RESOLVIDO: 4,
    FECHADO: 3
  },
  totalPorPrioridade: {
    BAIXA: 6,
    MEDIA: 9,
    ALTA: 5
  },
  totalPorCategoria: {
    ACESSO: 4,
    SISTEMAS: 6,
    INFRAESTRUTURA: 3,
    EQUIPAMENTOS: 2,
    FINANCEIRO: 2,
    RH: 1,
    OUTROS: 2
  },
  abertosHoje: 3,
  fechadosHoje: 2,
  abertosNaSemana: 11,
  fechadosNaSemana: 7,
  altaPrioridadeEmAberto: 4,
  tempoFechamentoHoras: { media: 42.5, mediana: 30, p90: 96 },
  tempoPrimeiraRespostaHoras: { media: 6.2, mediana: 4, p90: 14 },
  tempoAtribuicaoHoras: { media: 3.1, mediana: 2, p90: 8 },
  agingBacklog: { ate1Dia: 4, de1A3Dias: 6, acima3Dias: 3 },
  idadeChamadoMaisAntigoHoras: 480,
  percentualDentroDoSla: 72.5,
  concordanciaIaPercentual: 68,
  confiancaMediaIa: 0.81,
  distribuicaoClassificacao: {
    IA: 9,
    MANUAL: 7,
    PENDENTE: 4
  },
  filaJobs: {
    pendentes: 2,
    falhos: 1,
    tempoMedioProcessamentoSegundos: 4.7
  },
  duplicadosDetectados: 3
};
