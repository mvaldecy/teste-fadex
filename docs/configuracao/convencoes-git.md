# Convencoes de Git e Pull Requests

## Idioma

Mensagens de commit, titulos de PR e descricoes de PR devem ser escritos em portugues.

## Branches

- `dev`: desenvolvimento ativo.
- `hmg`: homologacao e estabilizacao.
- `prod`: entrega final.

Branches de trabalho devem partir de `dev` e usar nomes curtos e descritivos.

Como o repositorio e um monorepo, o nome da branch deve explicitar o escopo entre parenteses:

```text
feature(backend)/auth-jwt
feature(backend)/chamados-crud
feature(frontend)/shell-admin
feature(infra)/postgres-compose
feature(docs)/readme-execucao-local
feature(fullstack)/indicadores-tempo-real
fix(backend)/validacao-email-unico
docs(configuracao)/convencoes-git
```

Escopos sugeridos:

- `backend`: mudancas restritas ao Spring Boot.
- `frontend`: mudancas restritas ao Next.js.
- `infra`: Docker, Compose, scripts e configuracoes de ambiente.
- `docs`: documentacao geral.
- `configuracao`: convencoes e estrutura do projeto.
- `ia`: servico local de IA, embeddings e classificacao.
- `fullstack`: mudancas coordenadas entre backend e frontend.

## Commits

Usar mensagens objetivas, em portugues, preferencialmente com um tipo no inicio:

```text
chore: inicializa estrutura do monorepo
docs: documenta arquitetura inicial
feat: adiciona autenticacao com jwt
fix: corrige validacao de transicao de status
test: cobre criacao de chamado
refactor: reorganiza servico de chamados
```

Evitar mensagens genericas:

```text
ajustes
final
update
mudancas
```

## Pull Requests

Cada PR deve ter escopo pequeno e verificavel. A descricao deve conter:

```text
## Objetivo

Explique o que este PR entrega.

## Mudancas

- Liste as principais mudancas.

## Como testar

- Informe comandos ou passos de validacao.

## Observacoes

- Registre decisoes, limitacoes ou proximos passos.
```

O merge padrao de PRs deve ser `squash merge`. A mensagem final do squash deve continuar em portugues e representar a entrega do PR.

Push direto para `dev`, `hmg` e `prod` deve ser evitado. Essas branches devem receber mudancas por PR para preservar descricao, commits e arquivos modificados na timeline automatizada.

## PR Stacks

PR stacks podem ser usados quando uma entrega depende de outra. Nesse caso, cada PR deve informar claramente sua base e dependencia, por exemplo:

```text
Este PR depende de `feature(backend)/auth-jwt`.
```

O objetivo e manter revisoes pequenas sem bloquear a evolucao natural do projeto.

## Timeline e Changelogs

Cada PR mergeado em `dev`, `hmg` ou `prod` deve atualizar automaticamente:

- `TIMELINE.md`
- `backend/CHANGELOG.md`, quando houver mudancas em `backend/`
- `frontend/CHANGELOG.md`, quando houver mudancas em `frontend/`
- `infra/CHANGELOG.md`, quando houver mudancas em `infra/`

A pasta `docs/` nao tera changelog proprio. Mudancas de documentacao entram apenas na timeline global.

A automacao deve usar um workflow do GitHub Actions acionado por PR fechado e mergeado, com script versionado no repositorio. A entrada deve registrar data, branch de destino, numero do PR, descricao do PR, commits e arquivos modificados.
