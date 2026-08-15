# Revisão técnica — Fadex Helpdesk

Data: 2026-08-14 (prazo de entrega: 15/08/2026, 12h)
Escopo: auditoria de leitura sobre `backend/`, migrações, configuração e infraestrutura, com o
enunciado `Desafio_Analista_Desenvolvimento_Fadex.pdf` como referência normativa.

## Método e honestidade das evidências

Este documento separa deliberadamente **o que foi medido** do **que foi lido no código**.

**Medido de fato:**

1. **Contagem de consultas SQL da listagem de chamados.** Escrevi um teste descartável
   (`@DataJpaTest` + `hibernate.generate_statistics=true`, lendo `Statistics.getEntityFetchCount()`),
   rodei via `./gradlew test`, coletei os números e **removi o arquivo**. O worktree ficou limpo
   (`git status` sem pendências). Nenhum código de produção foi alterado.
2. **Teto de tamanho de página**, via `curl` autenticado como ADMIN contra a stack de pé.
3. **Estado real do banco** (`docker exec ... psql`, somente `select`): volume de chamados,
   usuários, fila `ai_jobs`, embeddings preenchidos.
4. **Autorização de `GET /users` como SOLICITANTE**, via `curl`, para testar uma suspeita que
   acabou **refutada** (ver §4.6).
5. **Triagem por IA de ponta a ponta**, criando dois chamados reais e acompanhando a classificação
   por *polling* — o que revelou o achado mais grave desta auditoria (§4.0).
6. **Suíte de testes completa.** `./gradlew test` → **BUILD SUCCESSFUL**: **289 testes em 55
   classes, 0 falhas, 0 erros, 0 ignorados**. Roda em H2 na memória, sem tocar a stack de pé. É a
   base que sustenta a afirmação de "testes automatizados" como diferencial entregue (§1.5) e o
   pressuposto de suíte verde nas correções recomendadas em §5.1.

**Inferido do código, não medido:** vazão do worker de IA sob carga, comportamento do executor sob
saturação, e o efeito de SMTP lento. Onde infiro, digo que infiro e mostro o caminho no código.

Nenhum serviço foi derrubado ou reconstruído. Nada em `frontend/**` foi tocado.

**Resíduo desta auditoria, a limpar antes de qualquer demonstração.** Os dois chamados criados para
medir a triagem **continuam no banco de desenvolvimento** — não os removi porque outra frente está
usando o banco e, ironicamente, **não existe endpoint de exclusão** (§1.2 item 3) para fazer isso
pela API. São eles:

- `3db84964-3e89-417d-932e-3408ff530b0b` — "Revisao tecnica - teste de triagem"
- `93b64baf-841f-43f7-be25-9de489fbb196` — "Impressora do setor financeiro sem toner"

O primeiro título denuncia a origem. Quando a stack estiver livre, apagar nesta ordem (chaves
estrangeiras): `ai_jobs` → `ticket_events` → `ticket_links` → `tickets`. Alternativa mais limpa:
recriar o banco com o seed, já que é ambiente de desenvolvimento.

---

## 1. Requisito a requisito

### 1.1 Entidades mínimas (§2.1)

| Requisito | Situação | Evidência |
|---|---|---|
| Usuário: nome, e-mail, senha com hash, papel ADMIN/SOLICITANTE | **Atendido** | `model/user/User.java`; hash BCrypt em `security/SecurityConfig.java:76-78`; `model/enums/Role.java`; unicidade em `db/migration/V1__create_initial_schema.sql:9` |
| Chamado: título, descrição, categoria, prioridade, status, solicitante, responsável opcional, origem da classificação, datas | **Atendido** | `model/ticket/Ticket.java:37-106`; `classification_origin` em `:64`; `assignee` opcional em `:59` |
| Comentário/Histórico: autor, texto, data | **Atendido** | `model/comment/TicketComment.java`; `model/event/TicketEvent.java`; migração `V2__auth_rbac_history.sql:17-25` |

Observação favorável: o modelo vai **além** do mínimo com `TicketEvent` (histórico estruturado por
tipo) separado de `TicketComment` (interação humana). Isso é modelagem madura, não inchaço — o
enunciado pede "registro de interações **e** mudanças de status", que são coisas de natureza
diferente.

### 1.2 Funcionalidades obrigatórias (§2.2)

| # | Requisito | Situação | Evidência / o que falta |
|---|---|---|---|
| 1 | Autenticação com emissão de token | **Atendido** | `controller/AuthController.java:28-42`; `security/JwtConfig.java`; JWT HS256. **Ressalva:** não há endpoint público de *cadastro* — `POST /users` exige ADMIN (`service/UserService.java:70`). É uma decisão defensável (helpdesk interno) mas **precisa estar justificada no README**, porque o checklist tem a linha "Cadastro de usuário funcional com hash de senha". |
| 2 | Autorização ADMIN × SOLICITANTE | **Atendido** | `security/AccessControlService.java:126-170`; filtro por papel na listagem em `service/TicketService.java:306` e `service/UserService.java:51`; verificado ao vivo (§4.6) |
| 3 | CRUD completo: criar, listar com filtros, detalhe, atualizar status, **excluir/cancelar** | **PARCIAL — maior lacuna funcional** | Criar/listar/detalhe/status: `controller/TicketController.java:40-72`. **Não existe `DELETE /api/v1/tickets/{id}` nem status `CANCELADO`.** O único `@DeleteMapping` é `/{id}/assignee` (`:84`), que remove responsável, não o chamado. `model/enums/TicketStatus.java:4-7` tem apenas ABERTO/EM_ANDAMENTO/RESOLVIDO/FECHADO. |
| 4 | Triagem automática por IA; ADMIN pode aceitar ou corrigir | **Atendido — verificado ao vivo**, com ressalva importante | Assíncrono via `ai/job/AiJobWorker.java`; correção pelo ADMIN em `ai/classification/TicketClassificationController.java:31-32`. **Medido:** dois chamados criados pela API foram classificados em ~17 s e ~15 s. **Ressalva:** ambos vieram da heurística de fallback, não do modelo — o caminho do LLM falha em 100% dos casos, silenciosamente (**ver §4.0**). Continua **conforme** §3.3, que permite heurística, mas muda o que o README pode afirmar. |
| 5 | Indicadores em tempo real + alerta de prioridade ALTA | **Atendido** | `ai/indicator/IndicatorController.java`; SSE em `sse/controller/NotificationController.java:21`; alerta ALTA em `notification/TicketSseNotificationListener.java:66-72` |
| 6 | Comentários/histórico em chamado existente | **Atendido** | `controller/TicketCommentController.java:47`; `controller/TicketEventController.java:30` |
| 7 | Validações de negócio (não reabrir fechado, campos obrigatórios, e-mail único) | **Atendido** | Não reabrir: `service/TicketService.java:218-221` lança `ConflictException`. Matriz de transições: `model/ticket/TicketStatusTransition.java:35-49` (FECHADO → conjunto vazio). Campos: `@NotBlank`/`@Size` nos DTOs. E-mail único: constraint `uk_users_email` + `service/UserService.java:97-103`. |

### 1.3 Requisitos técnicos obrigatórios (§3.1)

| Requisito | Situação | Evidência / o que falta |
|---|---|---|
| Repositório público com histórico de commits granular | **Atendido** | Histórico com commits pequenos, em português, com escopo (`feat(backend):`, `docs(projeto):`) |
| **README.md** com descrição, tecnologias, instalação passo a passo e como popular/testar a API | **NÃO ATENDIDO** | **Não existe `README.md` em lugar nenhum do repositório** (`git ls-files` confirmado). Há `AGENTS.md`, `docs/` e um wizard `setup.sh`, mas nada que o avaliador reconheça como README. |
| Persistência relacional | **Atendido** | PostgreSQL 17 + Flyway V1–V6; `docker-compose.yml` |
| Tratamento de erros 400/401/403/404/500 | **Atendido** | `exception/GlobalExceptionHandler.java`; 500 genérico sem vazar interno (`:120-134`) |
| Organização em camadas | **Atendido** | controller / service / repository / model consistente |

### 1.4 Diretrizes de IA e segurança (§3.3)

| Requisito | Situação | Evidência |
|---|---|---|
| Nenhum segredo commitado | **Atendido** | `.env.example` com placeholders; `.gitignore` cobre `.env` |
| `.env.example` presente | **Atendido** | `/.env.example`, `backend/.env.example`, `frontend/.env.example` |
| README com usuário de teste ADMIN e SOLICITANTE | **NÃO ATENDIDO** | Consequência da ausência de README. As credenciais existem e funcionam (`config/DevDataSeeder.java`), mas não estão documentadas onde o avaliador vai procurar. |

### 1.5 Diferenciais (§3.2) — todos entregues

Interface web (Next.js), detecção de duplicados por embedding, Docker Compose de um comando,
testes automatizados (~50 classes), Swagger/OpenAPI (`config/OpenApiConfig.java`). Deploy público
não há — é o único item de §3.2 ausente, e vale 0 nesta altura.

---

## 2. O que fizemos além do pedido

| Item | Agrega na avaliação? | Leitura honesta |
|---|---|---|
| **SSE** (`sse/`) | **Muito.** | É o item 5 obrigatório resolvido pelo caminho mais bem avaliado dos três oferecidos (WebSocket/SSE/polling). Registro de conexões, heartbeat, reconexão. Peso direto nos 20% de "Triagem por IA e tempo real". |
| **Detecção de duplicados por embedding** (`ai/duplicate/`) | **Muito.** | Diferencial explícito de §3.2, implementado do jeito "caro" (pgvector + HNSW), não com `LIKE`. Demonstra exatamente a "iniciativa acima do CRUD tradicional" que o §1 pede. |
| **Histórico de eventos** (`TicketEvent`) | **Sim.** | Sustenta o item 6 obrigatório e a linha de checklist "histórico em ordem cronológica" melhor que comentários sozinhos. |
| **E-mails com Thymeleaf** (`mail/`, `notification/`) | **Pouco.** | Nada no enunciado pede e-mail. Sete templates HTML com layout e componentes é bastante superfície para zero pontos diretos. Não é peso morto *nocivo* — mostra domínio de Spring — mas é onde mais esforço foi gasto pelo menor retorno de nota. Além disso é a origem do risco de concorrência mais grave do sistema (§4.1). |
| **Indicadores em quatro camadas** (`ai/indicator/`, ~15 DTOs) | **Misto.** | O enunciado pede "contagem por status/prioridade". Entregamos SLA, aging de backlog, carga por responsável, taxa de concordância da IA, volume por solicitante. Isso impressiona *se* a tela mostrar; se não mostrar, é código não exercitado. O `IndicatorService` tem ~20 passagens de stream sobre a mesma lista. |
| **Wizard de instalação** (`setup.sh`/`setup.cmd`) | **Ambíguo — e hoje é um risco.** | Reduz atrito de execução, o que ajuda em "facilidade de reproduzir" (5%). Mas o enunciado pede **README com passo a passo**, e um script não substitui isso no checklist. Hoje o wizard é o *único* caminho documentado, e é justamente o que não é avaliado. |
| **Seed com volume** (`DevTicketSeeder`) | **Sim, mas menor do que se pensa.** | Medido: o banco tem **31 chamados e 9 usuários**. É um seed de *demonstração*, não de volume. Serve bem para a tela de indicadores não ficar vazia — que é o objetivo real. Só não vale descrevê-lo como teste de carga. |

**Veredito:** SSE, duplicados e histórico pagam com folga. E-mail e a profundidade dos indicadores
são investimento acima do necessário; não removê-los agora, mas também não gastar mais tempo neles.

---

## 3. Pontos fortes e fracos

### Fortes

1. **A matriz de transição de status é um dado, não uma cascata de `if`.**
   `model/ticket/TicketStatusTransition.java` — `FECHADO → Set.of()` trata o estado terminal como
   dado. Regra de negócio testável e consultável por várias camadas.
2. **Uma única porta de escrita para classificação.** `TicketService.applyClassification`
   (`:247`) é a única via que grava categoria/prioridade/origem; o worker de IA não muta o chamado
   diretamente. Fronteira bem desenhada, e comentada no código explicando *por que*.
3. **A projeção de indicadores é uma consulta só, com `join`/`left join` explícitos.**
   `ai/indicator/IndicatorRepository.java` — a frente de indicadores evitou o N+1 que a frente de
   API não evitou. Vale registrar o contraste.
4. **Tratamento de erro não vaza interno.** `GlobalExceptionHandler:120-134` devolve 500 genérico.
5. **Ciclo de vida do SSE está correto.** `NotificationService:50-52` remove a inscrição em
   `onCompletion`, `onTimeout` **e** `onError`, e remove também em falha de envio (`:84,:91`).
   Conexão órfã **é** liberada — investiguei esperando um vazamento e não há.
6. **Comentários no código explicam decisão, não mecânica.** Vários trechos justificam *por que* a
   ordem é aquela. Isso conta nos 20% de qualidade de código.

### Fracos

1. **A integração com o LLM está morta e falha em silêncio** (§4.0). Descoberto medindo, não
   lendo. É o achado que melhor resume o risco de acreditar em relatos de frente sem verificar.
2. **Não existe README.** É o ponto fraco mais caro do projeto e não é técnico. Ele aparece em
   §3.1, em §4 (entregáveis) e em três linhas do checklist interno. Todo o resto desta auditoria
   vale menos que corrigir isto.
3. **O CRUD obrigatório está incompleto** — sem excluir nem cancelar (§1.2 item 3). É a única
   funcionalidade obrigatória com lacuna real.
4. **N+1 comprovado na listagem de chamados** (§4.3). Mede-se, existe, e a correção é de três
   linhas.
5. **A vazão da triagem é de 1 chamado a cada 10 segundos** (§4.1). Para uma feature que vale 20%
   da nota e que o avaliador vai testar criando chamados, isso é lento o bastante para parecer
   quebrado.
6. **Assimetria de investimento.** Sete templates de e-mail e ~15 DTOs de indicadores convivem com
   a ausência de um `DELETE /tickets/{id}` de dez linhas que o enunciado pede explicitamente.
   Um avaliador percebe essa assimetria e ela custa em "funcionalidade".
7. **`@Modifying` sem `flushAutomatically`/`clearAutomatically`** (§4.2) — e a frente de IA
   registrou tê-lo tratado. O relato não bate com o código.

---

## 4. Riscos técnicos

### 4.0 O modelo de IA nunca é usado: toda classificação cai no fallback — **gravidade alta**

Este achado foi encontrado **medindo**, não lendo, e é o mais importante do documento.

#### O que foi medido

Criei dois chamados reais pela API como SOLICITANTE e acompanhei a classificação:

| Chamado | Tempo até classificar | Resultado | `classificationJustification` |
|---|---|---|---|
| "Servidor de folha de pagamento fora do ar… Urgente" | **~17 s** | `IA` / SISTEMAS / ALTA / conf. 0,6 | "Classificacao por **fallback deterministico** baseado em palavras-chave." |
| "Impressora sem toner… **não é urgente**" | **~15 s** | `IA` / EQUIPAMENTOS / **ALTA** / conf. 0,6 | "Classificacao por **fallback deterministico**…" |

A triagem **funciona** de ponta a ponta e o requisito obrigatório 4 está atendido. Mas em **nenhum**
dos dois casos o modelo de linguagem foi usado: as duas classificações vieram da heurística de
palavras-chave, sempre com confiança fixa 0,6. Note também que o segundo chamado diz
explicitamente "não é urgente" e foi classificado como **ALTA** — a heurística erra onde o modelo
acertaria.

#### Não é falta de ambiente

Descartei as explicações fáceis, uma a uma:

- `docker exec fadex-helpdesk-backend-1 printenv` → **`AI_TRIAGE_ENABLED=true`**;
- `docker exec fadex-helpdesk-ollama-1 ollama list` → **ambos os modelos baixados**
  (`llama3.2:1b`, `all-minilm`);
- `AI_BASE_URL=http://ollama:11434` — correto para a rede interna do compose;
- Ollama responde **em 2,4 s** a partir de dentro do container do backend (testei com `wget`), bem
  abaixo do timeout de 20 s;
- não é *cold start*: o segundo chamado foi criado com o Ollama **já aquecido** e caiu no fallback
  do mesmo jeito.

#### Causa raiz — provada

`ai/client/LocalAiTriageClient.java:24` define o prompt de sistema:

> "Classifique o chamado e responda apenas um objeto JSON com category, priority, confidence e
> justification. **category e priority devem usar os valores dos enums fornecidos.**"

**Os valores dos enums nunca são fornecidos.** `formatTicket` (`:78-80`) envia apenas
`"Titulo: … Descricao: …"`. Nenhum ponto da requisição lista `SISTEMAS`, `EQUIPAMENTOS`, `ALTA`,
`MEDIA` etc. O modelo é instruído a usar uma lista que não recebeu.

Reproduzi a chamada exata (mesmo modelo, mesmo prompt, `format: json`, `temperature: 0`) direto no
Ollama. Resposta:

```json
{
  "category": "Impressora",
  "priority": "Médio",
  "confidence": 0.8,
  "justification": "A impressora da sala 12 parou de imprimir…"
}
```

`"Impressora"` e `"Médio"` **não são valores válidos**. Em `parseClassification:97-98`,
`TicketCategory.valueOf("Impressora")` lança `IllegalArgumentException`, capturada em `:73-74` e
convertida em `AiIntegrationException`. Em `AiJobWorker.classifyWithFallback:196-202` essa exceção
é capturada e **descartada sem uma única linha de log** — daí a queda para a heurística ser
invisível. Confirmei nos logs do backend: nenhum registro de erro de IA.

O JSON é sintaticamente válido (`format: json` garante isso), então a falha não é de parsing de
JSON — é de **vocabulário**. E é **determinística**: acontece em toda classificação.

#### Impacto concreto

1. Atinge o critério de **20%** ("Triagem por IA e tempo real"). Nada está tecnicamente errado
   perante §3.3 — heurística é permitida —, mas **o README não pode afirmar que a integração com
   Ollama classifica os chamados**, porque hoje ela não classifica nenhum.
2. `classificationOrigin = IA` com classificação vinda da heurística é **enganoso** para quem
   avalia o banco ou a tela.
3. A engolida silenciosa da exceção é um defeito de engenharia por si só: a integração inteira pode
   estar morta sem nenhum sinal.

#### Custo de correção — baixo, e o de melhor retorno da auditoria

Incluir os valores permitidos no prompt. Uma alteração de poucas linhas em `LocalAiTriageClient`,
enumerando `TicketCategory.values()` e `TicketPriority.values()` no prompt de sistema (idealmente
derivados do enum, não escritos à mão). Somar `log.warn` no `catch` de `classifyWithFallback` para
que a queda ao fallback deixe de ser silenciosa.

**Das duas metades, o `log.warn` é a mais durável** — é o que torna visível a *próxima* falha
silenciosa, independentemente de o ajuste de prompt se sustentar. Faça as duas, mas se só couber
uma, faça o log.

**E verifique depois de aplicar, como em §4.3-A:** enumerar os enums no prompt **não garante** que
um modelo de 1 bilhão de parâmetros passe a respeitá-los — ele pode continuar respondendo `"Alta"`
em vez de `ALTA`, ou traduzir os rótulos. O teste é de 30 segundos: criar um chamado e conferir se
`classificationJustification` **deixa** de dizer "fallback deterministico". Se continuar caindo no
fallback, o caminho robusto é normalizar a resposta (maiúsculas, sem acento, com um mapa de
sinônimos) antes do `valueOf`, em vez de confiar na obediência do modelo.

**Alternativa de custo zero, se o tempo acabar:** manter como está e **descrever com precisão no
README** — "a triagem usa heurística determinística de palavras-chave; a integração com Ollama está
implementada e é selecionável por configuração". Isso é 100% conforme §3.3. O que **não** pode
acontecer é o README descrever uma classificação por LLM que não ocorre.

**Nota de ambiente relacionada (gravidade média):** o serviço que baixa os modelos está atrás de um
profile — `docker-compose.yml:103` (`profiles: ["modelos"]`). Um avaliador que rode
`docker compose up` puro **não** baixa os modelos, e cairia no fallback mesmo com o prompt
corrigido. O `setup.sh` trata disso e avisa (`:597-603`, `:714`), mas o README precisa dizer
explicitamente qual comando baixa os modelos. (Detalhe menor: o comentário em `setup.sh:637` afirma
que "o serviço ollama-models não tem 'profiles' no compose" — hoje tem; comentário desatualizado.)

### 4.1 Concorrência: vazão da triagem e executor compartilhado

#### A) Vazão real da classificação — **gravidade média** (alta em percepção de avaliação)

**Evidência:** `application.properties:47` (`batch-size=1`), `:50` (`interval-millis=10000`),
`:14` (`QUARTZ_THREAD_COUNT=1`); `ai/job/AiJobWorker.java:35` (`@DisallowConcurrentExecution`),
`:99` (`findDueJobs(now, batchSize)` — pega **um** job por ciclo).

**Medido, para um chamado:** criei dois chamados reais e a classificação levou **~17 s** e **~15 s**
(§4.0). Isso confirma o modelo de vazão abaixo.

**Extrapolação para carga (calculada a partir do código, não medida sob carga):** o worker processa
**1 job por disparo, a cada 10 s**. E `AiJobService.enqueueTicketJobs:31-38` enfileira **dois** jobs
por chamado (CLASSIFICATION + EMBEDDING). Logo:

- 1 chamado → 2 jobs → **~20 s** — batendo com os ~15–17 s medidos.
- **50 chamados criados de uma vez → 100 jobs → ~1000 s ≈ 16 minutos** para drenar a fila.

Nada quebra — a fila é persistente (`ai_jobs`), `@DisallowConcurrentExecution` evita sobreposição,
e os jobs são drenados na ordem. O risco é de **percepção**: o avaliador cria alguns chamados,
não vê a categoria mudar em tempo hábil e conclui que a triagem não funciona.

**Medido:** a fila está hoje **inteiramente drenada** — `select type,status,count(*) from ai_jobs`
retorna `CLASSIFICATION|DONE|11` e `EMBEDDING|DONE|11`, zero pendentes. Não há backlog acumulado.
(Os 31 chamados do banco contra 11 jobs indicam que o seed insere a maior parte já classificada,
sem passar pela fila.)

**Impacto concreto:** demonstração parece lenta ou quebrada no item que vale 20% da nota.
**Custo de correção:** trivial e sem tocar código — `AI_WORKER_BATCH_SIZE=10` e
`AI_WORKER_INTERVAL_MILLIS=2000` no `.env`. Sobe a vazão para 5 jobs/s, ~20 s para os mesmos 100
jobs. Como `@DisallowConcurrentExecution` continua garantindo uma execução por vez, aumentar o
lote é seguro; só alonga a transação (ver §4.2-C).

#### B) E-mail e SSE dividem o mesmo executor — **gravidade alta**

**Evidência:** `sse/config/AsyncConfig.java:16-18` — `CORE_POOL_SIZE=2`, `MAX_POOL_SIZE=4`,
`QUEUE_CAPACITY=500`. Os dois listeners declaram o mesmo executor:
`notification/TicketSseNotificationListener.java:40` e
`notification/EmailNotificationListener.java:134,140,152`.

**Duas coisas que se somam, e a segunda é a que dói:**

1. `ThreadPoolTaskExecutor` só cresce além do *core* quando a fila **enche**. Com fila de 500, o
   pool opera com **2 threads efetivas**, não 4 — o `MAX_POOL_SIZE=4` é decorativo até haver 500
   tarefas enfileiradas.
2. **Não há nenhum timeout de SMTP configurado.** Nenhuma das propriedades
   `mail.smtp.connectiontimeout`, `mail.smtp.timeout` ou `mail.smtp.writetimeout` aparece em
   `application.properties:30-36`, e `mail/SmtpEmailSender.java` chama `mailSender.send()` direto.
   O padrão do JavaMail é **espera infinita**.

**Impacto concreto:** um host SMTP que aceita a conexão TCP e não responde (caso clássico de
firewall com *drop* silencioso, ou Mailpit pausado) **prende as duas threads para sempre**. A
partir daí nenhuma notificação SSE é despachada — os indicadores em tempo real param, em silêncio,
e nada no log diz "parei". Com `SMTP_HOST` apontando para fora de `localhost` isso deixa de ser
hipotético. Como o SSE é o coração dos 20% de "tempo real", a falha atinge o item de maior peso.

**Custo de correção:** ~3 linhas em `application.properties`, sem tocar Java:

```properties
spring.mail.properties.mail.smtp.connectiontimeout=${SMTP_CONNECTION_TIMEOUT_MS:5000}
spring.mail.properties.mail.smtp.timeout=${SMTP_TIMEOUT_MS:5000}
spring.mail.properties.mail.smtp.writetimeout=${SMTP_WRITE_TIMEOUT_MS:5000}
```

Separar os dois executores seria o conserto arquitetural correto, mas é mudança de produção e
**não** cabe nas horas restantes. Os timeouts resolvem a falha catastrófica; a separação fica como
dívida declarada.

#### C) Saturação da fila do executor — **gravidade baixa**

Sem `RejectedExecutionHandler` configurado, o padrão é `AbortPolicy`: acima de 500 tarefas
enfileiradas + 4 em execução, novas notificações são descartadas com `TaskRejectedException`.
Como isso ocorre dentro de `@Async` a partir de um listener `AFTER_COMMIT`, a exceção vai para o
handler assíncrono padrão (log) e a notificação se perde. **No volume desta aplicação (31 chamados,
9 usuários) é inalcançável.** Registro por completude; não vale tocar.

### 4.2 Transações

#### A) `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` — perda silenciosa? — **gravidade baixa, por desenho**

**Evidência:** ambos os listeners rodam `AFTER_COMMIT`. `TicketSseNotificationListener:44-53`
envolve cada despacho em `try/catch` e **loga** a falha; `EmailNotificationListener:199-212` faz o
mesmo no envio.

**Resposta direta à pergunta:** se o listener falha depois do commit, **a operação de negócio está
commitada e a notificação se perde** — não há reenvio nem outbox. Mas a perda **não é silenciosa**:
os dois caminhos registram `log.error` com o `eventId` da mensagem. É a escolha certa para o
escopo (e-mail que não sai não pode desfazer a criação de um chamado, como o próprio código
documenta em `UserService:79-81`).

Uma exceção real: `EmailNotificationListener:137` chama `emailComposer.compose(event)` **fora** do
`try`. Se a composição do template falhar, a exceção escapa para o handler assíncrono padrão —
ainda logada, mas por um caminho menos explícito. Cosmético.

**Custo de um outbox:** alto (tabela, worker, idempotência). **Dívida consciente** — vale citar no
README como limitação conhecida, não corrigir.

#### B) `AiJobWorker` sob `@Transactional`: escrita nativa × dirty checking — **gravidade média**

**Evidência:** `ai/job/AiJobWorker.java:88` (`@Transactional` no `execute`); `:207` chama
`ticketEmbeddingRepository.updateEmbedding(...)`, que é um `update` **nativo**
(`repository/TicketEmbeddingRepository.java:14-27`).

**A frente de IA relatou ter tratado isso com `flushAutomatically`/`clearAutomatically`. Isso não
confere.** A anotação no código é `@Modifying` **puro**, na linha 14, sem nenhum dos dois
atributos. O relato está errado.

**Por que isso importa:** o `Ticket` está *managed* no mesmo contexto de persistência (veio de
`job.getTicket()`, linha 205) e a entidade mapeia `embedding_model` e `embedding_updated_at`
(`Ticket.java:70-73`) — exatamente duas das três colunas que o `update` nativo escreve. Sem
`clearAutomatically=true`, a instância em memória mantém os valores **antigos** (tipicamente
`null`) depois da escrita nativa. Se qualquer código posterior na mesma transação sujar o `Ticket`,
o *dirty checking* do Hibernate no flush regrava os valores obsoletos, **desfazendo em silêncio**
parte da escrita nativa.

**Hoje o bug não dispara:** em `processEmbedding` o ticket não é modificado, e
`duplicateDetectionService.detect()` (`:217`) trabalha por `ticketId`. É uma **mina armada**, não
uma falha ativa — qualquer edição futura que toque o ticket nesse trecho a detona, e a falha seria
silenciosa e difícil de diagnosticar.

**Custo de correção:** uma linha —
`@Modifying(flushAutomatically = true, clearAutomatically = true)`. É código de produção, e a
frente de IA é a dona do arquivo.

#### C) Transação longa segurando conexão durante HTTP para o modelo — **gravidade média**

**Evidência:** `AiJobWorker.execute` é `@Transactional` (`:88`) e, dentro dela, `:198`
(`aiTriageClient.classify`) e `:206` (`aiEmbeddingClient.embed`) fazem chamadas HTTP ao Ollama com
timeout de **20 s** (`application.properties:49`).

**Sim, prende conexão do pool.** A transação abre no início do `execute`, a conexão do Hikari fica
associada a ela, e a chamada HTTP acontece **dentro** desse escopo. No pior caso a conexão fica
retida por 20 s (ou 40 s, se classificação e embedding caíssem no mesmo lote).

**Por que o impacto hoje é contido:** `batch-size=1` e `QUARTZ_THREAD_COUNT=1` significam que
**exatamente uma** conexão fica presa por vez, contra um pool Hikari padrão de 10. Sobram 9 para o
tráfego HTTP. O desenho ruim está compensado pelo dimensionamento conservador.

**Onde vira problema:** é exatamente o custo escondido da correção recomendada em §4.1-A. Subir
`AI_WORKER_BATCH_SIZE` para 10 faz uma única transação segurar a conexão por até 10 × 20 s = 200 s.
**Portanto: ao aumentar o lote, aumente o intervalo com cuidado e prefira lote ≤ 5.** Com o
fallback heurístico (`AI_TRIAGE_ENABLED=false`) não há HTTP nenhum e o ponto some.

O conserto correto — chamar o modelo fora da transação e abrir transação curta só para persistir —
é refatoração real do worker. **Não tocar agora.**

#### D) Escritas sem transação e autoinvocação — **sem achado**

Varri os serviços. `@Transactional` está sempre em método **público** de bean Spring, chamado de
fora (controller → service). Não encontrei `@Transactional` em método privado nem autoinvocação
que burlasse o proxy. `RefreshTokenService.create` (`:36`) não é anotado, mas sua única escrita é
`repository.save()`, que já é transacional em `SimpleJpaRepository`. **Verificado e são.**

### 4.3 Desempenho

#### A) N+1 em `GET /tickets` — **CONFIRMADO POR MEDIÇÃO — gravidade média**

**Evidência no código:** `Ticket.java:55,59` — `requester` e `assignee` são `FetchType.LAZY`;
`TicketMapper.toMinDto:68-84` acessa ambos e chama `UserMapper.toMinDto`, que lê `getName()` —
o que **força a inicialização do proxy**. `TicketRepository.java:9` é uma interface vazia, sem
`@EntityGraph`; `TicketService.findAll:66-73` usa `findAll(spec, pageable)` puro.

**Medição.** Teste descartável com `Statistics` do Hibernate, três cenários isolados (um método de
teste por cenário, para não haver contaminação do contexto de persistência entre eles):

| Cenário | Chamados na página | Usuários distintos | `entityFetchCount` (buscas lazy) | `prepareStatementCount` |
|---|---|---|---|---|
| A | 20 | 3 | **3** | 5 |
| B | 20 | 20 | **20** | 22 |
| C | 50 | 50 | **50** | 52 |

**O que os números provam.** O N+1 é real: `entityFetchCount` escala **1:1 com o número de
usuários distintos na página**. Mas o multiplicador **não é o número de linhas** — é o número de
usuários *distintos*, porque o contexto de persistência de primeiro nível deduplica (cenário A:
20 linhas, só 3 consultas). O pior caso é uma consulta por linha, quando cada chamado tem um
solicitante diferente: **50 linhas → 52 consultas, onde um `join fetch` usaria 2.**

Uma primeira tentativa de medir isso contra a stack de pé, por *deltas* de `pg_stat_user_tables`,
deu resultados incoerentes (o coletor de estatísticas do Postgres tem atraso e havia tráfego de
outra frente). **Descartei esses números** em vez de reportá-los; a tabela acima vem do teste
isolado.

**Impacto concreto:** no volume atual (medido: 31 chamados, 9 usuários) o custo é de poucas
consultas — irrelevante. Numa base real com centenas de solicitantes e página de 2000 (§4.3-C),
seriam ~2000 consultas numa requisição.

**Custo de correção:** três linhas, sem mudar nenhum chamador —

```java
@EntityGraph(attributePaths = {"requester", "assignee"})
@Override
Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);
```

em `TicketRepository`. É código de produção e mexe no caminho mais quente da API; com testes
verdes, é seguro. Ainda assim, **dívida consciente** frente ao README (ver §5).

**Aviso importante para quem aplicar: não confie na correção sem reconferir.** Não cheguei a
compilar essa alteração. `@EntityGraph` sobre a sobrescrita de
`JpaSpecificationExecutor.findAll(Specification, Pageable)` tem histórico de ser **silenciosamente
ignorado** em algumas versões, e a variante paginada ainda pode tropeçar na *count query*. Ou seja,
existe o risco real de a correção "entrar" e não mudar nada. Depois de aplicar, **conte as consultas
de novo** (mesmo método usado aqui: `hibernate.generate_statistics=true` +
`Statistics.getEntityFetchCount()`); o número esperado cai de ~1 por usuário distinto para **0**. Se
o `@EntityGraph` for ignorado, o plano B é um `@Query` explícito com `join fetch ticket.requester
left join fetch ticket.assignee` — mais verboso, mas sem ambiguidade.

#### B) `GET /indicators` agrega em Java — **gravidade baixa**

**Evidência:** `IndicatorRepository.findAllProjections()` carrega **todos** os chamados sem
paginação nem filtro; `IndicatorService:64-380` faz ~20 passagens de stream sobre a lista.

**Crédito onde é devido:** a consulta é **uma só**, com `join`/`left join` explícitos e projeção
para um record — sem N+1 e sem carregar entidades completas. Foi bem feito.

**A partir de quando deixa de ser aceitável:** cada projeção tem ~18 campos (UUIDs, enums, ~6
`LocalDateTime`, duas strings de nome) — na ordem de 300–400 bytes por linha em heap.

- **até ~10 mil chamados** (~4 MB, poucas dezenas de ms): confortável;
- **~50 mil** (~20 MB por requisição, e o SSE dispara recálculo a cada mudança de chamado):
  começa a doer, sobretudo por pressão de GC sob concorrência;
- **acima de ~100 mil**: insustentável — precisa virar `group by` no banco.

Com 31 chamados medidos, estamos **três ordens de grandeza** abaixo do limite. **Não tocar.**

#### C) Sem teto próprio de tamanho de página — **gravidade baixa**

**Medido:** `GET /api/v1/tickets?size=100000` como ADMIN → resposta `200` com
`pageable.pageSize = 2000`. O Spring Data Web limita ao seu `maxPageSize` padrão de **2000**; a
aplicação **não** define teto próprio. Ou seja: `size=100000` não é atendido, mas **2000 é**, e
2000 linhas combinadas com o N+1 de §4.3-A são ~2000 consultas numa requisição.

**Custo de correção:** uma linha em `application.properties`:
`spring.data.web.pageable.max-page-size=100`. Barato, mas com 31 chamados no banco não há como
explorar.

#### D) Índices — **em boa forma; uma lacuna**

`V1__create_initial_schema.sql:44-49` cria índice em `requester_id`, `assignee_id`, `status`,
`priority` e `category` — exatamente os filtros de `TicketSpecification:22-46`. `V2:37-41` e
`V3:37-40` cobrem eventos, refresh tokens e a fila de jobs (inclusive
`idx_ai_jobs_status_next_attempt_at`, que é o índice certo para `findDueJobs`). Índice HNSW para o
vetor via placeholder (`application.properties:12`). **Isso está acima da média.**

Duas ressalvas, ambas baixas:

1. **Não há índice em `tickets.created_at`**, que é a **ordenação padrão** da listagem
   (`TicketController:43`). Toda listagem sem filtro faz *sort* completo. Irrelevante em 31 linhas;
   é o primeiro índice a criar se a base crescer.
2. **A busca textual é `lower(campo) like '%termo%'`** (`TicketSpecification:48-55`). Curinga à
   esquerda **não usa índice btree** — é *sequential scan* com `lower()` por linha. O certo seria
   `pg_trgm` + índice GIN. Em 31 chamados, imperceptível. **Dívida consciente.**

#### E) Registro de conexões SSE — **verificado, sem achado**

`NotificationEmitterRegistry` usa `ConcurrentHashMap` de `Set` por usuário e **remove a chave
quando o conjunto esvazia** (`:29-33`) — o mapa não acumula entradas órfãs. `NotificationService`
remove a inscrição em `onCompletion`, `onTimeout` e `onError` (`:50-52`) **e** em falha de envio
(`:84,:91,:114,:121`). Há heartbeat (`NotificationHeartbeatScheduler`) para detectar conexão morta.
**Investiguei esperando vazamento e não encontrei.** Não há teto de conexões por usuário — um
cliente com bug poderia abrir muitas —, mas isso é dimensionamento, não vazamento.

### 4.4 Segurança

#### A) `JWT_SECRET` com default público — **gravidade alta (em produção); média aqui**

**Evidência:** `application.properties:21` e `docker-compose.yml:39` usam o mesmo default,
`trocar-por-valor-local-com-pelo-menos-32-caracteres`, **versionado no repositório**.

**Impacto concreto:** qualquer implantação que não sobrescreva `JWT_SECRET` assina com um segredo
público. Como o papel vem do claim `role` (`SecurityConfig:66`), qualquer pessoa com o repositório
forja um token ADMIN válido e obtém acesso total. O `.env.example:6` alerta ("a única variável que
você PRECISA trocar"), o que mitiga, mas o `docker-compose.yml` **usa o default silenciosamente**
se a variável não existir — ninguém é impedido de subir inseguro.

**Custo de correção:** baixo e de bom efeito narrativo — falhar na inicialização se o segredo for o
default e o perfil não for `dev`. Alternativa de custo zero: **documentar no README** que trocar
`JWT_SECRET` é obrigatório fora do local. Dado o prazo, a linha no README é suficiente e é o
caminho recomendado.

#### B) Sem limite de tentativas de login — **gravidade média, dívida consciente**

**Evidência:** `AuthService.login:38-49` compara a senha e retorna; não há contador, bloqueio,
atraso nem CAPTCHA. `SecurityConfig:49` deixa `/auth/login` como `permitAll`.

**Impacto:** força bruta sem restrição. Mitigado por BCrypt (custo por tentativa) e por não haver
cadastro público. **Custo de correção:** um bucket em memória por e-mail/IP — algumas dezenas de
linhas e testes. Não cabe no prazo. **Declarar no README.**

#### C) Refresh token sem rotação — **gravidade média, dívida consciente**

**Evidência:** `AuthService.refresh:51-59` chama `refreshTokenService.validate()`
(**`readOnly = true`**, `RefreshTokenService:45`) e devolve `createRegularResponse(user)`, que
emite um **novo** par. O refresh token apresentado **não é revogado**.

**Impacto:** um refresh token vale 7 dias (`application.properties:23`) e pode ser reapresentado
**indefinidamente** dentro da validade; se vazar, o atacante mantém sessão pelo período todo e não
há detecção de reuso. Cada refresh também **insere** uma nova linha em `refresh_tokens` sem
invalidar a anterior — a tabela cresce e o número de tokens válidos simultâneos é ilimitado.

**Crédito:** a revogação **existe e funciona** na troca de senha (`AuthService:71` →
`revokeActiveTokens`), e os tokens são guardados como **hash** BCrypt, não em claro
(`RefreshTokenService:38`). O desenho está certo; falta só rotacionar.

**Custo:** revogar o token apresentado dentro de `refresh` — poucas linhas, mas exige tornar
`validate` transacional de escrita e ajustar testes. **Não cabe hoje.**

#### D) CORS — **adequado**

`SecurityConfig:81-95`: origens vêm de configuração (sem `*`), `allowCredentials(true)` combinado
com lista explícita — correto (curinga com credenciais seria rejeitado pelo navegador). Métodos e
cabeçalhos enumerados. **Sem achado.**

#### E) Actuator — **verificado: não existe**

`build.gradle` **não** declara `spring-boot-starter-actuator`, e não há nenhuma propriedade
`management.*`. Não há superfície de Actuator para expor. Registro explicitamente porque foi
levantado como risco: **procurei e não está lá.**

#### F) Mensagens de erro — **sem vazamento**

`GlobalExceptionHandler:120-134` devolve `INTERNAL_ERROR` / "Ocorreu um erro interno." sem *stack
trace*, classe de exceção ou SQL. `AuthService:38-40` usa "Credenciais invalidas." tanto para
e-mail inexistente quanto para senha errada — **não** revela quais e-mails existem. Bem feito.

#### G) Política de senha — **mínima, aceitável**

`ChangePasswordRequestDto:11` exige 8–72 caracteres. Não há exigência de complexidade nem lista de
senhas comuns. O teto de 72 é correto (limite do BCrypt) e indica cuidado. Senhas provisórias são
geradas por `TemporaryPasswordGenerator` e forçam troca via `PasswordChangeRequiredFilter`. Para o
escopo, suficiente. Sem ação.

#### H) Endpoints públicos — **verificado, um por um**

`SecurityConfig:28-33` libera apenas Swagger (`/v3/api-docs/**`, `/swagger-ui/**`) e
`/api/v1/choices`; tudo mais cai em `anyRequest().authenticated()` (`:52`). Confirmei que
`ChoicesController` devolve **apenas rótulos de enum** (status, prioridade, categoria) — nenhum
dado de negócio. Expor Swagger é intencional e desejável aqui: o avaliador precisa dele.

### 4.5 Autorização, endpoint por endpoint — **sem achado**

Conferi todos os controllers:

| Endpoint | Proteção | Situação |
|---|---|---|
| `GET /tickets` | filtro por papel em `TicketService:306` | OK — SOLICITANTE só vê os próprios |
| `GET /tickets/{id}` | `assertCanAccessTicket` (`TicketService:78`) | OK |
| `PATCH /tickets/{id}/status`, `/assignee`, `DELETE /assignee` | `assertAdmin()` (`:106,:147,:188`) | OK |
| **`GET /tickets/{id}/similar`** | `@PreAuthorize("hasRole('ADMIN')")` (`TicketSimilarityController:30`) | **OK — restrito a ADMIN, como deveria** |
| `POST /tickets/{id}/ai-triage` | `@PreAuthorize` ADMIN (`TicketTriageController:32`) | OK |
| `PATCH /tickets/{id}/classification` | `@PreAuthorize` ADMIN (`TicketClassificationController:32`) | OK |
| `GET /indicators` | `@PreAuthorize` ADMIN na **classe** (`IndicatorController:11`) | OK |
| `GET/POST /ai/jobs/**` | `@PreAuthorize` ADMIN na classe (`AiJobController:26`) | OK |
| `GET/POST /tickets/{id}/comments` | `assertCanAccessTicket` (`TicketCommentService:59,72`) | OK |
| `GET /tickets/{id}/events` | `assertCanAccessTicket` (`TicketEventService:51`) | OK |
| `POST /users` | `assertAdmin()` (`UserService:70`) | OK |
| `GET /users/{id}` | `assertCanAccessUser` (`UserService:61`) | OK |
| `GET /users` | filtro por papel (`UserService:51`) | OK — ver §4.6 |
| `GET /notifications/stream` | autenticado; audiência filtrada por usuário/papel | OK |

**Não encontrei um único endpoint sem a guarda que deveria ter.** Sobre a pergunta específica de
`/similar` expor título de chamado de terceiros: está corretamente restrito a ADMIN, e o ADMIN já
enxerga todos os chamados — **não há vazamento**. Procurei vazamento equivalente em `UserMinDto`
(expõe só `id` e `name`, **não** e-mail) e em `TicketMinDto` — nada.

### 4.6 Uma suspeita investigada e **refutada**

Registro porque medir importa mais que suspeitar. Ao ler `UserService.findAll` notei que, ao
contrário de `findById` e `create`, ela **não** chama `assertAdmin()` nem `assertCanAccessUser()`.
A hipótese era exposição da lista completa de usuários a qualquer autenticado.

**Testei ao vivo** — login como `solicitante@fadex.org.br`, `GET /api/v1/users?size=3`:

```
HTTP 200 — totalElements: 1  (apenas o próprio usuário)
```

A guarda existe, só está em outro lugar: `resolveFilterByRole` (`UserService:105+`) devolve o
filtro intacto para ADMIN e o restringe ao próprio id caso contrário — o mesmo padrão de
`TicketService:306`. **Falso positivo. O código está correto.**

---

### 4.7 Conferência do `acompanhamento-desenvolvimento.md` contra o código

O documento de acompanhamento foi conferido linha a linha contra o código, e **não deve ser usado
como fonte de verdade**. Onde diverge:

| Linha | O que o documento diz | O que o código diz |
|---|---|---|
| 54 | "Histórico de mudanças de status — **Pendente**; eventos de status ainda não" | **Desatualizado.** `model/event/TicketEvent.java` existe, é gravado em toda transição (`TicketService:238`) e exposto em `GET /tickets/{id}/events`. Requisito **atendido**. |
| 47 | "CRUD completo — Parcial; implementar atualização de status, atribuição, exclusão/cancelamento" | **Parcialmente desatualizado.** Status e atribuição **foram** implementados (`TicketController:64-89`). **Exclusão/cancelamento continua sendo a lacuna real** — este ponto do documento segue válido e é confirmado por §1.2. |
| 53 | "Comentários — Parcial" | **Desatualizado.** Criação e listagem existem, com autorização e ordenação cronológica. Atendido. |
| 61–62, 96–100 | README pendente | **Confirmado.** Continua sendo o item aberto mais caro. |

Ou seja: o acompanhamento **subestima** o que já foi construído em histórico e comentários, e
**acerta** nos dois itens que de fato faltam (README e exclusão/cancelamento).

---

## 5. Lista priorizada

### 5.1 Corrigir antes da entrega

Ordenado por nota ganha por hora gasta.

| # | Ação | Por quê | Custo |
|---|---|---|---|
| **1** | **Escrever o `README.md`** | Requisito obrigatório §3.1 **ausente**, citado em §4 (entregáveis) e em 3 linhas do checklist. Precisa conter: descrição, tecnologias, passo a passo de instalação, **credenciais de teste** (`admin@fadex.org.br`/`admin123`, `solicitante@fadex.org.br`/`solicitante123`), exemplos `curl` ou coleção, e a **justificativa da abordagem de IA** (Ollama local + fallback heurístico — que §3.3 permite explicitamente). | 2–3 h. **Prioridade absoluta.** |
| **2** | Timeouts de SMTP (§4.1-B) | Impede que SMTP travado mate o SSE — o item de 20% da nota. | 3 linhas de properties, ~5 min |
| **3** | **`AI_WORKER_INTERVAL_MILLIS=2000`**, mantendo `AI_WORKER_BATCH_SIZE=1` (§4.1-A) | Sobe a vazão para 30 jobs/min — de sobra para a demonstração — **sem alongar a transação**, evitando por completo o problema de §4.2-C. Subir o lote para 5 é opcional e só vale se houver necessidade de carga; a mudança de intervalo sozinha entrega quase todo o benefício. | ~5 min |
| **4** | Corrigir o prompt da IA **ou** descrever a heurística com precisão no README (§4.0) | Hoje o LLM nunca é usado e ninguém sabe. Enumerar os valores dos enums no prompt + um `log.warn` no `catch` faz a integração passar a funcionar de verdade. Se o tempo apertar, a opção de custo zero é o README dizer a verdade sobre a heurística — §3.3 permite. | 20–30 min para corrigir; 5 min para documentar |
| **5** | `@Modifying(flushAutomatically=true, clearAutomatically=true)` (§4.2-B) | Desarma a mina e alinha o código ao que a frente de IA relatou. | 1 linha + rodar a suíte |
| **6** | **Decidir sobre excluir/cancelar** (§1.2 item 3) | Única funcionalidade **obrigatória** com lacuna real (25% da nota). Caminho mais barato: `DELETE /api/v1/tickets/{id}` restrito a ADMIN, com evento no histórico. Se não der tempo, **declarar no README** como limitação — é bem melhor que silêncio. | 1–2 h para implementar; 5 min para declarar |

Os itens 2, 3 e 5 somam **menos de 15 minutos** de configuração e não competem com o README. O
item 4 é o de maior retorno técnico depois do README — e tem uma saída de 5 minutos caso o tempo
acabe.

### 5.2 Dívida consciente — registrar no README e citar como "sei que existe"

Um candidato que enumera as próprias limitações demonstra critério; um que as esconde parece não
tê-las visto.

- **N+1 na listagem de chamados** (§4.3-A) — *medido*: 50 linhas/50 usuários = 52 consultas.
  Correção conhecida: `@EntityGraph` em `TicketRepository` (3 linhas). Não feito por ser o caminho
  mais quente da API e o tempo estar melhor aplicado no README. **Se sobrar meia hora depois do
  README, este é o primeiro da fila** — mas só vale aplicar **com a recontagem de consultas
  descrita em §4.3-A**, porque o `@EntityGraph` pode ser silenciosamente ignorado nessa assinatura.
  Aplicar sem medir é pior que não aplicar: dá a sensação de resolvido sem resolver.
- **Notificação sem outbox** (§4.2-A) — falha após o commit é logada, não reenviada.
- **Sem limite de tentativas de login** (§4.4-B).
- **Refresh token sem rotação** (§4.4-C) — reuso possível dentro dos 7 dias.
- **`JWT_SECRET` com default público** (§4.4-A) — documentar como obrigatório trocar.
- **Indicadores agregados em Java** (§4.3-B) — confortável até ~10 mil chamados; virar `group by`
  acima disso.
- **Busca textual com `like '%...%'` sem índice** (§4.3-D) — `pg_trgm` + GIN quando crescer.
- **Sem índice em `tickets.created_at`**, a ordenação padrão (§4.3-D).
- **Sem teto próprio de paginação**; o limite efetivo de 2000 é do Spring, não nosso (§4.3-C).
- **E-mail e SSE dividem executor** (§4.1-B) — os timeouts removem a falha catastrófica; separar os
  pools é o conserto correto e fica para depois.

### 5.3 Não tocar

- **Refatorar o `AiJobWorker`** para chamar o modelo fora da transação (§4.2-C) — refatoração real
  do componente mais delicado, a horas da entrega. O dimensionamento atual (1 conexão presa de 10)
  já contém o risco.
- **Separar os executores de e-mail e SSE** (§4.1-B) — os timeouts entregam a maior parte do
  benefício por uma fração do risco.
- **Redimensionar o pool assíncrono / política de rejeição** (§4.1-C) — inalcançável neste volume.
- **Teto de conexões SSE por usuário** (§4.3-E) — não há vazamento; é só dimensionamento.
- **Política de complexidade de senha** (§4.4-G) — 8–72 caracteres é adequado ao escopo.
- **Reescrever os indicadores como `group by`** (§4.3-B) — três ordens de grandeza de folga.
- **Deploy público** (§3.2) — vale 5% compartilhado com outros seis diferenciais, dos quais já
  entregamos seis.

---

## Nota final

O projeto está **substancialmente acima** do que o desafio pede em arquitetura, e **abaixo** no
item mais barato de todos: a documentação de entrega. SSE, pgvector, fila persistente de jobs,
matriz de transições e ~50 classes de teste são trabalho de bom nível. Nada disso é avaliado se o
avaliador não conseguir rodar o projeto — e o único artefato que ele vai abrir primeiro, o
`README.md`, não existe.

O segundo achado mais caro é de outra natureza: **a integração com o LLM não funciona e ninguém
sabia** (§4.0). Toda classificação cai na heurística, por um prompt que promete "os valores dos
enums fornecidos" sem fornecê-los, e a exceção resultante é engolida sem log. Isso não viola o
enunciado — §3.3 permite heurística —, mas define o que o README pode honestamente afirmar. É um
bom lembrete de que auditar código lendo não basta: as duas frentes envolvidas achavam que a
integração estava de pé, e bastou criar um chamado para descobrir que não.

As horas restantes deveriam ir, nesta ordem: **README**, os ajustes de configuração de 15 minutos,
a decisão sobre o prompt da IA, e a decisão sobre excluir/cancelar.
