/**
 * Cor de apoio por prioridade.
 *
 * A cor e **reforco**, nunca o unico portador da informacao: a linha continua
 * mostrando a prioridade por extenso e a listagem traz legenda. Quem nao
 * distingue as cores nao perde nada.
 *
 * O mapeamento segue o que as pessoas ja esperam — vermelho para ALTA, ambar
 * para MEDIA, azul frio para BAIXA — e a saturacao fica baixa de proposito:
 * barra lateral e fundo suave, para a lista nao virar um semaforo.
 *
 * O mapa guarda so classes. Rotulo de prioridade continua vindo do backend,
 * pelo `choices`.
 */
export type PriorityTone = {
  bar: string;
  dot: string;
  surface: string;
};

const neutralTone: PriorityTone = {
  bar: "border-slate-300",
  dot: "bg-slate-400",
  surface: ""
};

const priorityTones: Record<string, PriorityTone> = {
  ALTA: {
    bar: "border-red-500",
    dot: "bg-red-500",
    surface: "bg-red-50/50"
  },
  MEDIA: {
    bar: "border-amber-500",
    dot: "bg-amber-500",
    surface: "bg-amber-50/40"
  },
  BAIXA: {
    bar: "border-sky-500",
    dot: "bg-sky-500",
    surface: ""
  }
};

export function resolvePriorityTone(priority: string): PriorityTone {
  return priorityTones[priority] ?? neutralTone;
}
