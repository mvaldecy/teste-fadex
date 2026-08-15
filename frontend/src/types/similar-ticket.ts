import type {
  TicketCategoryValue,
  TicketPriorityValue,
  TicketStatusValue
} from "./choice";

/**
 * Item de `GET /api/v1/tickets/{id}/similar`.
 *
 * `similarity` e nulavel de proposito: vinculos gravados antes da migration V6
 * não registraram o valor e não ha backfill possível, porque o embedding de
 * origem pode ter mudado desde a deteccao. A tela precisa renderizar a
 * ausencia em vez de assumir zero — que seria "nada parecido", o oposto do que
 * o vinculo significa.
 */
export type SimilarTicketDto = {
  id: string;
  title: string;
  status: TicketStatusValue;
  priority: TicketPriorityValue;
  category: TicketCategoryValue;
  similarity: number | null;
  createdAt: string;
};

/**
 * Chamado do ranking dos mais próximos, com a similaridade calculada na hora.
 *
 * Aqui `similarity` nunca é nula — o número é o motivo de o item existir. E
 * `linked` diz se o par cruzou o limiar e virou vínculo persistido, o que
 * separa "o sistema afirma que é duplicata" de "foi o mais próximo que achei".
 */
export type NearestTicketDto = {
  id: string;
  title: string;
  status: TicketStatusValue;
  priority: TicketPriorityValue;
  category: TicketCategoryValue;
  similarity: number;
  linked: boolean;
  createdAt: string;
};
