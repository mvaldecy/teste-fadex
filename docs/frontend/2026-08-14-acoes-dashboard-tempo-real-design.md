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
- Sistema de permissoes no frontend. **Atualizado no segundo ciclo:** alem de esconder os itens
  de navegacao de ADMIN, `/usuarios` e `/admin/jobs` passam por uma guarda de rota que
  redireciona quem nao e ADMIN. Esconder item de menu nunca foi controle de acesso — quem
  digitava a URL recebia `403` da API como erro tecnico. A autorizacao de verdade continua sendo
  do backend.
- Ordenacao configuravel nas telas novas. **A paginacao entrou** em `/usuarios` e `/admin/jobs`,
  com uma barra reutilizavel; a ordenacao segue fixa.

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

## Contratos: de provisorio para verificado

Esta secao substitui os contratos provisorios do primeiro ciclo. **Todos os endpoints do plano
estao publicados** e foram exercitados contra o backend rodando, com o token do `admin`. O que
segue e o que a verificacao mudou.

### Nao ha mais fallback para dado fixo

O ciclo anterior caia para dado fixo em `404` ou `500` porque `GET /indicators` e `GET /ai/jobs`
ainda nao existiam. Os dois estao no ar, e o mecanismo inteiro foi removido:
`endpoint-fallback.ts`, `indicators.fixture.ts` e `ai-jobs.fixture.ts` deixaram de existir.
Manter o fallback num endpoint publicado transformaria erro real da API em tela de exemplo, que
e o pior dos dois mundos: o operador ve numeros que nao existem e ninguem ve a falha.

### Divergencias encontradas contra o que o desenho supunha

| Ponto | O que o desenho dizia | O que o backend faz |
| --- | --- | --- |
| `PATCH /tickets/{id}/assignee` em chamado ja atribuido | `409`, atribuir e recusar mutuamente exclusivos | `200`, troca o responsavel |
| `PATCH /tickets/{id}/assignee` com responsavel nao-ADMIN | nao previsto | `409` "O responsavel pelo chamado precisa ter papel de administrador" |
| `POST /ai/jobs/{id}/retry` em job que nao falhou | nao previsto | `409` "Apenas jobs com falha podem ser retentados" |
| `GET /indicators` | objeto plano, chaves em portugues | quatro camadas — `overview`, `durations`, `ai`, `workload` — em ingles |
| `GET /ai/jobs` | corpo do item ja conferia | conferiu, campo a campo |
| `GET /tickets/{id}/events` | contrato real desde o inicio | conferiu, campo a campo |

Cada divergencia virou mudanca de UI, nao comentario: o seletor de responsavel fica visivel
mesmo com o chamado atribuido (troca direta), a lista de candidatos ja vinha filtrada por
`role=ADMIN` e por isso o segundo `409` nunca chega ao usuario, o botao de reprocessar so fica
ativo em job `FAILED`, e os indicadores foram reescritos sobre o payload real.

**Duas coisas ficam registradas para o backend**, sem alteracao em `backend/**` por esta frente:

1. A exclusao mutua entre atribuir e recusar estava registrada aqui como decisao de produto, nao
   como inferencia. O backend nao a implementa. Ou a decisao mudou, ou a regra nunca chegou ao
   codigo — vale confirmar antes que a UI de troca direta seja tomada por engano.
2. A exigencia de que o responsavel seja ADMIN nao esta em `docs/backend/api.md`.

### Campos de IA no `TicketDto`

`aiSuggestedCategory`, `aiSuggestedPriority` e `confidence` chegam preenchidos. O bloco de
sugestao renderiza com os tres, e aceitar a sugestao e reenviar os valores por
`PATCH /tickets/{id}/classification` — nao ha endpoint separado de aceite. O corpo enviado usa
`classificationJustification`, que o backend aceita como alias de `justification`.

## Pendencias Registradas para o Backend

1. **`GET /api/v1/users` devolve apenas `{id, name}`.** A projecao nao traz `email` nem `role`,
   embora aceite filtro por ambos. A tela de usuarios contorna com dialog de detalhe por
   `GET /users/{id}`, mas o certo e ampliar a projecao de listagem.
2. **`POST /api/v1/users` nao recebe senha.** O `api.md` diz que o backend gera senha
   provisoria e envia por e-mail, mas `CreateUserRequest` e `createUserFormSchema` no frontend
   pedem `password`. O frontend corrige o proprio lado neste ciclo.
3. **`UserDto` no frontend nao tem `mustChangePassword`**, que o `api.md` documenta. Corrigido
   neste ciclo.
4. **`PATCH /tickets/{id}/assignee` nao implementa a exclusao mutua** registrada aqui como
   decisao de produto: chamado ja atribuido e reatribuido com `200`. Confirmar se a decisao
   mudou ou se a regra nao chegou ao codigo.
5. **`PATCH /tickets/{id}/assignee` exige responsavel com papel ADMIN** (`409` caso contrario) e
   isso nao esta em `docs/backend/api.md`.
6. **`AuthLoginResponse` no frontend descarta `refreshToken` e `mustChangePassword`.** Isto
   nao e pendencia do backend: `AuthResponseDto` **ja devolve os dois**, e `POST /api/v1/auth/refresh`
   ja existe no `AuthController`. Quem joga fora os campos e o tipo do frontend. Verificado no
   codigo, corrigido neste ciclo — ver "Renovacao de sessao" abaixo.

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

### 1.1 Renovacao de sessao com refresh token

O `accessToken` expira em uma hora. Sem renovacao, a sessao morre no meio do trabalho e o
usuario e expulso para o login sem aviso — inclusive durante uma madrugada de desenvolvimento,
que e o cenario real desta entrega.

O backend ja entrega tudo o que falta: `AuthResponseDto` inclui `refreshToken` e
`mustChangePassword`, e `POST /api/v1/auth/refresh` esta publicado. O frontend so precisa parar
de descartar os campos.

Decisao: interceptor de resposta no `api.ts` que, ao receber `401` em requisicao que nao seja a
propria `/auth/refresh`, chama o refresh **uma unica vez** e repete a requisicao original. As
chamadas concorrentes que falharem no mesmo intervalo compartilham a mesma promessa de refresh,
em vez de dispararem um refresh cada — sem isso, uma tela com quatro requisicoes paralelas gera
quatro refreshes e invalida o proprio token em cascata.

Se o refresh falhar, a sessao e encerrada e o usuario vai para o login. Nao ha segunda tentativa:
refresh que falha significa token revogado ou expirado, e insistir so multiplica `401`.

`mustChangePassword` passa a ser guardado na sessao. O fluxo de troca obrigatoria de senha
(`POST /api/v1/auth/change-password`) **nao** entra neste ciclo — o seed nao cria usuario nesse
estado, e a tela extra nao cabe no prazo. Mas o campo fica disponivel para o proximo ciclo, em
vez de ser descartado no parse.

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
`stream: true`, buffer acumulado e quebra por `\n\n`.

Detalhe confirmado capturando os bytes do backend rodando: os campos vem **sem espaco** depois
dos dois-pontos (`event:CONEXAO_ESTABELECIDA`, `data:{...}`), e o keep-alive e `:ping`, tambem
sem espaco. O parser trata o espaco como opcional e descarta qualquer linha iniciada por `:`,
entao os dois formatos funcionam. Cada frame e lido linha a linha:
`event:` define o nome, `data:` acumula (pode haver varias linhas), `id:` e `retry:` sao lidos
e ignorados, e linha iniciada por `:` e comentario de keep-alive (o `: ping` de 20s) e e
descartada. `data` sem JSON valido nao derruba o stream: o evento e entregue com payload `null`.

Reconexao com backoff exponencial de 1s a 30s. Como o contrato nao faz replay de
`Last-Event-ID`, **toda reconexao bem-sucedida dispara recarga por REST** nos assinantes —
por isso o evento sintetico `CONEXAO_ESTABELECIDA` e repassado aos hooks como gatilho de
refresh, e nao apenas logado.

`401` no stream exige cuidado maior do que "parar de tentar". O `fetch` do stream **nao passa
pelo interceptor do axios**, entao um token vencido mataria o stream para sempre enquanto as
chamadas REST seguiriam renovando normalmente — a sessao pareceria viva e o tempo real estaria
morto, exatamente no cenario de trabalho longo que motivou o refresh.

Por isso o cliente renova o token no proprio `401`, reusando a promessa compartilhada de
`refreshAccessToken()`, e reconecta. Desiste apenas se o refresh falhar ou se um token novo
tambem levar `401` (limite de duas tentativas), o que evita laco contra token revogado.

Casos vizinhos: sem token, o cliente para (sessao encerrada, reconectar so geraria laco); e o
`logout()` chama `stopNotificationsStream()` explicitamente, senao o cliente continuaria
tentando reconectar depois de a sessao ter sido limpa.

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
src/services/indicators.service.ts     GET /indicators (sem fallback: endpoint publicado)
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
src/components/layout/pagination-bar.tsx          paginacao das listagens
src/components/layout/admin-route-guard.tsx       guarda de rota das telas de ADMIN
src/features/notifications/high-priority-alerts.tsx alerta de prioridade ALTA no shell
```

## UI

O visual segue o que ja existe: fundo `slate-50`, acento `emerald-700`, cards `shadcn/ui`,
tabela em desktop virando cards em telas menores. Nada de biblioteca de grafico nova — os
indicadores usam barras proporcionais em CSS puro, o que evita 100kB de bundle por um payload
de contagens e mantem o build previsivel dentro do prazo.

Toda listagem tem tres estados explicitos: carregando (skeleton), vazio (moldura tracejada com
texto) e erro (faixa vermelha com a mensagem normalizada por `toApiErrorMessage`). Sucesso e
falha de acao assincrona usam toast do Sonner, como no resto do app.

## Verificacao no Navegador

O cliente SSE era a maior lacuna do ciclo anterior: existia codigo, nao existia evidencia. Foi
verificado com o Chrome real, contra o **dev server** (`next dev`), e nao contra o container de
producao — o StrictMode so monta duas vezes em desenvolvimento, entao medir no build de producao
nao provaria nada sobre a contagem de assinantes. A instrumentacao foi um envelope em
`window.fetch` registrando inicio, status e aborto de cada requisicao ao stream.

- **StrictMode:** duas requisicoes saem, e so uma sobrevive. A primeira e abortada 8 ms depois de
  aberta, pelo unsubscribe do primeiro mount; a segunda responde `200` e permanece. Sobra uma
  conexao viva, que e o que a contagem de assinantes promete.
- **Backoff:** com `docker stop fadex-helpdesk-backend-1`, as tentativas saem com intervalos de
  1 s, 2 s, 4 s, 8 s, 16 s, 30 s e 30 s — o teto de 30 s segurou por dois ciclos. Os intervalos
  medidos entre inicios sao ~1 s maiores porque incluem a propria tentativa que falha.
- **Recuperacao:** apos `docker start`, a decima tentativa respondeu `200` e o stream voltou sem
  intervencao. A tela nao ficou presa em estado de erro.
- **Logout:** a conexao viva e abortada no clique em "Sair" e **nenhuma** nova tentativa aparece
  depois — o `stopNotificationsStream()` no `logout()` faz o que promete.
- **Tempo real de ponta a ponta:** chamado aberto por outro usuario aparece na listagem do ADMIN
  sem recarregar, e o alerta de prioridade ALTA chega como toast com o titulo do chamado e atalho
  para abri-lo, disparado pela triagem da IA.

### Defeito encontrado e corrigido

A sessao reidratava mas `isHydrated` nunca virava `true`: qualquer F5 numa rota do dashboard
travava o app em "Carregando sessao..." para sempre. A causa e que, para storage sincrono, o
zustand roda a reidratacao **dentro** do `create`, quando a const `useSessionStore` ainda esta na
zona morta temporal; o `ReferenceError` era engolido pelo thenable interno da persistencia. A
troca de status virou acao da propria store (`markHydrated`), que nao depende da referencia
externa, com `initialState` como rede no ramo de erro.

## Validacao

`make frontend-lint` e `make frontend-build` a cada commit.

Nao ha runner de teste no frontend neste ciclo, mas o parser SSE — a parte com maior risco de
regressao — e funcao pura exportada e foi verificado de verdade, executando o modulo real com
`node --experimental-strip-types`:

- 9 verificacoes de comportamento: frame do contrato, `: ping` descartado, `data:` multi-linha,
  JSON invalido virando payload `null` sem derrubar o stream, terminadores CRLF, ausencia de
  `event:` caindo em `message`, campo sem espaco apos os dois-pontos, bloco so de comentarios,
  e frame partido ao meio entre dois chunks.
- 1 verificacao contra os **bytes exatos** capturados do backend rodando, confirmando que o
  `:ping` real nao vira evento.

Quando um runner for adicionado, esses casos viram o arquivo de teste sem reescrita.
