import type { ChoiceDto, ChoicesResponse } from "@/src/types/api";

export type ChoiceLabelMap = {
  statuses: Map<string, string>;
  priorities: Map<string, string>;
  categories: Map<string, string>;
  classificationOrigins: Map<string, string>;
};

function toLabelMap(items: ChoiceDto<string>[]) {
  return new Map(items.map((item) => [item.value, item.label]));
}

export function buildChoiceLabelMap(choices: ChoicesResponse): ChoiceLabelMap {
  return {
    statuses: toLabelMap(choices.ticketStatuses),
    priorities: toLabelMap(choices.ticketPriorities),
    categories: toLabelMap(choices.ticketCategories),
    classificationOrigins: toLabelMap(choices.classificationOrigins)
  };
}

export function resolveChoiceLabel(
  labels: Map<string, string> | undefined,
  value: string
) {
  return labels?.get(value) ?? value;
}
