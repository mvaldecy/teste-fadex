import { getPublicEnv } from "@/src/config/public-env";
import type { NotificationListener } from "@/src/types/api";
import { refreshAccessToken } from "./api";
import { getApiAccessToken } from "./api-token";
import { parseEventPayload, parseSseFrame } from "./sse-parser";

/**
 * Cliente do stream `GET /api/v1/notifications/stream`.
 *
 * E um singleton de modulo com contagem de assinantes: quatro telas querem
 * eventos e um hook que abrisse `fetch` por montagem daria quatro conexoes por
 * usuario. O primeiro `subscribe` abre a conexao, o ultimo `unsubscribe` a
 * aborta — o que tambem faz o double-mount do StrictMode terminar com uma
 * conexao viva, e nao duas.
 *
 * `EventSource` nao serve aqui: nao envia header `Authorization`.
 */

const initialRetryDelayMs = 1000;
const maxRetryDelayMs = 30000;
const maxUnauthorizedRetries = 2;
const frameSeparator = "\n\n";

const listeners = new Set<NotificationListener>();

let controller: AbortController | null = null;
let retryTimer: ReturnType<typeof setTimeout> | null = null;
let retryDelayMs = initialRetryDelayMs;
let unauthorizedRetries = 0;
let isRunning = false;

function emit(name: string, payload: unknown) {
  for (const listener of [...listeners]) {
    listener({ name, payload });
  }
}

function clearRetryTimer() {
  if (retryTimer) {
    clearTimeout(retryTimer);
    retryTimer = null;
  }
}

async function consumeStream(signal: AbortSignal) {
  const { apiBaseUrl } = getPublicEnv();
  const token = getApiAccessToken();

  if (!token) {
    throw new Error("SEM_TOKEN");
  }

  const response = await fetch(`${apiBaseUrl}/notifications/stream`, {
    headers: {
      Accept: "text/event-stream",
      Authorization: `Bearer ${token}`
    },
    signal
  });

  if (response.status === 401 || response.status === 403) {
    throw new Error("NAO_AUTORIZADO");
  }

  if (!response.ok || !response.body) {
    throw new Error(`STREAM_INDISPONIVEL_${response.status}`);
  }

  // Conexao aceita: zera backoff e contagem de 401.
  retryDelayMs = initialRetryDelayMs;
  unauthorizedRetries = 0;

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  for (;;) {
    const { done, value } = await reader.read();

    if (done) {
      return;
    }

    buffer += decoder.decode(value, { stream: true });

    let separatorIndex = buffer.indexOf(frameSeparator);

    while (separatorIndex !== -1) {
      const rawFrame = buffer.slice(0, separatorIndex);
      buffer = buffer.slice(separatorIndex + frameSeparator.length);

      const frame = parseSseFrame(rawFrame);

      if (frame) {
        emit(frame.event, parseEventPayload(frame.data));
      }

      separatorIndex = buffer.indexOf(frameSeparator);
    }
  }
}

function scheduleReconnect(delayMs = retryDelayMs) {
  if (!isRunning || listeners.size === 0) {
    return;
  }

  clearRetryTimer();

  retryTimer = setTimeout(() => {
    retryTimer = null;
    void run();
  }, delayMs);

  retryDelayMs = Math.min(retryDelayMs * 2, maxRetryDelayMs);
}

/**
 * O `fetch` do stream nao passa pelo interceptor do axios, entao o token
 * vencido precisa ser renovado aqui. Sem isto o stream morreria no fim da
 * primeira hora e nunca mais voltaria, enquanto o REST seguiria funcionando.
 */
async function handleUnauthorized() {
  unauthorizedRetries += 1;

  if (unauthorizedRetries > maxUnauthorizedRetries) {
    isRunning = false;
    return;
  }

  const nextToken = await refreshAccessToken();

  if (!nextToken) {
    isRunning = false;
    return;
  }

  scheduleReconnect(0);
}

async function run() {
  if (!isRunning || listeners.size === 0) {
    return;
  }

  const currentController = new AbortController();
  controller = currentController;

  try {
    await consumeStream(currentController.signal);
  } catch (error) {
    if (currentController.signal.aborted) {
      return;
    }

    const reason = error instanceof Error ? error.message : "";

    // Sem token e estado de sessao encerrada: reconectar so geraria laco
    // contra um usuario deslogado.
    if (reason === "SEM_TOKEN") {
      isRunning = false;
      return;
    }

    if (reason === "NAO_AUTORIZADO") {
      await handleUnauthorized();
      return;
    }
  }

  scheduleReconnect();
}

export function subscribeToNotifications(listener: NotificationListener) {
  listeners.add(listener);

  if (!isRunning) {
    isRunning = true;
    retryDelayMs = initialRetryDelayMs;
    unauthorizedRetries = 0;
    void run();
  }

  return () => {
    listeners.delete(listener);

    if (listeners.size > 0) {
      return;
    }

    stopNotificationsStream();
  };
}

/**
 * Encerra o stream independentemente de assinantes. Chamado no logout: sem
 * isso o cliente continuaria tentando reconectar com a sessao ja limpa.
 */
export function stopNotificationsStream() {
  isRunning = false;
  controller?.abort();
  controller = null;
  clearRetryTimer();
  retryDelayMs = initialRetryDelayMs;
  unauthorizedRetries = 0;
}
