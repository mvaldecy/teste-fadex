export type RoleValue = "ADMIN" | "SOLICITANTE";

export type TicketStatusValue =
  | "ABERTO"
  | "EM_ANDAMENTO"
  | "RESOLVIDO"
  | "FECHADO"
  | "CANCELADO";

export type TicketPriorityValue = "BAIXA" | "MEDIA" | "ALTA";

export type TicketCategoryValue =
  | "ACESSO"
  | "SISTEMAS"
  | "INFRAESTRUTURA"
  | "EQUIPAMENTOS"
  | "FINANCEIRO"
  | "RH"
  | "OUTROS";

export type ClassificationOriginValue = "IA" | "MANUAL" | "PENDENTE";

export type ChoiceDto<TValue extends string = string> = {
  value: TValue;
  label: string;
};

export type ChoicesResponse = {
  roles: ChoiceDto<RoleValue>[];
  ticketStatuses: ChoiceDto<TicketStatusValue>[];
  ticketPriorities: ChoiceDto<TicketPriorityValue>[];
  ticketCategories: ChoiceDto<TicketCategoryValue>[];
  classificationOrigins: ChoiceDto<ClassificationOriginValue>[];
};
