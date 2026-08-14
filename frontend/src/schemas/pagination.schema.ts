import { z } from "zod";

const emptyToUndefined = (value: unknown) => (value === "" ? undefined : value);

export const paginationParamsSchema = z.object({
  page: z.preprocess(
    emptyToUndefined,
    z.coerce.number().int().min(0).optional()
  ),
  size: z.preprocess(
    emptyToUndefined,
    z.coerce.number().int().min(1).max(100).optional()
  ),
  sort: z.preprocess(emptyToUndefined, z.string().trim().min(1).optional())
});

export type PaginationParamsData = z.infer<typeof paginationParamsSchema>;
