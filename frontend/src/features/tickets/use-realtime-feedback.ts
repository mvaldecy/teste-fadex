"use client";

import { useCallback, useEffect, useRef, useState } from "react";

const highlightDurationMs = 12000;

/**
 * Memória curta do que o stream acabou de mudar.
 *
 * O tempo real já funcionava: a tela recarregava ao receber o evento. O que
 * faltava era **perceber** — trocar os dados em silencio e indistinguivel de
 * não ter reagido. Este hook guarda quem mudou ha pouco, para que a linha
 * fique destacada por alguns segundos, e o instante da ultima atualizacao,
 * para o rotulo "ao vivo" do cabecalho.
 */
export function useRealtimeFeedback() {
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [highlightedIds, setHighlightedIds] = useState<ReadonlySet<string>>(
    new Set()
  );
  const timersRef = useRef(new Map<string, ReturnType<typeof setTimeout>>());

  const clearHighlight = useCallback((ticketId: string) => {
    timersRef.current.delete(ticketId);
    setHighlightedIds((current) => {
      if (!current.has(ticketId)) {
        return current;
      }

      const next = new Set(current);
      next.delete(ticketId);

      return next;
    });
  }, []);

  const registerUpdate = useCallback(
    (ticketId: string | null) => {
      setUpdatedAt(new Date());

      if (!ticketId) {
        return;
      }

      const existingTimer = timersRef.current.get(ticketId);

      if (existingTimer) {
        clearTimeout(existingTimer);
      }

      timersRef.current.set(
        ticketId,
        setTimeout(() => clearHighlight(ticketId), highlightDurationMs)
      );

      setHighlightedIds((current) => {
        if (current.has(ticketId)) {
          return current;
        }

        return new Set(current).add(ticketId);
      });
    },
    [clearHighlight]
  );

  // Os timers precisam morrer com o componente: sem isto uma navegacao durante
  // a janela de destaque chamaria `setState` numa tela desmontada.
  useEffect(() => {
    const timers = timersRef.current;

    return () => {
      for (const timer of timers.values()) {
        clearTimeout(timer);
      }

      timers.clear();
    };
  }, []);

  return { highlightedIds, registerUpdate, updatedAt };
}
