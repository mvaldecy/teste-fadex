"use client";

import { useEffect, useRef } from "react";
import { subscribeToNotifications } from "@/src/services/notifications-stream";
import type { NotificationEvent } from "@/src/types/api";

type UseNotificationsOptions = {
  enabled: boolean;
  onEvent: (event: NotificationEvent) => void;
};

/**
 * Ponte entre o cliente singleton de SSE e o ciclo de vida do React.
 *
 * O callback fica numa ref porque o efeito nao pode depender dele: se
 * dependesse, todo render que recriasse a funcao derrubaria e reabriria a
 * assinatura do stream.
 */
export function useNotifications({
  enabled,
  onEvent
}: UseNotificationsOptions) {
  const handlerRef = useRef(onEvent);

  useEffect(() => {
    handlerRef.current = onEvent;
  }, [onEvent]);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    return subscribeToNotifications((event) => handlerRef.current(event));
  }, [enabled]);
}
