# Triagem IA e Embeddings Design

## Objetivo

Implementar triagem automatica de chamados sem depender de API externa, usando um servico local de IA em Docker para classificar categoria/prioridade e gerar embeddings para deteccao de chamados similares.

A solucao deve atender ao requisito obrigatorio do desafio de sugerir categoria e prioridade ao criar chamados, permitir revisao pelo ADMIN e adicionar o diferencial de deteccao de chamados duplicados/similares. A criacao de chamado nao deve ficar lenta nem falhar quando o modelo local estiver indisponivel.

## Escopo

Incluido neste ciclo:

- Boundary de IA no backend, isolado do dominio de chamados.
- Servico local de IA via Docker, inicialmente com Ollama.
- Classificacao de chamado por IA local, com fallback deterministico.
- Persistencia de jobs de IA em banco relacional.
- Worker Quartz para processar classificacao e embedding de forma assincrona.
- Persistencia do embedding do chamado.
- Endpoint de similares calculado sob demanda por cosine similarity.
- Relacionamento persistido e bidirecional entre chamados.
- Endpoint para ADMIN revisar/corrigir classificacao.
- Configuracao para stacks Docker paralelas por worktree.
- Documentacao da abordagem no README e em docs de dominio quando a implementacao for feita.

Fora deste ciclo:

- Treinamento de modelo proprio.
- Uso de API externa de IA.
- pgvector ou busca vetorial nativa no banco.
- Kafka, RabbitMQ ou broker externo.
- Agrupamento automatico em entidade de incidente/problema.
- Bloqueio de criacao por duplicidade.
- Execucao de modelos grandes que exijam GPU.

## Requisitos do Desafio Cobertos

O PDF do desafio permite API de IA gratuita, modelo local leve, heuristica propria ou mock deterministico bem justificado. Esta feature escolhe modelo local leve com fallback deterministico para demonstrar arquitetura real de integracao sem chaves externas.

Requisitos obrigatorios cobertos:

- Ao criar chamado, o sistema sugere categoria e prioridade a partir do titulo/descricao.
- A abordagem de IA fica documentada e reproduzivel localmente.
- O ADMIN pode aceitar implicitamente a sugestao ou corrigi-la manualmente.
- Nenhuma chave de API ou segredo e necessaria.

Diferencial coberto:

- Deteccao de chamados similares por embeddings.

## Arquitetura

O backend tera um boundary de IA em `br.org.fadex.helpdesk.ai`, separado dos services de chamado:

```text
backend/src/main/java/br/org/fadex/helpdesk/ai/
├── AiIntegrationException.java
├── AiJobService.java
├── AiJobWorker.java
├── AiTriageClient.java
├── AiEmbeddingClient.java
├── LocalAiTriageClient.java
├── LocalAiEmbeddingClient.java
├── FallbackTicketClassifier.java
├── TicketClassification.java
└── TicketEmbedding.java
```

O dominio de chamados continua responsavel pela regra de negocio:

```text
TicketService
├── cria chamado em transacao curta
├── grava classificacao inicial PENDENTE
├── cria jobs de IA
└── nao chama modelo local dentro da transacao de criacao

TicketSimilarityService
├── busca embedding do chamado base
├── calcula similares sob demanda
├── aplica filtro por grupo de status
└── retorna candidatos com score

TicketLinkService
├── cria vinculos persistidos entre chamados
├── lista vinculos por chamado
└── remove vinculos apenas por acao explicita
```

## Fluxo de Criacao

`POST /api/v1/tickets` deve responder rapidamente:

```text
1. Recebe titulo e descricao.
2. Abre transacao.
3. Salva o chamado como:
   - status: ABERTO
   - category: OUTROS
   - priority: MEDIA
   - classificationOrigin: PENDENTE
4. Cria jobs PENDING para CLASSIFICATION e EMBEDDING em ai_jobs.
5. Comita.
6. Retorna 201.
```

Nenhuma chamada ao Ollama acontece dentro desta transacao. Isso evita prender conexao de banco e thread HTTP enquanto o modelo local carrega ou processa.

## Processamento Assincrono

O backend adicionara Quartz com pool pequeno para consumir jobs persistidos em Postgres.

Configuracao conservadora:

```properties
app.ai.worker.enabled=${AI_WORKER_ENABLED:true}
app.ai.worker.batch-size=${AI_WORKER_BATCH_SIZE:1}
app.ai.worker.max-attempts=${AI_WORKER_MAX_ATTEMPTS:3}
app.ai.worker.request-timeout-seconds=${AI_REQUEST_TIMEOUT_SECONDS:20}
spring.quartz.properties.org.quartz.threadPool.threadCount=${QUARTZ_THREAD_COUNT:1}
```

O worker roda periodicamente e processa poucos jobs por ciclo:

```text
1. Busca jobs PENDING com nextAttemptAt vencido.
2. Marca job como PROCESSING.
3. Executa a chamada de IA com timeout.
4. Atualiza o chamado quando a resposta for valida.
5. Marca job como DONE.
6. Em falha, incrementa attempts, grava lastError e agenda backoff.
7. Ao exceder tentativas, marca FAILED.
```

O Quartz nao sera tratado como fila. A fila sera a tabela `ai_jobs`; o Quartz sera apenas o agendador/worker que consome essa fila. Assim os jobs sobrevivem a restart da aplicacao.

## Classificacao

O classificador local recebe titulo e descricao e deve retornar JSON estruturado:

```json
{
  "category": "SISTEMAS",
  "priority": "ALTA",
  "confidence": 0.82,
  "justification": "Descricao indica indisponibilidade de sistema interno para o usuario."
}
```

Valores aceitos:

- `category`: `ACESSO`, `SISTEMAS`, `INFRAESTRUTURA`, `EQUIPAMENTOS`, `FINANCEIRO`, `RH`, `OUTROS`
- `priority`: `BAIXA`, `MEDIA`, `ALTA`

Se a resposta do modelo for invalida, indisponivel ou expirar, o fallback deterministico classifica por palavras-chave simples e registra justificativa curta. Para o contrato atual, `classificationOrigin=IA` significa classificacao automatica do sistema, seja por modelo local ou por fallback deterministico. A justificativa deve deixar claro quando o fallback foi usado. Se nem o fallback produzir classificacao confiavel, o chamado permanece com `classificationOrigin=PENDENTE`.

Quando o modelo local ou fallback produzir classificacao valida:

- `category` e `priority` sao atualizados.
- `classificationOrigin` vira `IA`.
- `classificationJustification` recebe a justificativa.

## Revisao Manual

ADMIN podera corrigir classificacao:

```text
PATCH /api/v1/tickets/{id}/classification
```

Payload esperado:

```json
{
  "category": "INFRAESTRUTURA",
  "priority": "ALTA",
  "justification": "Chamado corrigido porque afeta rede do setor."
}
```

Ao corrigir:

- `classificationOrigin` vira `MANUAL`.
- `category`, `priority` e `classificationJustification` sao atualizados.
- A operacao deve respeitar autorizacao ADMIN.

Aceitar sugestao nao precisa de endpoint proprio no primeiro ciclo. A sugestao da IA ja fica ativa no chamado quando o job termina. Se o ADMIN nao corrigir, ela e considerada aceita implicitamente.

## Embeddings e Similares

O job `EMBEDDING` gera vetor a partir de:

```text
titulo + "\n\n" + descricao
```

O vetor sera persistido inicialmente no proprio banco relacional sem pgvector. A representacao pode ser JSON/texto ou outra forma simples validada pelo backend. A busca de similares sera calculada em Java com cosine similarity, adequada ao volume pequeno do desafio.

Endpoint sob demanda:

```text
GET /api/v1/tickets/{id}/similar?statusGroup=active|closed|all
```

Regras:

- `active`: considera `ABERTO` e `EM_ANDAMENTO`.
- `closed`: considera `RESOLVIDO` e `FECHADO`.
- `all`: considera todos os status.
- O chamado base nunca aparece como similar de si mesmo.
- Apenas chamados com embedding disponivel entram no calculo.
- O retorno respeita limite e threshold configuraveis.
- Se o chamado base ainda nao tiver embedding, retorna lista vazia com status HTTP 200.

Configuracoes:

```properties
app.ai.similarity.threshold=${AI_SIMILARITY_THRESHOLD:0.75}
app.ai.similarity.limit=${AI_SIMILARITY_LIMIT:5}
```

## Vinculos Persistidos

Chamados similares sugeridos pela IA nao viram vinculo automaticamente. O ADMIN decide quando persistir a relacao.

Endpoints:

```text
GET    /api/v1/tickets/{id}/links?statusGroup=active|closed|all
POST   /api/v1/tickets/{id}/links
DELETE /api/v1/tickets/{id}/links/{linkedTicketId}
```

Payload de criacao:

```json
{
  "linkedTicketId": "00000000-0000-0000-0000-000000000000"
}
```

Regras:

- O vinculo e bidirecional: se A esta relacionado a B, B tambem mostra A.
- O banco deve impedir duplicidade logica do par.
- Um chamado nao pode ser vinculado a si mesmo.
- Resolver ou fechar chamado nao remove o vinculo.
- A relacao e historica e ajuda a identificar recorrencia ou problema sistemico.
- Apenas ADMIN cria/remove vinculos no primeiro ciclo.

## Modelo de Dados

Alteracoes em `tickets`:

```text
classification_justification text null
embedding text null
embedding_model varchar(120) null
embedding_updated_at timestamp null
```

Nova tabela `ai_jobs`:

```text
id uuid primary key
ticket_id uuid not null
type varchar(30) not null
status varchar(30) not null
attempts integer not null
next_attempt_at timestamp not null
last_error text null
created_at timestamp not null
updated_at timestamp not null
```

Enums esperados:

- `AiJobType`: `CLASSIFICATION`, `EMBEDDING`
- `AiJobStatus`: `PENDING`, `PROCESSING`, `DONE`, `FAILED`

Nova tabela `ticket_links`:

```text
id uuid primary key
source_ticket_id uuid not null
target_ticket_id uuid not null
created_by uuid not null
created_at timestamp not null
```

Constraints esperadas:

- FK de `ai_jobs.ticket_id` para `tickets.id`.
- FK de `ticket_links.source_ticket_id` e `target_ticket_id` para `tickets.id`.
- FK de `ticket_links.created_by` para `users.id`.
- Check para `source_ticket_id <> target_ticket_id`.
- Par canonico para evitar duplicidade bidirecional. A implementacao pode normalizar a ordem dos UUIDs antes de salvar.

## Docker e Stacks por Worktree

O `docker-compose.yml` deve permitir stacks paralelas para worktrees diferentes.

Decisoes:

- Usar `name: ${COMPOSE_PROJECT_NAME:-fadex-helpdesk}`.
- Remover `container_name` fixo dos servicos.
- Manter portas configuraveis por variaveis de ambiente.
- Adicionar `ollama` como servico opcional da stack.
- Nao criar comando novo no Makefile neste ciclo.

Exemplo de ambiente temporario por worktree:

```env
COMPOSE_PROJECT_NAME=fadex-triagem
POSTGRES_PORT=15432
BACKEND_PORT=18080
FRONTEND_PORT=13080
MAILPIT_SMTP_PORT=11025
MAILPIT_UI_PORT=18025
OLLAMA_PORT=11435
AI_WORKER_BATCH_SIZE=1
QUARTZ_THREAD_COUNT=1
```

O backend em container deve usar:

```env
AI_BASE_URL=http://ollama:11434
```

O backend rodando fora do Docker deve usar:

```env
AI_BASE_URL=http://localhost:11434
```

## Configuracao de IA

Variaveis esperadas:

```env
AI_TRIAGE_ENABLED=false
AI_BASE_URL=http://localhost:11434
AI_CLASSIFICATION_MODEL=llama3.2:1b
AI_EMBEDDING_MODEL=all-minilm
AI_WORKER_ENABLED=true
AI_WORKER_BATCH_SIZE=1
AI_WORKER_MAX_ATTEMPTS=3
AI_REQUEST_TIMEOUT_SECONDS=20
AI_SIMILARITY_THRESHOLD=0.75
AI_SIMILARITY_LIMIT=5
QUARTZ_THREAD_COUNT=1
```

`AI_TRIAGE_ENABLED=false` e o default mais seguro para desenvolvimento local sem Ollama. Na stack com Ollama, o valor pode ser ativado por `.env` da worktree. Os nomes de modelo podem ser ajustados conforme disponibilidade local. A documentacao final deve explicar que o avaliador pode usar outro modelo compativel com Ollama alterando as variaveis.

## Autorizacao

Regras iniciais:

- Criacao de chamado continua disponivel para usuario autenticado.
- Revisao de classificacao e gerenciamento de vinculos sao exclusivos de ADMIN.
- Listagem de similares e links deve respeitar as regras de visibilidade de chamados.
- SOLICITANTE nao deve receber dados de chamados de outros solicitantes quando a autorizacao por papel estiver aplicada.

Se a regra ADMIN x SOLICITANTE ainda estiver em evolucao no momento da implementacao, os novos endpoints devem ser implementados de forma compativel com o service de autorizacao existente, sem duplicar regra nos controllers.

## Erros e Degradacao

Falha do modelo local nao deve quebrar CRUD basico.

Comportamentos esperados:

- Timeout ou erro no Ollama marca job como FAILED apenas depois do numero maximo de tentativas.
- Enquanto o job estiver pendente, o chamado mostra `classificationOrigin=PENDENTE`.
- Se embedding nao existir, `/similar` retorna lista vazia.
- Payload invalido em revisao manual retorna 400.
- Chamado inexistente retorna 404.
- Usuario sem permissao retorna 403.
- Falhas inesperadas continuam passando pelo `GlobalExceptionHandler`.

## Testes

Cobertura minima de backend:

- `TicketServiceTest`: criacao de chamado enfileira jobs e nao chama IA sincrona.
- `AiJobServiceTest`: cria jobs, controla tentativas e backoff.
- `AiJobWorkerTest`: processa classificacao valida, embedding valido e falhas.
- `FallbackTicketClassifierTest`: cobre palavras-chave para categoria/prioridade.
- `TicketSimilarityServiceTest`: calcula cosine similarity, aplica threshold, limit e statusGroup.
- `TicketLinkServiceTest`: cria vinculo bidirecional logico, impede auto-vinculo e duplicidade.
- Controller tests para revisao de classificacao, similares e links.
- Teste de propriedades para defaults de IA/Quartz.

Verificacoes:

```bash
make backend-test
make backend-build
```

Se houver alteracao de contrato consumido pelo frontend:

```bash
make frontend-lint
make frontend-build
```

## Documentacao a Atualizar na Implementacao

- `docs/backend/api.md`: novos endpoints e campos de resposta.
- `docs/configuracao/env.md`: Ollama, variaveis de IA, stacks por worktree e portas.
- `docs/projeto/acompanhamento-desenvolvimento.md`: status de IA, similares e pendencias.
- `README.md`: justificativa da abordagem de IA local, fallback, comandos de execucao e exemplos.

## Criterios de Aceite

- Criar chamado nunca depende de resposta sincrona do modelo local.
- Jobs de classificacao e embedding sao persistidos.
- Worker Quartz processa jobs com concorrencia baixa e retry limitado.
- Chamado recebe categoria/prioridade/justificativa quando classificacao termina.
- ADMIN consegue corrigir classificacao manualmente.
- Embedding e salvo quando o job termina.
- Endpoint de similares retorna candidatos com score e filtro `statusGroup`.
- ADMIN consegue persistir/remover vinculos entre chamados.
- Vinculos persistidos sao bidirecionais e permanecem apos resolucao/fechamento.
- Compose permite stacks paralelas por worktree sem conflito de `container_name`.
- A aplicacao continua funcional sem Ollama ativo.
