# Fadex Helpdesk

Central de chamados internos com triagem automática por IA local, indicadores em tempo real e
notificações por e-mail. Projeto do desafio técnico de Analista de Desenvolvimento da Fadex.

O solicitante abre o chamado escrevendo apenas título e descrição. A partir daí o sistema faz o
trabalho: um worker classifica categoria e prioridade por IA, registra a confiança e a
justificativa, procura chamados semelhantes por similaridade de texto e avisa os administradores.
O administrador revisa a sugestão, aceita ou corrige — e é dessa correção que sai a métrica de
concordância entre humano e modelo.

## Sumário

- [Stack](#stack)
- [Como rodar](#como-rodar)
- [Credenciais](#credenciais)
- [O que o sistema faz](#o-que-o-sistema-faz)
- [Abordagem de IA](#abordagem-de-ia)
- [Arquitetura](#arquitetura)
- [API](#api)
- [Testes](#testes)
- [Limitações conhecidas](#limitações-conhecidas)
- [Documentação](#documentação)
- [Como este projeto foi construído](#como-este-projeto-foi-construído)

## Stack

| Camada | Tecnologia |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1, Spring Security (resource server JWT), Spring Data JPA, Quartz, Thymeleaf |
| Banco | PostgreSQL 17 com pgvector |
| Migrações | Flyway |
| Frontend | Next.js 15 (App Router), React 19, TypeScript estrito, Tailwind, Radix UI, Zustand |
| IA local | Ollama — `llama3.2:3b` para classificação, `all-minilm` para embeddings |
| E-mail | SMTP, com Mailpit no ambiente local |
| Tempo real | Server-Sent Events |
| Infra | Docker Compose |

## Como rodar

**Pré-requisito único: Docker com Docker Compose.** Backend e frontend são compilados dentro das
imagens — não é preciso ter Java nem Node na máquina.

```bash
git clone <url-do-repositorio>
cd teste-fadex
./setup.sh
```

O wizard cuida do resto: confere os pré-requisitos, **testa cada porta e desloca sozinho as que
estiverem ocupadas**, gera o `JWT_SECRET`, deriva a URL da API e a lista de CORS a partir das portas
escolhidas, e sobe a stack. No fim ele imprime os endereços e as credenciais. No caso normal é só ir
apertando Enter.

No Windows, rode pelo **Git Bash** ou **WSL** — ou clique duas vezes em `setup.cmd`, que localiza o
bash do Git for Windows.

Opções úteis:

```bash
./setup.sh --dry-run   # percorre o fluxo inteiro sem tocar no Docker; útil para conferir portas
./setup.sh --yes       # aceita todos os padrões, sem perguntas
make setup             # atalho para ./setup.sh, para quem tem make
```

Durante a execução ele pergunta se quer baixar os modelos de IA (cerca de 2,1 GB). **Pode recusar**: a
classificação continua funcionando por heurística de palavras-chave, e o sistema não perde nenhuma
funcionalidade além da detecção de duplicados, que depende de embeddings.

### Sem o wizard

```bash
cp .env.example .env     # e troque o JWT_SECRET
docker compose up -d --build
docker compose run --rm ollama-models   # opcional: baixa os modelos de IA
```

### Endereços

Com as portas padrão:

| Serviço | Endereço |
| --- | --- |
| Aplicação | http://localhost:3000 |
| API | http://localhost:8080/api/v1 |
| Swagger | http://localhost:8080/swagger-ui.html |
| Mailpit (e-mails enviados) | http://localhost:8025 |

Se alguma porta estiver ocupada, o wizard escolhe outra e informa no resumo final.

### Comandos do dia a dia

```bash
docker compose ps        # status
docker compose logs -f   # logs
docker compose down      # derruba, preservando os dados
docker compose down -v   # derruba e apaga o banco
```

## Credenciais

Criadas automaticamente pelo seed de desenvolvimento:

| Papel | Nome | E-mail | Senha |
| --- | --- | --- | --- |
| **ADMIN** | Administrador | `admin@fadex.org.br` | `admin123` |
| ADMIN | Carla Menezes | `carla.menezes@fadex.org.br` | `admin123` |
| ADMIN | Marcos Valdecy | `marcos.valdecy@fadex.org.br` | `dev123` |
| **SOLICITANTE** | Solicitante | `solicitante@fadex.org.br` | `solicitante123` |
| SOLICITANTE | Ana Ribeiro | `ana.ribeiro@fadex.org.br` | `solicitante123` |
| SOLICITANTE | Bruno Carvalho | `bruno.carvalho@fadex.org.br` | `solicitante123` |

As duas primeiras em negrito bastam para avaliar. As outras existem porque algumas coisas só
aparecem com mais de uma pessoa: **atribuir um chamado a outro administrador**, ver que só o próprio
responsável consegue recusar a atribuição, e conferir que o solicitante enxerga apenas os chamados
que ele mesmo abriu.

Na tela de login há **atalhos que preenchem essas credenciais** — as seis contas, agrupadas por
papel, com uma linha dizendo o que cada uma enxerga. Um clique preenche o formulário. Os atalhos
aparecem só quando o seed está ligado, porque sem ele as contas não existem.

O seed também cria **26 chamados** distribuídos entre os quatro status e as três prioridades, com
datas retroagidas. Isso existe para que os indicadores
tenham do que falar: sem histórico, tempo médio de fechamento e taxa de concordância com a IA
nasceriam vazios.

Quatro desses chamados ficam propositalmente **sem classificação**, para que dê para acompanhar a
triagem acontecendo: abra um deles como ADMIN e clique em "Solicitar triagem".

O seed é controlado por `APP_SEED_ENABLED` e pode ser desligado.

## O que o sistema faz

**Chamados** — abertura, listagem paginada com filtros reativos por status, prioridade, categoria,
atribuição e busca textual, detalhe, mudança de status com matriz de transições válidas, atribuição
e recusa de responsável, e **cancelamento**.

Duas regras que valem citar: **chamado fechado não reabre**, e o cancelamento sai por status em vez
de remoção — apagar a linha levaria junto o histórico, os comentários e a contagem que alimenta os
indicadores. O ADMIN cancela chamado aberto ou em andamento; o solicitante só o próprio, e só
enquanto está aberto.

**Autenticação e autorização** — login com JWT e refresh token, senha com hash, troca obrigatória de
senha provisória. ADMIN enxerga e administra tudo; SOLICITANTE vê e comenta apenas os próprios
chamados. A interface esconde o que o papel não pode fazer, mas quem autoriza é o servidor.

**Comentários e histórico** — comentários em ordem cronológica e registro automático de cada
mudança: criação, status, responsável, classificação.

**Triagem por IA** — classificação automática de categoria e prioridade com justificativa e grau de
confiança, revisão pelo administrador, e detecção de chamados semelhantes por embeddings. A tela
avisa quando a classificação vigente é sugestão da IA que ninguém confirmou ainda, e a aba de
semelhantes mostra também o **ranking dos mais próximos sem corte de limiar** — sem ele, "não há
duplicata" e "o modelo não achou" seriam indistinguíveis.

**Indicadores** — contagens por status, prioridade e categoria; tempo de fechamento, de primeira
resposta e de atribuição com média, mediana e p90; envelhecimento da fila; percentual dentro do SLA;
concordância entre administrador e IA; carga por responsável.

**Tempo real** — o painel e as listas se atualizam sozinhos por SSE, e chamados de prioridade ALTA
disparam alerta imediato para os administradores.

**Notificações por e-mail** — atribuição, mudança de status, comentário da contraparte, resolução e
senha provisória. Quem causou a ação nunca recebe e-mail da própria ação.

## Abordagem de IA

**Modelo local, não serviço pago.** A triagem roda em Ollama dentro do próprio Compose. Não há chave
de API para configurar, custo por requisição, nem dado de chamado saindo da máquina — o que importa
num sistema que trata assunto interno de uma organização. O custo dessa escolha é o modelo pequeno:
`llama3.2:3b` erra mais que um modelo grande, e é justamente por isso que a revisão humana faz parte
do fluxo em vez de ser opcional. O `3b` foi escolhido por medição, não por tamanho: contra os
chamados semeados ele acerta 7 de 10 categorias, contra 3 de 10 do `1b`. A classificação leva cerca
de 12 s por chamado numa máquina de 12 vCPU — em máquina menor demora mais, e o timeout de 60 s
existe para não desistir cedo demais e cair na heurística sem necessidade.

**Processamento assíncrono.** Criar chamado não espera o modelo. A criação enfileira um job, e um
worker Quartz processa em segundo plano com tentativas e recuo progressivo. O usuário nunca fica
preso esperando inferência, e modelo fora do ar não impede ninguém de abrir chamado.

**Degradação prevista, não acidental.** Se o modelo falhar ou não estiver instalado, um classificador
heurístico por palavras-chave assume, com justificativa dizendo que foi heurística. O sistema
continua classificando — pior, mas honesto sobre como chegou ali.

A heurística junta título e descrição, tira acentos e minúsculas, e procura palavras-chave numa
ordem fixa — a primeira que casar decide a categoria: `ACESSO` (senha, login, acesso, bloqueado),
`SISTEMAS` (sistema, erro, aplicação, interno), `INFRAESTRUTURA` (rede, internet, servidor, infra),
`EQUIPAMENTOS` (computador, impressora, teclado, mouse), `FINANCEIRO` (pagamento, nota fiscal,
boleto), `RH` (férias, folha, benefício) e, se nada casar, `OUTROS`. A prioridade sai da mesma
varredura: `ALTA` com urgente, indisponível, parado, bloqueado ou "não consegue acessar"; `BAIXA`
com dúvida, orientação ou "quando possível"; `MEDIA` no resto.

A ordem é o que mais pesa e o que menos aparece: *"erro no sistema da folha de pagamento"* casa
`SISTEMAS` no segundo teste e nunca chega em `RH`. É a limitação inerente da abordagem — ela não lê
a frase, só procura substrings —, e é por isso que ela é o plano B e não o principal.

**A sugestão é auditada.** Categoria, prioridade e confiança sugeridas pela IA são gravadas em
colunas próprias, separadas da classificação vigente. Sem isso, a correção do administrador
sobrescreveria a sugestão e a taxa de concordância daria 100% para sempre — mediria nada. É essa
separação que permite dizer quanto o modelo acerta.

**Duplicados por similaridade semântica.** Cada chamado gera um embedding (`all-minilm`, 384
dimensões) guardado em pgvector. Pares acima de **0,75** de similaridade de cosseno viram vínculo
persistido, visível na aba "Semelhantes" e sinalizado por um selo na listagem. O pipeline funciona
ponta a ponta: o vínculo que existe na base foi criado pela detecção automática, não inserido à mão.

**A parte honesta: o modelo não é bom o suficiente para o caso real.** Medindo todos os 378 pares
possíveis dos 28 chamados da base, um chamado escrito de propósito como duplicata de outro —
"Problema no sistema de arquivos" contra "Servidor de arquivos fora do ar" — pontua **0,5022**,
enquanto pares sem relação nenhuma chegam a **0,6661**. O modelo coloca a duplicata verdadeira
**abaixo** do ruído. Baixar o limiar não resolve: para alcançar 0,50 seria preciso passar por todos
os falsos positivos antes.

Testamos trocar por um modelo multilíngue (`paraphrase-multilingual`, mesmas 384 dimensões, troca
sem migração). O piso de ruído melhora muito — a mediana geral cai de 0,41 para 0,22 —, mas o topo
do ranking piora: a duplicata verdadeira que era a primeira colocada cai para segunda, atrás de um
par falso. **Não trocamos**, porque a medição não sustentou a troca.

O que funciona hoje é o caso de reescrita próxima: duplicata escrita com outras palavras mas com o
mesmo vocabulário pontua 0,85 e é detectada. O que não funciona é o caso mais comum de helpdesk —
duas pessoas descrevendo o mesmo problema com vocabulários diferentes. A correção de verdade é um
modelo de embedding treinado ou ajustado para português de domínio, não um ajuste de limiar.

## Arquitetura

Monorepo com backend e frontend separados.

```
backend/src/main/java/br/org/fadex/helpdesk
├── ai/            triagem, embeddings, jobs, indicadores, duplicados
├── config/        Quartz, auditoria, OpenAPI, seed
├── controller/    endpoints REST
├── exception/     erros de domínio e tratamento HTTP
├── mail/          envio de e-mail
├── model/         entidades, DTOs e enums
├── notification/  eventos de domínio que alimentam SSE e e-mail
├── repository/    persistência e especificações de filtro
├── security/      JWT, papéis e controle de acesso
├── service/       regras de negócio
└── sse/           motor de notificações em tempo real

frontend
├── app/           rotas do App Router
└── src/
    ├── features/  telas por domínio
    ├── services/  cliente HTTP e stream SSE
    ├── stores/    sessão
    └── schemas/   validação com Zod
```

Duas decisões que explicam boa parte do desenho:

**Um gatilho, dois transportes.** Mudança relevante publica um evento de domínio; ouvintes separados
derivam a notificação em tempo real e o e-mail. Ambos rodam depois do commit e de forma assíncrona,
então falha de SMTP não desfaz operação de negócio nem trava a requisição.

**A regra mora num lugar só.** A matriz de transições de status é uma estrutura consultável usada
pelo serviço para recusar mudança inválida e publicada em `/api/v1/ticket-status-transitions` para o
cliente habilitar apenas o que o servidor aceita. A interface não duplica a regra.

## API

Contrato completo em [`docs/backend/api.md`](docs/backend/api.md). Swagger em
`http://localhost:8080/swagger-ui.html`.

**Coleção do Postman:** [`docs/fadex-helpdesk.postman_collection.json`](docs/fadex-helpdesk.postman_collection.json)
— 26 requisições em 13 pastas. Importe e comece por **Autenticação → login**: ele guarda o token na
variável da coleção, e as demais requisições já saem autenticadas. Ajuste `baseUrl` se escolheu
outra porta no wizard.

Ela é **gerada a partir do OpenAPI publicado pela própria aplicação**, com
[`scripts/gerar-colecao-postman.py`](scripts/gerar-colecao-postman.py) — uma coleção escrita à mão
envelhece em silêncio, e quem descobre é quem manda uma requisição que não existe mais. Para
regerar depois de mudar a API:

```bash
curl -s http://localhost:8080/v3/api-docs > /tmp/openapi.json
python3 scripts/gerar-colecao-postman.py /tmp/openapi.json docs/fadex-helpdesk.postman_collection.json
```

Exemplos com `curl`:

```bash
# Login
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@fadex.org.br","password":"admin123"}'

# Guarde o token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@fadex.org.br","password":"admin123"}' | jq -r .accessToken)

# Listar chamados com filtro
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/v1/tickets?status=ABERTO&priority=ALTA&page=0&size=10'

# Abrir chamado
curl -s -X POST http://localhost:8080/api/v1/tickets \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Nao consigo acessar a VPN","description":"Erro de credencial desde ontem."}'

# Indicadores
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/indicators

# Stream de notificações (SSE)
curl -N -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/notifications/stream
```

Mudança de status, atribuição e revisão de classificação seguem o mesmo padrão — veja o contrato.

## Testes

```bash
make backend-test    # ou: cd backend && ./gradlew test
make frontend-lint
make frontend-build
```

São **350 testes** cobrindo serviços, persistência, camada web, motor de SSE, composição de e-mail,
worker de IA, cálculo de indicadores e limite de tentativas de login.

## Limitações conhecidas

Levantadas por auditoria interna do próprio projeto, com evidência em código. Estão aqui porque
saber onde o sistema cede vale mais que fingir que não cede.

**Vazão da triagem.** O worker processa um job a cada 10 segundos e cada chamado gera dois
(classificação e embedding). Cinquenta chamados criados de uma vez levam cerca de dezesseis
minutos para drenar. A fila é persistente e nada se perde — é questão de vazão, ajustável por
`AI_WORKER_INTERVAL_MILLIS` e `AI_WORKER_BATCH_SIZE`.

**Qualidade da classificação.** Medida contra os chamados semeados, com categoria e prioridade
curadas como gabarito: a heurística acerta 5 de 10 categorias, o `llama3.2:3b` acerta 5 de 10 e o
mesmo modelo com schema restrito acerta 7 de 10. **Nenhuma das abordagens classifica prioridade
de forma confiável** — a heurística responde `MEDIA` para quase tudo e os modelos pequenos têm
viés oposto. É exatamente por isso que a revisão pelo ADMIN faz parte do fluxo, e não é opcional.

**Notificação sem outbox.** E-mail e SSE são despachados após o commit, de forma assíncrona. Se o
envio falhar, a operação de negócio permanece e a notificação se perde — registrada em log, não
reenviada. A alternativa correta é uma tabela de outbox com reprocessamento.

**Consultas.** Os indicadores são agregados em memória, o que é confortável na casa dos milhares de
chamados e deve virar agregação no banco acima disso. A busca textual usa `like` sem índice. O N+1
que existia na listagem foi corrigido com `EntityGraph` — medido no log do Postgres: duas consultas
por página, nenhuma na tabela de usuários.

**Autenticação.** O limite de tentativas de login guarda o contador **em memória**: com mais de uma
instância cada uma conta a sua, o que multiplica o limite efetivo pelo número de instâncias. Em uma
instância, que é como o sistema roda hoje, a proteção vale integralmente; a versão correta usaria
armazenamento compartilhado. O refresh token não é rotacionado dentro da validade. O `JWT_SECRET`
tem valor padrão apenas para desenvolvimento e **precisa ser trocado** em qualquer uso real — o
wizard de instalação gera um automaticamente.

**Infraestrutura.** O despacho de e-mail e o de SSE compartilham o mesmo executor; há timeouts de
SMTP configurados para que um servidor lento não trave as notificações em tempo real, mas separar
os pools é o conserto correto. Datas usam `LocalDateTime` com o fuso fixado por variável de
ambiente; o certo seria `Instant` com formatação no cliente.

## Documentação

Três documentos cobrem o sistema. Comece por eles:

| Documento | O que responde |
| --- | --- |
| [`docs/backend/api.md`](docs/backend/api.md) | **Contrato da API**: caminhos, corpos, códigos de resposta, filtros, eventos do stream |
| [`docs/backend/arquitetura.md`](docs/backend/arquitetura.md) | Como o backend está organizado, o ciclo de vida do chamado, a triagem por IA, duplicados, SSE, e-mail e segurança |
| [`docs/frontend/arquitetura.md`](docs/frontend/arquitetura.md) | Estrutura da interface, sessão, guardas de rota, tempo real, filtros e configuração pública |

Complementares:

- [`docs/infraestrutura/2026-08-15-deploy.md`](docs/infraestrutura/2026-08-15-deploy.md) — o que é
  preciso para colocar em produção, e o que não foi validado por falta de plataforma.
- [`docs/projeto/2026-08-15-revisao-tecnica.md`](docs/projeto/2026-08-15-revisao-tecnica.md) —
  auditoria interna, com achados de desempenho, transação e segurança apontando arquivo e linha.
- [`docs/configuracao/env.md`](docs/configuracao/env.md) — variáveis de ambiente em detalhe.

O restante de `docs/` é **registro do desenvolvimento** — os documentos de design e os planos de
implementação escritos antes de cada frente de trabalho. Ficaram no repositório porque mostram as
decisões sendo tomadas e descartadas, mas não são a documentação do sistema: para saber como ele
funciona hoje, os três de cima bastam.

## Como este projeto foi construído

Desenvolvido com **desenvolvimento assistido por agentes** — Claude e Codex —, com o trabalho
dividido em frentes paralelas (backend, frontend, IA, infraestrutura) que rodaram em *worktrees* Git
separadas e foram integradas por revisão.

Isso muda o que vale a pena olhar aqui. O volume de código não é evidência de esforço; o que
distingue o trabalho é onde ele foi **medido em vez de suposto**. Alguns exemplos que estão
documentados com os números:

- A qualidade da classificação foi medida contra um gabarito, e o resultado — **nenhuma abordagem
  classifica prioridade de forma confiável** — está no README em vez de escondido.
- O limiar de similaridade foi medido sobre os pares reais da base, não arbitrado, e o comentário no
  código carrega a medição para que o próximo ajuste também seja medido.
- O fim do N+1 na listagem foi verificado contando as consultas no log do Postgres.
- A correção das variáveis públicas do frontend foi verificada construindo a imagem com um valor
  falso e procurando por ele no bundle.

As decisões que não deram certo também estão registradas — inclusive um limiar que subiu por
argumento e teve de descer por medição.

## Ambiente

Todas as variáveis estão documentadas em [`.env.example`](.env.example), com comentário explicando
cada bloco. As duas que importam ao rodar fora do wizard:

- `JWT_SECRET` — precisa de pelo menos 32 caracteres. Gere com `openssl rand -base64 48`.
- `AI_TRIAGE_ENABLED` — nasce ligada. Desligue apenas se não quiser rodar o Ollama.

Não versione `.env` real. O wizard de instalação gera o arquivo e nunca sobrescreve valor
existente sem perguntar.
