"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { toApiErrorMessage } from "@/src/services/api-error";
import { ticketCommentsService } from "@/src/services/ticket-comments.service";
import type {
  CreateTicketCommentRequest,
  TicketCommentSummary
} from "@/src/types/api";

export function useTicketComments(ticketId: string | null) {
  const [comments, setComments] = useState<TicketCommentSummary[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadComments = useCallback(async () => {
    if (!ticketId) {
      setComments([]);
      setError(null);
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const response = await ticketCommentsService.list(ticketId, {
        page: 0,
        size: 10,
        sort: "createdAt,desc"
      });

      setComments(response.content);
    } catch (loadError) {
      setError(toApiErrorMessage(loadError));
    } finally {
      setIsLoading(false);
    }
  }, [ticketId]);

  const createComment = useCallback(
    async (payload: CreateTicketCommentRequest) => {
      if (!ticketId) {
        return false;
      }

      setIsCreating(true);

      try {
        await ticketCommentsService.create(ticketId, payload);
        await loadComments();
        toast.success("Comentario publicado.");

        return true;
      } catch (createError) {
        toast.error("Não foi possível publicar o comentario.", {
          description: toApiErrorMessage(createError)
        });

        return false;
      } finally {
        setIsCreating(false);
      }
    },
    [loadComments, ticketId]
  );

  useEffect(() => {
    void loadComments();
  }, [loadComments]);

  return {
    comments,
    isLoading,
    isCreating,
    error,
    loadComments,
    createComment
  };
}
