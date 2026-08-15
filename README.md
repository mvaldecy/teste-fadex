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
- [Documentação](#documentação)

## Stack

| Camada | Tecnologia |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1, Spring Security (resource server JWT), Spring Data JPA, Quartz, Thymeleaf |
| Banco | PostgreSQL 17 com pgvector |
| Migrações | Flyway |
| Frontend | Next.js 15 (App Router), React 19, TypeScript estrito, Tailwind, Radix UI, Zustand |
| IA local | Ollama — `llama3.2:1b` para classificação, `all-minilm` para embeddings |
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

O assistente cuida do resto: confere os pré-requisitos, **testa cada porta e desloca sozinho as que
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

Durante a execução ele pergunta se quer baixar os modelos de IA (mais de 1 GB). **Pode recusar**: a
classificação continua funcionando por heurística de palavras-chave, e o sistema não perde nenhuma
funcionalidade além da detecção de duplicados, que depende de embeddings.

### Sem o assistente

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

Se alguma porta estiver ocupada, o assistente escolhe outra e informa no resumo final.

### Comandos do dia a dia

```bash
docker compose ps        # status
docker compose logs -f   # logs
docker compose down      # derruba, preservando os dados
docker compose down -v   # derruba e apaga o banco
```

## Credenciais

Criadas automaticamente pelo seed de desenvolvimento:

| Papel | E-mail | Senha |
| --- | --- | --- |
| ADMIN | `admin@fadex.org.br` | `admin123` |
| SOLICITANTE | `solicitante@fadex.org.br` | `solicitante123` |

O seed também cria outros administradores e solicitantes, além de **24 chamados** distribuídos entre
os quatro status e as três prioridades, com datas retroagidas. Isso existe para que os indicadores
tenham do que falar: sem histórico, tempo médio de fechamento e taxa de concordância com a IA
nasceriam vazios.

Quatro desses chamados ficam propositalmente **sem classificação**, para que dê para acompanhar a
triagem acontecendo: abra um deles como ADMIN e clique em "Solicitar triagem".

O seed é controlado por `APP_SEED_ENABLED` e pode ser desligado.

## O que o sistema faz

**Chamados** — abertura, listagem paginada com filtros por status, prioridade, categoria,
solicitante, responsável e busca textual, detalhe, mudança de status com matriz de transições
válidas, atribuição e recusa de responsável. Chamado fechado não reabre.

**Autenticação e autorização** — login com JWT e refresh token, senha com hash, troca obrigatória de
senha provisória. ADMIN enxerga e administra tudo; SOLICITANTE vê e comenta apenas os próprios
chamados. A interface esconde o que o papel não pode fazer, mas quem autoriza é o servidor.

**Comentários e histórico** — comentários em ordem cronológica e registro automático de cada
mudança: criação, status, responsável, classificação.

**Triagem por IA** — classificação automática de categoria e prioridade com justificativa e grau de
confiança, revisão pelo administrador, e detecção de chamados semelhantes por embeddings.

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
`llama3.2:1b` erra mais que um modelo grande, e é justamente por isso que a revisão humana faz parte
do fluxo em vez de ser opcional.

**Processamento assíncrono.** Criar chamado não espera o modelo. A criação enfileira um job, e um
worker Quartz processa em segundo plano com tentativas e recuo progressivo. O usuário nunca fica
preso esperando inferência, e modelo fora do ar não impede ninguém de abrir chamado.

**Degradação prevista, não acidental.** Se o modelo falhar ou não estiver instalado, um classificador
heurístico por palavras-chave assume, com justificativa dizendo que foi heurística. O sistema
continua classificando — pior, mas honesto sobre como chegou ali.

**A sugestão é auditada.** Categoria, prioridade e confiança sugeridas pela IA são gravadas em
colunas próprias, separadas da classificação vigente. Sem isso, a correção do administrador
sobrescreveria a sugestão e a taxa de concordância daria 100% para sempre — mediria nada. É essa
separação que permite dizer quanto o modelo acerta.

**Duplicados por similaridade semântica.** Cada chamado gera um embedding (`all-minilm`, 384
dimensões) guardado em pgvector. Pares acima de 0,90 de similaridade de cosseno viram vínculo
persistido, visível na aba "Semelhantes". O limiar é alto de propósito: falso positivo aqui vira
vínculo que alguém precisa desfazer à mão.

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

Exemplos:

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

A suíte do backend cobre serviços, persistência, camada web, motor de SSE, composição de e-mail,
worker de IA e cálculo de indicadores.

## Documentação

- [`docs/backend/api.md`](docs/backend/api.md) — contrato da API
- [`docs/backend/`](docs/backend/) — decisões de design e planos de implementação do backend
- [`docs/ia/`](docs/ia/) — revisão de classificação e indicadores
- [`docs/frontend/`](docs/frontend/) — decisões da interface
- [`docs/configuracao/`](docs/configuracao/) — ambiente e convenções de Git
- [`docs/projeto/`](docs/projeto/) — acompanhamento dos requisitos e divisão do trabalho

## Ambiente

Todas as variáveis estão documentadas em [`.env.example`](.env.example), com comentário explicando
cada bloco. As duas que importam ao rodar fora do assistente:

- `JWT_SECRET` — precisa de pelo menos 32 caracteres. Gere com `openssl rand -base64 48`.
- `AI_TRIAGE_ENABLED` — nasce ligada. Desligue apenas se não quiser rodar o Ollama.

Não versione `.env` real. O assistente de instalação gera o arquivo e nunca sobrescreve valor
existente sem perguntar.
