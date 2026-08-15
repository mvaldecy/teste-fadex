# Semelhantes, Triagem Manual e Varredura por Papel — Design

## Objetivo

Consumir os dois endpoints que faltavam (`GET /tickets/{id}/similar` e
`POST /tickets/{id}/ai-triage`), corrigir a conclusao errada do ciclo anterior sobre atribuicao, e
fazer uma varredura por papel na interface inteira.

## Correcao do ciclo anterior

O ciclo anterior registrou que `PATCH /tickets/{id}/assignee` trocava o responsavel de um chamado
ja atribuido, respondendo `200`, e liberou o seletor de responsavel com base nisso.

**A conclusao estava errada, e o erro foi de metodo, nao de leitura do backend.** A sequencia
observada foi: um `PATCH` com responsavel nao-ADMIN, que falhou com `409` e portanto **nao
atribuiu**; e um segundo `PATCH` com um ADMIN, que respondeu `200` — legitimamente, porque o
chamado continuava sem responsavel. O `200` era de uma atribuicao comum, e foi lido como
reatribuicao. `TicketService.updateAssignee` recusa chamado ja atribuido desde sempre.

A licao registrada: **um teste que muda duas variaveis de uma vez nao prova nenhuma das duas.**
Para afirmar "reatribuicao funciona" era preciso partir de um chamado comprovadamente atribuido, o
que teria custado uma requisicao a mais de leitura.

A UI voltou ao desenho original: com responsavel, nome e "Recusar atribuicao"; sem responsavel,
seletor e "Atribuir".

## Escopo

- Branch `feature(frontend)/semelhantes-e-triagem-manual`, a partir de `dev`.
- Aba "Semelhantes" no detalhe do chamado, restrita a ADMIN.
- Botao de triagem manual, com rotulo e estado dependentes do chamado.
- Varredura por papel: esconder o que e permissao, desabilitar o que e estado.

Fora deste escopo: `backend/**` e migrations.

## Decisoes

### 1. Esconder e desabilitar sao coisas diferentes

A regra que orientou a varredura, e que vale para a interface inteira:

- **Permissao esconde.** O usuario nunca vai poder, entao o elemento nao existe para ele. Renderizar
  e falhar depois e pior que nao renderizar: promete uma porta que nao abre.
- **Estado desabilita.** O usuario ate poderia, mas agora nao da. O elemento aparece apagado **com o
  motivo visivel**, porque sumir sem explicacao deixa a pessoa procurando o que nao existe mais.

**Isto e experiencia de uso, nao seguranca.** Quem autoriza e o backend, que continua respondendo
`403` e `409` independentemente do que a tela mostre. Nenhuma checagem de servidor foi trocada por
checagem de tela; o que a tela evita e o usuario tomar erro por algo que nunca poderia fazer.

| Elemento | Tratamento | Motivo |
| --- | --- | --- |
| Rotas `/dashboard`, `/usuarios`, `/admin/jobs` | esconde (menu) e guarda (rota) | `403` no endpoint |
| Aba "Semelhantes" | esconde | `403`, e expoe titulo de chamado alheio |
| Acoes do chamado (status, responsavel, classificacao, triagem) | esconde | `403` |
| Reprocessar job de IA | desabilita fora de `FAILED` | `409` por estado |
| Solicitar triagem | desabilita com job ativo dos dois tipos | `409` por estado |
| Transicoes de status invalidas | remove da lista; seletor inteiro desabilitado em `FECHADO` | `409` por estado |

### 2. O dashboard e ADMIN, e cada papel tem uma casa

`GET /api/v1/indicators` e restrito. Mandar todo mundo para `/dashboard` apos o login faria o
SOLICITANTE cair numa tela que a guarda devolve no instante seguinte. `homeRouteForRole` resolve o
destino: ADMIN vai para `/dashboard`, os demais para `/tickets`.

O detalhe que quebraria: a guarda de ADMIN antes redirecionava para `/dashboard`. Com o dashboard
tambem guardado, isso viraria laco — o nao-ADMIN seria mandado justamente para a rota que o expulsa.
Por isso a guarda passou a usar `homeRouteForRole`.

### 3. Matriz de transicoes duplicada, com registro

`GET /api/v1/choices` publica os valores de `TicketStatus`, mas **nao as transicoes validas**. Sem
elas a tela so teria duas saidas: oferecer transicao invalida e deixar o usuario tomar `409`, ou
repetir a matriz do backend. Repetir e o menor dano, e esta isolado em
`ticket-status-transitions.ts` com o registro da duplicacao.

**Pendencia para o backend:** expor `TicketStatusTransition.allowedFrom` no `/choices`. A propria
classe Java diz que a matriz existe em estrutura consultavel para "o front habilitar botoes" — falta
so o transporte.

### 4. Similaridade ausente nao e zero

`similarity` vem `null` em vinculo gravado antes da migration `V6`. Renderizar `0%` afirmaria "nada
parecido", que e o oposto do que a existencia do vinculo significa. A tela diz "Similaridade nao
registrada".

### 5. A guarda da triagem e por tipo, como a do backend

O backend so responde `409` quando **nenhum** dos dois tipos pode ser enfileirado — um embedding
ainda `PENDING` nao bloqueia a reclassificacao. A tela le `GET /ai/jobs?ticketId=` e desabilita o
botao pelo mesmo criterio. Entre a leitura e o clique o estado pode mudar; quando muda, o `409` cai
no tratamento de conflito, que recarrega e desabilita.

O toast fala em **enfileirar**, nao em concluir, porque a resposta e `202`: o worker processa depois.

## Estrutura

```
src/features/tickets/ticket-status-transitions.ts   matriz de transicoes (duplicada do backend)
src/features/tickets/use-ticket-similar.ts          GET /tickets/{id}/similar
src/features/tickets/ticket-similar-list.tsx        lista da aba "Semelhantes"
src/features/tickets/use-ticket-triage.ts           POST /tickets/{id}/ai-triage + estado da fila
src/routes/routes.ts                                + homeRouteForRole
```

## Verificacao

Endpoints exercitados por `curl` contra o backend rodando, com token de ADMIN:

- `POST /ai-triage` respondeu `202` com dois jobs; a chamada seguinte, `409` com
  "Ja existe triagem em andamento para este chamado."
- `GET /similar` respondeu `200` com o par nas **duas** direcoes: consultando o chamado de origem e
  consultando o mais antigo, cujo vinculo so existe na direcao oposta. Para produzir o par foi
  preciso criar dois chamados de texto praticamente identico — os chamados do seed nao passam do
  limiar de 0.75.

No navegador, com o dev server e o Chrome real:

- **ADMIN:** quatro abas com "Semelhantes", que lista o par com "100% de similaridade"; botao
  "Reprocessar com IA" em chamado ja classificado e "Solicitar triagem" em `PENDENTE`; ao clicar,
  toast de enfileiramento e o botao **desabilitado** com o motivo no titulo e ao lado.
- **Status:** chamado `FECHADO` tem o seletor desabilitado e nenhuma opcao; chamado `RESOLVIDO`
  oferece "Em andamento", "Resolvido" e "Fechado" — sem "Aberto", como a matriz do backend manda.
- **Responsavel:** chamado atribuido mostra nome e "Recusar atribuicao", sem seletor.
- **SOLICITANTE:** login cai em `/tickets`; o menu tem so "Chamados"; `/dashboard` e `/usuarios`
  digitados na URL devolvem para `/tickets`; o detalhe do chamado tem tres abas, sem acoes e sem
  classificacao. **Nenhuma requisicao a rota de ADMIN foi disparada na sessao** — verificado pela
  `performance` da pagina, com zero chamadas a `similar`, `ai/jobs`, `indicators` e `users`.

`make frontend-lint` e `make frontend-build` a cada commit. O build pegou o que o lint nao pega:
dois imports de tipo faltando em `tickets.service.ts` passaram no ESLint e quebraram no `tsc`.
