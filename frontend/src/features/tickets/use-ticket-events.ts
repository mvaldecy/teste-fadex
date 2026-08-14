"use client";

import { useEffect } from "react";

type UseTicketEventsOptions = {
  enabled: boolean;
  onTicketChanged: () => void;
  onCommentChanged: () => void;
};

export function useTicketEvents({
  enabled,
  onTicketChanged,
  onCommentChanged
}: UseTicketEventsOptions) {
  useEffect(() => {
    if (!enabled) {
      return;
    }

    void onTicketChanged;
    void onCommentChanged;
  }, [enabled, onCommentChanged, onTicketChanged]);
}
