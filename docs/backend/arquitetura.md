# Arquitetura do backend

Como o backend está organizado e o que acontece em cada fluxo. O **contrato** dos endpoints —
caminhos, corpos, códigos de resposta — está em [`api.md`](api.md); aqui está o porquê e o caminho
percorrido dentro da aplicação.

## Organização

O código é agrupado por responsabilidade técnica, com a IA isolada num pacote próprio:

| Pacote | O que vive ali |
| --- | --- |
| `controller` | Entrada HTTP. Sem regra de negócio: recebe, delega, devolve. |
| `service` | Regra de negócio e transações. |
| `repository` | Acesso a dados via Spring Data, com `Specification` para a busca filtrada. |
| `model` | Entidades JPA, DTOs e enums. |
| `security` | JWT, controle de acesso e limite de tentativas de login. |
| `notification` | Tradução de evento de domínio em e-mail e em evento de tempo real. |
| `sse` | Motor de Server-Sent Events: conexões, audiências, despacho. |
| `mail` | Envio SMTP e renderização dos templates. |
| `ai` | Triagem, embeddings, duplicados, fila de jobs e indicadores. |
| `exception` | Exceções da aplicação e o tratador global. |

`ai` se subdivide em `client` (fala com o Ollama), `triage` (heurística de fallback), `job` (fila e
worker), `duplicate` (similaridade), `classification` (revisão humana) e `indicator` (métricas).

## O ciclo de vida de um chamado

Sete estados possíveis não existem: são cinco, e as transições permitidas são publicadas pela API em
`GET /api/v1/ticket-status-transitions` — a tela não precisa reimplementar a regra.

```
ABERTO ──► EM_ANDAMENTO ──► RESOLVIDO ──► FECHADO
   │             │              │
   └─────────────┴──────────────┴──────► CANCELADO   (terminal)
```

`FECHADO` e `CANCELADO` são terminais. O cancelamento saiu por status, e não por remoção: um chamado
apagado leva junto o histórico, os comentários e a contagem que alimenta os indicadores.

Quem pode o quê: o ADMIN enxerga e movimenta todos os chamados; o SOLICITANTE enxerga apenas os que
abriu e só pode cancelar os próprios, e apenas enquanto estão `ABERTO`. Isso não é checado na tela —
é aplicado no serviço, e a tela apenas esconde o que não adianta tentar.

**Atribuição.** Um chamado sem responsável pode ser assumido por qualquer ADMIN ou atribuído a
outro. Um chamado que já tem responsável responde `409` numa nova atribuição: trocar a quente é
outra operação, com outra semântica, e merece endpoint próprio. **Só o próprio responsável se
desatribui** — sem escape para outros ADMIN, porque tirar alguém de um chamado sem que ele saiba é
justamente o que gera trabalho perdido.

## Da criação à triagem

Criar um chamado **não espera o modelo**. O caminho é assíncrono de propósito:

1. `POST /tickets` grava o chamado com `classificationOrigin = PENDENTE` e devolve `201`.
2. Na mesma transação são enfileirados dois jobs: `CLASSIFICATION` e `EMBEDDING`.
3. Um worker Quartz drena a fila em lotes, com tentativas e recuo progressivo.
4. Cada job concluído publica um evento de tempo real e atualiza os indicadores.

O usuário nunca fica preso esperando inferência, e modelo fora do ar não impede ninguém de abrir
chamado — a fila é persistente e nada se perde.

### Classificação

A chamada ao Ollama envia **um schema JSON com os valores aceitos**, derivados dos próprios enums.
Sem isso o modelo respondia rótulos livres — "Impressora", "CRITICAL" — o parse estourava e **toda**
classificação caía no fallback heurístico em silêncio. O schema zerou os valores inválidos e foi a
mudança de maior retorno em toda a triagem.

Se o modelo falhar ou não estiver instalado, um classificador determinístico por palavras-chave
assume, e a queda é registrada em `WARN` — nunca mais em silêncio. A justificativa gravada diz que
foi heurística, então a origem da classificação é auditável depois.

A sugestão da IA é gravada em colunas próprias, **separadas da classificação vigente**. Sem essa
separação a correção do ADMIN sobrescreveria a sugestão e a taxa de concordância daria 100% para
sempre — mediria nada.

### Duplicados

O embedding de cada chamado é gerado pelo `all-minilm` e guardado em coluna `vector(384)` com índice
HNSW. A similaridade de cosseno é calculada **em Java**, não no banco: o pgvector aqui é tipo de
coluna e índice, o que significa que a aplicação roda em Postgres comum quando a IA está desligada.

O limiar é `0,75`, medido sobre os pares reais da base e não arbitrado. As distribuições se
sobrepõem — há duplicata verdadeira em 0,672 e par falso em 0,726 —, então não existe corte limpo.

O limite real não é o corte, é o modelo: medindo os 378 pares possíveis dos 28 chamados da base, uma
duplicata escrita com vocabulário diferente pontua 0,5022 enquanto pares sem relação chegam a
0,6661 — a duplicata verdadeira fica **abaixo** do ruído, e nenhum limiar separa isso. Trocar para
`paraphrase-multilingual` foi testado: melhora o piso de ruído (mediana 0,41 → 0,22) e piora o topo
do ranking, então a troca não se sustentou. O README registra a medição completa.

## Notificações

Duas saídas independentes escutam o **mesmo** evento de domínio, publicado com
`@TransactionalEventListener(AFTER_COMMIT)` — nada é notificado antes de o dado estar gravado.

### Tempo real (SSE)

O motor mantém as conexões abertas por usuário e resolve a audiência de cada mensagem: por usuário,
por papel, ou os dois. São cinco eventos, e três deles só alcançam ADMIN — o resumo de quem recebe o
quê está em [`api.md`](api.md), seção Notificações.

Dois detalhes que custaram tempo e ficam registrados: **o `EventSource` nativo do navegador não
envia cabeçalho `Authorization`**, então o cliente é `fetch` com leitura de stream e parse manual dos
quadros; e a audiência de atualização precisa incluir todo ADMIN, senão a listagem dele fica parada
enquanto o painel reage.

### E-mail

O compositor traduz o evento em mensagens com HTML (Thymeleaf) e alternativa em texto puro. Duas
regras valem mais que a tabela de gatilhos:

- **Ninguém recebe e-mail da própria ação** — sem isso o ADMIN recebe e-mail do próprio comentário.
- **Chamado de prioridade normal não gera e-mail.** Só `ALTA`. Todo chamado virando e-mail treina as
  pessoas a ignorar a caixa; o tempo real cobre o caso comum.

O envio tem timeouts de conexão, leitura e escrita configurados. Sem eles o JavaMail espera
indefinidamente, e como o executor é compartilhado com o SSE um servidor SMTP lento travaria as
notificações em tela.

## Segurança

Autenticação por JWT, com o backend como *resource server*. O token de acesso dura uma hora; o
refresh token é persistido e invalidado em cascata quando um é usado indevidamente.

Usuário criado por um ADMIN nasce com senha provisória enviada por e-mail e `mustChangePassword`.
O token emitido nesse login **não abre o resto do sistema**: ele é limitado ao endpoint de troca de
senha, então uma senha provisória vazada não vira sessão.

Login tem limite de tentativas: cinco erros no mesmo e-mail em quinze minutos trancam a conta por
cinco, respondendo `429`. A contagem acontece **antes** de conferir a senha — verificar primeiro
custaria um BCrypt por tentativa, que é exatamente o trabalho que a força bruta quer impor. E-mail
inexistente conta junto com senha errada, senão o contador revelaria quais e-mails existem.

## Banco e migrações

Sete migrações Flyway, aplicadas na subida. O esquema nasce em `V1` e cresce por adição — nenhuma
migração reescreve dado existente. `V3` traz a coluna de embedding e o índice HNSW; `V7` traz o
cancelamento.
