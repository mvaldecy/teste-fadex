"use client";

import { useEffect, useState } from "react";
import {
  type TicketStatusTransitions,
  ticketStatusTransitionsService
} from "@/src/services/ticket-status-transitions.service";
import type { TicketStatusValue } from "@/src/types/api";

/**
 * Matriz de transicoes de status, vinda de `GET /api/v1/ticket-status-transitions`.
 *
 * Antes isto era uma constante local que **duplicava** a matriz do backend, com
 * um comentario registrando a divida e pedindo o endpoint. O endpoint existe, e
 * a divida esta paga: acrescentar um status no dominio (foi o caso de
 * `CANCELADO`) nao exige mais editar esta lista.
 *
 * A checagem aqui e de experiencia de uso, nao de autorizacao: quem recusa a
 * transicao invalida continua sendo o backend, com `409`.
 */

/**
 * Cache de modulo: a matriz e a mesma para todo mundo e nao muda em runtime, e
 * abrir cada chamado nao precisa refazer a chamada.
 */
let cachedTransitions: TicketStatusTransitions | null = null;
let pendingRequest: Promise<TicketStatusTransitions> | null = null;

async function loadTransitions() {
  if (cachedTransitions) {
    return cachedTransitions;
  }

  pendingRequest ??= ticketStatusTransitionsService.get();

  try {
    cachedTransitions = await pendingRequest;

    return cachedTransitions;
  } finally {
    pendingRequest = null;
  }
}

/**
 * Enquanto a matriz nao chegou — e se a chamada falhar — o valor e `null`, e
 * toda funcao abaixo trata isso como "nada permitido". Acao de ciclo de vida
 * escondida e melhor do que acao oferecida que o servidor vai recusar; a falha
 * nao derruba o resto da tela do chamado.
 */
export function useTicketStatusTransitions() {
  const [transitions, setTransitions] = useState<TicketStatusTransitions | null>(
    cachedTransitions
  );

  useEffect(() => {
    let isActive = true;

    loadTransitions()
      .then((loaded) => {
        if (isActive) {
          setTransitions(loaded);
        }
      })
      .catch(() => {
        if (isActive) {
          setTransitions(null);
        }
      });

    return () => {
      isActive = false;
    };
  }, []);

  return transitions;
}

export function allowedStatusesFrom(
  transitions: TicketStatusTransitions | null,
  from: TicketStatusValue
) {
  return transitions?.[from] ?? [];
}

/**
 * Estado terminal e o que a matriz diz que nao tem saida — `FECHADO` e
 * `CANCELADO` hoje. Derivar daqui evita listar status na tela e ficar devendo a
 * atualizacao quando aparecer o proximo.
 */
export function isTerminalStatus(
  transitions: TicketStatusTransitions | null,
  from: TicketStatusValue
) {
  return transitions !== null && allowedStatusesFrom(transitions, from).length === 0;
}

export function canCancelFrom(
  transitions: TicketStatusTransitions | null,
  from: TicketStatusValue
) {
  return allowedStatusesFrom(transitions, from).includes("CANCELADO");
}

/**
 * O status atual entra na lista porque o seletor precisa exibir o proprio valor
 * selecionado — um `Select` sem a opcao corrente renderiza vazio.
 *
 * `CANCELADO` sai: cancelar e irreversivel e tem botao proprio, com
 * confirmacao. Escondido dentro do mesmo seletor das transicoes cotidianas,
 * seria um clique errado esperando para acontecer.
 */
export function selectableStatusesFrom(
  transitions: TicketStatusTransitions | null,
  from: TicketStatusValue
) {
  return [
    from,
    ...allowedStatusesFrom(transitions, from).filter(
      (status) => status !== "CANCELADO"
    )
  ];
}
