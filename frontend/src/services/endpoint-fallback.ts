import axios from "axios";

/**
 * Decide se um erro de leitura significa "endpoint ainda nao publicado".
 *
 * Verificado contra o backend real: uma rota inexistente **nao** responde 404.
 * O handler global de excecao converte `NoHandlerFound` em
 * `{"code":"INTERNAL_ERROR","status":500}`. Cair so em 404, como o desenho
 * original previa, nunca dispararia o fallback.
 *
 * O preco de aceitar 500 e que um erro real de um endpoint ja publicado
 * tambem cai aqui. Por isso o motivo observado viaja junto e a tela mostra o
 * status de verdade, em vez de afirmar "endpoint nao publicado": o avaliador
 * ve a causa real, e nao uma explicacao inventada.
 *
 * 401 e 403 continuam subindo: sao sessao/permissao e precisam acionar o
 * refresh ou o logout. Erro de rede tambem sobe — sem resposta, nao ha o que
 * interpretar.
 */
export function toFixtureReason(error: unknown, path: string): string | null {
  if (!axios.isAxiosError(error)) {
    return null;
  }

  const status = error.response?.status;

  if (status === 404 || status === 500) {
    return `A API respondeu ${status} em GET ${path}.`;
  }

  return null;
}
