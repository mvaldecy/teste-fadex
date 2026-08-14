import { z } from "zod";

export const createTicketCommentSchema = z.object({
  text: z.string().trim().min(1, "Informe o comentario.")
});

export type CreateTicketCommentData = z.infer<
  typeof createTicketCommentSchema
>;
