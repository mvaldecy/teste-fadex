"use client";

import { useCallback, useEffect, useState } from "react";
import { toApiErrorMessage } from "@/src/services/api-error";
import { ticketEventsService } from "@/src/services/ticket-events.service";
import type { TicketEventDto, TicketEventFilters } from "@/src/types/api";

const initialHistoryFilters: TicketEventFilters = {
  page: 0,
  size: 30,
  sort: "createdAt,desc"
};

export function useTicketHistory(ticketId: string | null) {
  const [events, setEvents] = useState<TicketEventDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadEvents = useCallback(async () => {
    if (!ticketId) {
      setEvents([]);
      setIsLoading(false);
      return;
    }

    setError(null);

    try {
      const response = await ticketEventsService.list(
        ticketId,
        initialHistoryFilters
      );

      setEvents(response.content);
    } catch (loadError) {
      setError(toApiErrorMessage(loadError));
    } finally {
      setIsLoading(false);
    }
  }, [ticketId]);

  useEffect(() => {
    setIsLoading(true);
    void loadEvents();
  }, [loadEvents]);

  return {
    events,
    isLoading,
    error,
    loadEvents
  };
}
