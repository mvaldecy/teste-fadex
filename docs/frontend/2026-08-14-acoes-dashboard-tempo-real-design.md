# Frontend Acoes, Dashboard e Tempo Real — Design

## Objetivo

Fechar a experiencia operacional do frontend do Fadex Helpdesk: sair da aplicacao, administrar
usuarios, agir sobre o chamado (status, responsavel, classificacao), acompanhar indicadores e
receber atualizacoes em tempo real pelo stream SSE ja publicado pelo backend.

## Escopo

- Branch `feature(frontend)/acoes-dashboard-tempo-real`, a partir de `dev`.
- Menu de usuario no header do `app-shell.tsx`, com logout ligado ao `session.store.ts`.
- Guarda de sessao e persistencia do token, para que recarregar a pagina nao derrube a sessao.
- Pagina `/usuarios`, com listagem, filtros, detalhe e criacao.
- Consumo real do stream `GET /api/v1/notifications/stream`, com parser SSE proprio.
- Acoes no detalhe do chamado: alterar status, atribuir responsavel, recusar atribuicao,
  aceitar a sugestao da IA ou classificar manualmente.
- Pagina `/dashboard` com os indicadores de `GET /api/v1/indicators`, atualizando por SSE.
- Pagina `/admin/jobs` com listagem de jobs de IA e retry.
- Aba de historico do chamado, consumindo `GET /api/v1/tickets/{ticketId}/events`.

Fora deste escopo:

- Qualquer alteracao em `backend/**` e qualquer migration.
- Sistema de permissoes no frontend. Gating por papel fica restrito a esconder os itens de
  navegacao de ADMIN (`/usuarios` e `/admin/jobs`), conforme decisao registrada em
  `docs/projeto/2026-08-14-frentes-de-trabalho.md`.
- Paginacao completa e ordenacao configuravel nas telas novas; as listagens usam a primeira
  pagina com `size` fixo, como ja acontece na tela de chamados.

## Estado de Partida

Levantamento feito sobre o codigo em `dev` (`95da427`), para nao redesenhar o que ja existe:

| Item | Situacao |
| --- | --- |
| `logout()` em `src/stores/session.store.ts` | Existe, nunca foi chamado pela UI |
| `usersService.list/getById/create` | Existem, sem tela |
| `src/features/tickets/use-ticket-events.ts` | Stub com `useEffect` vazio |
| `src/features/tickets/ticket-actions.tsx` | So o botao "Visualizar" |
| `app/(dashboard)/home/page.tsx` | Cards de indicador com valor fixo `--` |
| `app/(dashboard)/layout.tsx` | Sem guarda de sessao |
| `src/services/api-token.ts` | Token em variavel de modulo, perdido no reload |
| Aba "Historico" em `ticket-detail-panel.tsx` | Placeholder, apesar de `GET /tickets/{id}/events` existir desde `e8695b8` |

## Dependencia do Backend e Contratos Provisorios

Das seis entregas, tres dependem de endpoints que ainda nao existem em `docs/backend/api.md`.
Verificado no codigo, e nao so na documentacao: `cf2a80b` adicionou `changeStatus` e `unassign`
a **entidade** `Ticket`, mas nenhum controller expoe `PATCH` ou `DELETE` ate agora. Os contratos
abaixo seguem provisorios.
A regra de trabalho e a definida no documento de frentes: **nao esperar**. Cada service novo
usa o caminho e o verbo fixados no documento de frentes, e cai para dado fixo quando o backend
responde `404`.

### Regra de fallback

O fallback e por `404` — e so por `404`. Um `401`, `403`, `409` ou `500` continua sendo erro
visivel na tela. Um `catch` generico esconderia bug real de integracao e faria a tela parecer
saudavel contra um backend quebrado.

Quando o dado exibido veio do fixture, a tela mostra um marcador visivel
("Dados de exemplo — endpoint ainda nao publicado"). O avaliador nunca ve numero inventado
apresentado como numero real.

### Contratos provisorios — confirmar com a frente API

Os caminhos e verbos abaixo sao os fixados no documento de frentes e devem ser usados
literalmente. Os **corpos** de requisicao e resposta sao inferencia do frontend e precisam
ser confirmados quando o delta do `api.md` sair.

`PATCH /api/v1/tickets/{id}/status`

```json
{ "status": "EM_ANDAMENTO" }
```

Resposta esperada: `TicketDto`. Espera-se `409` ao tentar reabrir chamado `FECHADO`.

`PATCH /api/v1/tickets/{id}/assignee`

```json
{ "assigneeId": "00000000-0000-0000-0000-000000000000" }
```

Resposta esperada: `TicketDto`.

`DELETE /api/v1/tickets/{id}/assignee`

Sem corpo. Resposta esperada: `TicketDto` (ou `204`; o frontend recarrega o chamado de
qualquer forma, entao os dois funcionam).

`PATCH /api/v1/tickets/{id}/classification`

```json
{ "category": "SISTEMAS", "priority": "ALTA", "justification": "texto opcional" }
```

Resposta esperada: `TicketDto`. Aceitar a sugestao da IA e enviar `aiSuggestedCategory` e
`aiSuggestedPriority` sem alteracao — nao ha endpoint separado de "aceitar".

`GET /api/v1/indicators` — payload unico com todas as camadas. Forma assumida:

```json
{
  "totalPorStatus": { "ABERTO": 8, "EM_ANDAMENTO": 5, "RESOLVIDO": 4, "FECHADO": 3 },
  "totalPorPrioridade": { "BAIXA": 6, "MEDIA": 9, "ALTA": 5 },
  "totalPorCategoria": { "SISTEMAS": 7 },
  "abertosHoje": 3,
  "fechadosHoje": 2,
  "abertosNaSemana": 11,
  "fechadosNaSemana": 7,
  "altaPrioridadeEmAberto": 4,
  "tempoFechamentoHoras": { "media": 42.5, "mediana": 30.0, "p90": 96.0 },
  "tempoPrimeiraRespostaHoras": { "media": 6.2, "mediana": 4.0, "p90": 14.0 },
  "tempoAtribuicaoHoras": { "media": 3.1, "mediana": 2.0, "p90": 8.0 },
  "agingBacklog": { "ate1Dia": 4, "de1A3Dias": 6, "acima3Dias": 3 },
  "idadeChamadoMaisAntigoHoras": 480.0,
  "percentualDentroDoSla": 72.5,
  "concordanciaIaPercentual": 68.0,
  "confiancaMediaIa": 0.81,
  "distribuicaoClassificacao": { "IA": 9, "MANUAL": 7, "PENDENTE": 4 },
  "filaJobs": { "pendentes": 2, "falhos": 1, "tempoMedioProcessamentoSegundos": 4.7 },
  "duplicadosDetectados": 3,
  "cargaPorResponsavel": [{ "responsavel": { "id": "...", "name": "..." }, "abertos": 5 }],
  "topSolicitantes": [{ "solicitante": { "id": "...", "name": "..." }, "total": 6 }]
}
```

Todo campo agregado e lido como **opcional** no frontend. A camada 4 e o p90 estao na linha de
corte do documento de frentes; se a frente IA nao entregar, o card correspondente simplesmente
nao renderiza, em vez de quebrar a pagina.

`GET /api/v1/ai/jobs` — pagina de jobs. Forma assumida por item:

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "ticketId": "00000000-0000-0000-0000-000000000000",
  "type": "CLASSIFICACAO",
  "status": "FALHOU",
  "attempts": 2,
  "errorMessage": "timeout ao chamar o modelo local",
  "createdAt": "2026-08-14T10:00:00",
  "updatedAt": "2026-08-14T10:05:00"
}
```

`POST /api/v1/ai/jobs/{id}/retry` — sem corpo, resposta ignorada; a tela recarrega a lista.

### Campos novos no `TicketDto`

A frente IA vai expor `aiSuggestedCategory`, `aiSuggestedPriority`, `confidence` e
`justification`. O frontend declara os quatro como opcionais em `TicketDto`. Enquanto o
backend nao os enviar, o bloco de sugestao da IA nao aparece — nao ha fixture aqui, porque o
resto do `TicketDto` e real e misturar campo inventado com campo real seria enganoso.

## Pendencias Registradas para o Backend

1. **`GET /api/v1/users` devolve apenas `{id, name}`.** A projecao nao traz `email` nem `role`,
   embora aceite filtro por ambos. A tela de usuarios contorna com dialog de detalhe por
   `GET /users/{id}`, mas o certo e ampliar a projecao de listagem.
2. **`POST /api/v1/users` nao recebe senha.** O `api.md` diz que o backend gera senha
   provisoria e envia por e-mail, mas `CreateUserRequest` e `createUserFormSchema` no frontend
   pedem `password`. O frontend corrige o proprio lado neste ciclo.
3. **`UserDto` no frontend nao tem `mustChangePassword`**, que o `api.md` documenta. Corrigido
   neste ciclo.
4. **`AuthLoginResponse` no frontend nao tem `refreshToken` nem `mustChangePassword`.** Sem
   isso nao da para implementar refresh silencioso nem fluxo de troca de senha obrigatoria.
   Fica registrado; o refresh nao entra neste ciclo.

## Decisoes de Arquitetura

### 1. Sessao persistida em `sessionStorage`

Hoje o token vive em variavel de modulo e a store nao persiste. Qualquer F5 derruba a sessao e
a tela seguinte responde `401` — o que passaria a ser visivel e constante com quatro paginas
novas e um stream que reconecta.

Decisao: persistir `user`, `role`, `accessToken`, `tokenType` e `expiresIn` via middleware
`persist` do zustand em `sessionStorage`, reidratando `setApiAccessToken` no `onRehydrateStorage`.

`sessionStorage` e nao `localStorage`: o token expira em uma hora e nao ha refresh implementado;
manter o token vivo entre sessoes do navegador so aumentaria a janela de exposicao sem entregar
nada. `sessionStorage` some ao fechar a aba, que e o comportamento que queremos.

O custo aceito, dito de forma explicita: **JWT em `sessionStorage` e legivel por qualquer XSS
na pagina.** A alternativa sem esse custo seria nao persistir nada e so adicionar a guarda de
redirect — mas ai o F5 continua derrubando a sessao, o que com quatro telas novas e um stream
que reconecta vira o comportamento dominante da aplicacao, nao um caso de borda. A opcao
realmente segura, cookie `HttpOnly` emitido pelo backend, esta fora do escopo desta frente
(exige mudanca em `backend/**`). Fica registrada como o caminho correto para o proximo ciclo.

Junto vem uma guarda: `app/(dashboard)/layout.tsx` vira um client component que espera a
reidratacao e redireciona para `/login` quando nao ha sessao. Sem a espera pela reidratacao, o
redirect dispara antes do storage carregar e o usuario e expulso ao recarregar.

### 2. Uma unica conexao SSE, compartilhada

Quatro telas querem eventos: listagem de chamados, detalhe, dashboard e jobs. Um hook que abre
`fetch` por montagem daria quatro streams por usuario.

Decisao: um **cliente singleton de modulo** em `src/services/notifications-stream.ts`, com
contagem de assinantes. O primeiro `subscribe` abre a conexao; o ultimo `unsubscribe` aborta.
Os hooks de tela apenas assinam nomes de evento.

Singleton de modulo, e nao React Context: nao ha estado a renderizar, so callbacks; e a
contagem de assinantes resolve o double-mount do StrictMode sem provider. Em desenvolvimento o
StrictMode monta, desmonta e remonta; o unsubscribe do primeiro mount zera a contagem e o
subscribe do segundo reabre — uma conexao viva, nao duas.

### 3. Parser SSE manual sobre `fetch`

`EventSource` nao envia `Authorization` e axios nao expoe corpo incremental no navegador.

Decisao: `fetch` com `AbortController`, `response.body.getReader()`, `TextDecoder` com
`stream: true`, buffer acumulado e quebra por `\n\n`. Cada frame e lido linha a linha:
`event:` define o nome, `data:` acumula (pode haver varias linhas), `id:` e `retry:` sao lidos
e ignorados, e linha iniciada por `:` e comentario de keep-alive (o `: ping` de 20s) e e
descartada. `data` sem JSON valido nao derruba o stream: o evento e entregue com payload `null`.

Reconexao com backoff exponencial de 1s a 30s. Como o contrato nao faz replay de
`Last-Event-ID`, **toda reconexao bem-sucedida dispara recarga por REST** nos assinantes —
por isso o evento sintetico `CONEXAO_ESTABELECIDA` e repassado aos hooks como gatilho de
refresh, e nao apenas logado.

`401` no stream nao e reconectavel: significa token expirado ou ausente. Nesse caso o cliente
para de tentar, em vez de bombardear o backend.

### 4. Refresh por evento, nao merge incremental

Ao receber `CHAMADO_ATUALIZADO`, o frontend recarrega o recurso por REST em vez de aplicar o
payload do evento no estado local. E mais chamadas, mas o payload do evento nao esta
documentado e aplicar campo por campo de um payload nao contratado e como se cria divergencia
silenciosa entre tela e banco. Com o volume do desafio, o custo e irrelevante.

### 5. `/dashboard` e a rota; `/home` redireciona

`routes.home` (`/home`) hoje tem cards falsos de indicador. Em vez de manter duas telas
concorrentes, `/dashboard` passa a ser a rota real dos indicadores e `/home` redireciona para
ela, preservando qualquer link antigo.

### 7. Historico do chamado entra agora, com contrato real

`GET /api/v1/tickets/{ticketId}/events` foi publicado em `e8695b8` e a aba "Historico" do
detalhe continua com o texto "sera conectado quando o contrato da API for definido". O contrato
existe, o historico de mudancas e item obrigatorio do desafio e o custo e um service e um
componente de lista. Entra neste ciclo, e e a unica entrega nova sem contrato inventado.

### 6. Acoes do chamado ficam no detalhe, nao na listagem

`ticket-actions.tsx` (usado por linha da listagem) continua sendo so "Visualizar". Status,
responsavel e classificacao exigem contexto — sugestao da IA, responsavel atual, transicao
valida — que nao cabe numa celula de tabela. As acoes viram um bloco proprio no
`ticket-detail-panel.tsx`, visivel apenas para ADMIN.

## Estrutura

```
src/services/notifications-stream.ts   cliente SSE singleton (parser, reconexao, assinaturas)
src/services/indicators.service.ts     GET /indicators com fallback por 404
src/services/ai-jobs.service.ts        GET /ai/jobs e POST /ai/jobs/{id}/retry
src/services/tickets.service.ts        + updateStatus, assign, unassign, updateClassification

src/features/notifications/use-notifications.ts   ponte React -> cliente singleton
src/features/users/*                              tela de usuarios
src/features/indicators/*                         tela de indicadores
src/features/ai-jobs/*                            tela de jobs
src/features/tickets/ticket-lifecycle-actions.tsx acoes de status/responsavel
src/features/tickets/ticket-classification-card.tsx sugestao da IA e classificacao manual
src/features/tickets/ticket-history-list.tsx     historico de eventos do chamado
src/components/layout/user-menu.tsx               menu de usuario com logout
```

## UI

O visual segue o que ja existe: fundo `slate-50`, acento `emerald-700`, cards `shadcn/ui`,
tabela em desktop virando cards em telas menores. Nada de biblioteca de grafico nova — os
indicadores usam barras proporcionais em CSS puro, o que evita 100kB de bundle por um payload
de contagens e mantem o build previsivel dentro do prazo.

Toda listagem tem tres estados explicitos: carregando (skeleton), vazio (moldura tracejada com
texto) e erro (faixa vermelha com a mensagem normalizada por `toApiErrorMessage`). Sucesso e
falha de acao assincrona usam toast do Sonner, como no resto do app.

## Validacao

`make frontend-lint` e `make frontend-build` a cada commit. Nao ha runner de teste no frontend
neste ciclo — o parser SSE, que e a parte com maior risco de regressao, e escrito como funcao
pura exportada (`parseSseFrame`) justamente para ficar testavel quando o runner existir, e e
verificado manualmente contra o backend rodando.
