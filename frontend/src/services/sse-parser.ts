/**
 * Parse de frames SSE.
 *
 * Fica separado do transporte por ser a peca com maior risco de regressao e a
 * única testavel sem rede. Não importa nada: são funcoes puras.
 */

export type SseFrame = {
  event: string;
  data: string;
  id: string | null;
};

/**
 * Converte um frame bruto (o texto entre dois `\n\n`) em campos.
 *
 * Devolve `null` para blocos sem nenhum campo util, que e o caso do
 * keep-alive `: ping` enviado a cada 20 segundos pelo backend. Sem essa
 * guarda, cada ping viraria um evento `message` fantasma.
 */
export function parseSseFrame(frame: string): SseFrame | null {
  const dataLines: string[] = [];
  let event = "message";
  let id: string | null = null;
  let hasField = false;

  for (const rawLine of frame.split("\n")) {
    const line = rawLine.replace(/\r$/, "");

    // Linha vazia não carrega campo; linha iniciada por `:` e comentario.
    if (line === "" || line.startsWith(":")) {
      continue;
    }

    const separatorIndex = line.indexOf(":");
    const field = separatorIndex === -1 ? line : line.slice(0, separatorIndex);
    const rawValue = separatorIndex === -1 ? "" : line.slice(separatorIndex + 1);
    const value = rawValue.startsWith(" ") ? rawValue.slice(1) : rawValue;

    if (field === "event") {
      event = value;
      hasField = true;
    } else if (field === "data") {
      dataLines.push(value);
      hasField = true;
    } else if (field === "id") {
      id = value;
      hasField = true;
    }
  }

  if (!hasField) {
    return null;
  }

  return { event, data: dataLines.join("\n"), id };
}

/**
 * `data` inválido não pode derrubar o stream: devolve `null` e o evento segue
 * sendo entregue, porque o nome do evento já basta para acionar a recarga.
 */
export function parseEventPayload(data: string): unknown {
  if (data.trim() === "") {
    return null;
  }

  try {
    return JSON.parse(data);
  } catch {
    return null;
  }
}
