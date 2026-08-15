# Próximos Passos — Continuidade

Documento de retomada. Atualizado em 15/08/2026, manhã, com `dev` em `2a2ba49`.
**Prazo de submissão: 15/08/2026 às 12h.**

## Estado atual

Todas as frentes estão **mergeadas em `dev`**: cancelamento, revisão técnica, seed/IA/atribuição,
refinamentos de uso do frontend e preparação para deploy. Não há branch pendente.

- Backend: **341 testes, 0 falhas**.
- Frontend: lint limpo, build com 11 rotas.
- Partida a frio verificada pelo caminho do avaliador (`down` + remoção do volume do Postgres +
  `up -d --build`): stack de pé em **99 s**, 38 jobs de IA todos `DONE`, **0 falhos**, **0 quedas
  para a heurística**, 26 embeddings, 1 vínculo de duplicata, concordância admin×IA em **63,6 %**,
  fila drenando em 51,4 s de média.
- Histogramas de duração publicados nos três blocos; `firstResponse` e `assignment` têm 14 amostras
  e passam do corte de 12 que o frontend exige para desenhar o gráfico.

### O que restou por fazer

1. **Tornar o repositório público** — decisão do Marcos, na hora que ele escolher. Continua privado.
2. Abrir a aplicação e conferir as telas novas com olho humano antes de submeter.

## Histórico das frentes

### O que estava em andamento (tudo mergeado)

| Item | Onde | Situação |
| --- | --- | --- |
| Cancelamento de chamado | `feature(backend)/cancelamento-de-chamado` | **Concluída e empurrada** — 10 commits, 323 testes verdes, aguardando merge |
| Revisão técnica | `docs(projeto)/revisao-tecnica` | **Pronta e não mergeada** — 4 commits, contém `2026-08-15-revisao-tecnica.md` |

A revisão técnica vale a leitura antes de qualquer decisão: ela lista os achados de
desempenho, transação e segurança com evidência de arquivo e linha.

## Achados sobre a triagem por IA

O prompt não enviava os valores dos enums, então **toda** classificação caía no fallback
heurístico em silêncio. Corrigido em `90cf7c4`. Com o prompt corrigido, medi quatro modelos
locais contra os 20 chamados semeados, usando categoria e prioridade do seed como gabarito:

| Abordagem | Categoria | Prioridade | Valores inválidos |
| --- | --- | --- | --- |
| Heurística de palavras-chave | 5/10 | 6/10 | — |
| `llama3.2:1b` | 3/10 | 1/10 | 0 |
| `llama3.2:3b` | 5/10 | 2/10 | 2 |
| `qwen2.5:1.5b` | 3/10 | 4/10 | 1 |
| `qwen2.5:3b` | 3/10 | 4/10 | 0 |
| **`llama3.2:3b` com schema `enum`** | **7/10** | 2/10 | **0** |

Três conclusões:

1. **O Ollama aceita um schema JSON no campo `format`**, não apenas a string `"json"`. Com
   `enum`, o modelo fica impedido de inventar valor — foi o que levou o `llama3.2:3b` de
   5/10 para 7/10 e zerou os inválidos. **É a mudança de maior retorno e ainda não foi
   aplicada ao código.**
2. **Nenhuma abordagem classifica prioridade.** O 6/10 da heurística é viés de classe
   majoritária: ela responde `MEDIA` em 9 dos 10 casos e `MEDIA` é o esperado em 6. Os
   modelos têm o viés oposto (o 1b responde `BAIXA` para tudo, o 3b `ALTA`). Isso precisa
   estar dito no README com essas palavras — é a conclusão mais honesta da medição, e
   justifica por que a revisão do ADMIN faz parte do fluxo.
3. **Temperatura não é alavanca.** O código já usa `temperature: 0`, que é o certo para
   classificação. O erro medido não é aleatoriedade.

### Recomendação para a triagem

- Aplicar o schema com `enum` no `LocalAiTriageClient` e trocar o modelo padrão para
  `llama3.2:3b` (2 GB contra 1,3 GB no download do avaliador).
- Considerar usar a IA para categoria e a heurística para prioridade — é medivelmente melhor
  que qualquer uma sozinha. Complica a narrativa; documentar como decisão consciente.

## Achados sobre a deteccao de duplicados

Medi o `all-minilm` com pares em portugues, via o endpoint de embeddings do Ollama:

| Caso | Similaridade de cosseno |
| --- | --- |
| Duplicata real, escrita com outras palavras | **0,857** |
| Mesmo tema, chamados distintos | 0,432 |
| Temas distintos | 0,414 |
| Temas distintos (outro par) | 0,246 |

**O modelo esta adequado** — a separacao entre duplicata e ruido e enorme. O problema era o
limiar: estava em 0,90, **acima** da duplicata real, entao so pegava copia quase literal e
perdia a parafrase, que e o caso interessante. Ajustado para **0,80**, que fica com folga
acima do ruido (0,43) e abaixo da duplicata (0,86).

Registro do erro: eu endossei a subida de 0,75 para 0,90 argumentando que 0,75 era frouxo,
sem ter medido. A medicao mostrou que havia margem de sobra. O comentario no
`application.properties` agora carrega os numeros, para que o proximo ajuste seja medido e
nao arbitrado.

Trocar o modelo de embedding (por exemplo para `bge-m3`, multilingue) so faria sentido se a
separacao fosse ruim — nao e o caso. E custaria migration: a coluna e `vector(384)` e a
dimensao mudaria.

### Sobre usar a API da Anthropic

Decidido **não fazer agora**. Sem deploy, o avaliador roda local, não tem chave e o caminho
nunca executa — o ganho seria só de leitura de código. É passageiro do deploy: se o deploy
acontecer e sobrar tempo, entra em cerca de uma hora, aproveitando a interface
`AiTriageClient` que já existe. Se entrar, atenção a dois pontos: a chave nunca com prefixo
`NEXT_PUBLIC_`, e uma instância pública com credenciais de seed no README significa que
qualquer pessoa consome a chave criando chamados.

## Fila sugerida, em ordem de risco

1. **Verificar o SSE no navegador.** É o único ponto do sistema sem evidência empírica de
   ponta a ponta com a stack final, e é a parte visualmente mais forte. Abrir
   `localhost:3001`, logar, confirmar uma requisição pendente em `notifications/stream`,
   mexer num chamado em outra aba e ver a tela reagir.
2. **Limpar o resíduo do banco.** São **34 chamados e 9 usuários** contra 24 e 6 do seed —
   e agora há **dois chamados em `CANCELADO`**, um deles cancelado por acidente durante a
   verificação (`bbeebf48-3230-492d-9c55-d9d4b252d7d4`). `CANCELADO` é terminal e não há
   caminho de volta pela API, o que reforça a ressemeadura —
   inclui um par de chamados quase idênticos criado para provar a detecção de duplicados.
   O caminho mais simples é `docker compose down -v` e deixar o seed recriar do zero; ele é
   idempotente por título. **Confirmar antes: isso apaga o volume.**
3. **Mergear a revisão técnica e o cancelamento.** As duas branches estão prontas e
   empurradas. O cancelamento saiu por status (`DELETE` devolve `200` com o chamado em
   `CANCELADO`), ADMIN cancela `ABERTO`/`EM_ANDAMENTO`, solicitante só o próprio e só
   `ABERTO`. Ele também **encontrou e corrigiu um bug real de indicador**: sem
   `resolvedAt`/`closedAt`, todo cancelado virava violação de SLA permanente, piorando com
   o tempo. Mede 323 testes.
4. **NÃO tornar o repositório público.** Decisão explícita do Marcos em 15/08, madrugada:
   o repositório continua privado até que ele diga o contrário. É item da checklist de
   submissão, mas **a virada é dele, na hora que ele escolher** — nenhum agente deve
   executar, sugerir de novo, nem tratar como pendência a resolver. Isso também descarta a
   rotina agendada na nuvem, que exigiria acesso ao repositório.
5. **Aplicar o schema `enum` na triagem** — meia hora, e é o que faz a IA parecer competente
   na tela do avaliador.
6. **Deploy**, se houver fôlego. É o único item grande que resta e o primeiro a cortar.

Os itens 1 a 4 somam menos de uma hora e são eliminatórios ou quase.

## Achados da verificação manual (madrugada de 15/08)

**1. Ambiente inconsistente: dados à frente do código.** A frente de cancelamento rodou
contra o banco compartilhado, aplicou a **V7** e deixou dois chamados em `CANCELADO` — ambos
do `solicitante@fadex.org.br`. O container voltou a servir o build da `dev`, que não conhece
esse status. Resultado: `GET /indicators` responde **500** (varre todos os chamados) e a
listagem do solicitante responde **500** (os dois cancelados são dele). A listagem do admin
escapa só porque eles não caem na primeira página. **Não é bug de código** — o build da `dev`
está correto para os dados que a `dev` produz. Resolve com merge do cancelamento e rebuild,
ou com ressemeadura (`docker compose down -v`).

**2. Fuso horário.** O container rodava em UTC sem `TZ` definida, e as entidades usam
`LocalDateTime`, que não carrega offset — os carimbos apareciam três horas adiantados.
Corrigido fixando `TZ` no compose. **A correção definitiva é `Instant`/`OffsetDateTime`
ponta a ponta com formatação no navegador**, e fica como dívida registrada.

**3. O `GlobalExceptionHandler` não registra nada ao devolver 500.** A causa do erro acima
teve de ser deduzida por fora, testando o enum pela API. Em produção seria um 500 sem rastro.
Vale logar a exceção antes de responder.

**4. Sessão persistida sem versionamento.** Quem tinha sessão de antes das mudanças de hoje
fica com estado de formato antigo no `sessionStorage` — sem `user`, o menu de logout não
renderiza; sem `role`, a navegação esconde tudo. O usuário fica preso numa interface
degradada e sem conseguir sair. O `persist` do zustand tem `version` e `migrate` para isso.

## Armadilhas do ambiente, aprendidas na prática

- **O container do backend guarda imagem antiga.** Três vezes hoje um endpoint novo
  respondeu 500 porque o container estava desatualizado. Antes de qualquer demonstração:
  `docker compose up -d --build backend`.
- **A porta 11434 não é o nosso Ollama.** Há outro Ollama na máquina, do `open-webui`, com
  modelos diferentes. O nosso está publicado em **11435**; o backend fala com ele por dentro
  da rede, em `ollama:11434`. Testes manuais precisam apontar para 11435.
- **O frontend está em `:3001`**, não 3000 — `FRONTEND_PORT` no `.env`.
- **O wizard escreve o `.env` mesmo em `--dry-run`.** Para experimentar sem tocar no seu
  arquivo: `ENV_FILE=/tmp/env-teste ./setup.sh --dry-run`.
- **Migrations**: V1–V6 usadas. A frente de cancelamento está com a **V7**.

## Comandos

```bash
make backend-test                          # 292 testes
make frontend-lint && make frontend-build
docker compose up -d --build backend       # reconstrói o backend
docker compose logs -f backend
./setup.sh --dry-run --yes | tee /tmp/wizard.log
```
