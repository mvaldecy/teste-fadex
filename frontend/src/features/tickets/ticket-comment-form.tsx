"use client";

import { Send } from "lucide-react";
import { useState } from "react";
import { Button } from "@/src/components/ui/button";
import { Label } from "@/src/components/ui/label";
import { Textarea } from "@/src/components/ui/textarea";
import {
  createTicketCommentSchema,
  type CreateTicketCommentData
} from "@/src/schemas/comment.schema";

type TicketCommentFormProps = {
  isCreating: boolean;
  onCreateComment: (payload: CreateTicketCommentData) => Promise<boolean>;
};

export function TicketCommentForm({
  isCreating,
  onCreateComment
}: TicketCommentFormProps) {
  const [text, setText] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const parsed = createTicketCommentSchema.safeParse({ text });

    if (!parsed.success) {
      setError(parsed.error.flatten().fieldErrors.text?.[0] ?? null);
      return;
    }

    setError(null);
    const didCreate = await onCreateComment(parsed.data);

    if (didCreate) {
      setText("");
    }
  }

  return (
    <form className="grid gap-3" onSubmit={handleSubmit}>
      <div className="grid gap-2">
        <Label htmlFor="ticket-comment">Novo comentario</Label>
        <Textarea
          id="ticket-comment"
          placeholder="Registre uma atualizacao do atendimento"
          value={text}
          onChange={(event) => setText(event.target.value)}
        />
        {error ? (
          <p className="text-sm font-medium text-red-700">{error}</p>
        ) : null}
      </div>
      <div className="flex justify-end">
        <Button disabled={isCreating} type="submit">
          <Send className="h-4 w-4" />
          {isCreating ? "Publicando..." : "Publicar"}
        </Button>
      </div>
    </form>
  );
}
