# Frontend Shell Inicial

## Contexto

O projeto Fadex Helpdesk e um monorepo com backend Spring Boot e frontend planejado em React/Next.js. O desafio tecnico valoriza uma interface web simples consumindo a API e exibindo indicadores em tempo real. Este ciclo cria a base navegavel do frontend sem implementar regras de negocio completas.

## Objetivo

Inicializar o frontend com Next.js, TypeScript e uma casca de aplicacao suficiente para evoluir em PRs pequenos:

- tela de login;
- layout autenticado;
- navegacao para chamados e indicadores;
- base Axios centralizada em `src/services/api.ts`;
- leitura de variaveis de ambiente;
- estrutura de pastas com `.gitkeep` nas pastas ainda sem codigo;
- preparacao para consumir choices do backend sem hardcode de labels de enums.

## Decisoes

- Usar Next.js 15.5.23 com App Router, React e TypeScript.
- Manter o frontend dentro de `frontend/`, separado do backend.
- Usar Tailwind CSS 3 para estilos utilitarios e responsivos desde o scaffold inicial.
- Manter `globals.css` apenas como entrada global das diretivas do Tailwind e estilos base realmente compartilhados.
- Usar Zod para validacao de formularios, variaveis de ambiente publicas e contratos de resposta consumidos da API.
- Usar Zustand para estado cliente leve, iniciando por sessao simulada e estado de UI que precise ser compartilhado entre componentes.
- Usar Axios para chamadas HTTP.
- Criar `src/services/api.ts` com a instancia base do Axios.
- Services de dominio devem ficar em `src/services/` e chamar a base Axios para acessar endpoints.
- Centralizar rotas, schemas, stores, tipos e services por responsabilidade.
- Pin de dependencias no frontend deve evitar `latest` para preservar build reproduzivel.
- Overrides de `postcss` e `sharp` devem manter `npm audit --omit=dev` sem vulnerabilidades conhecidas de producao.
- Usar `NEXT_PUBLIC_API_BASE_URL` para apontar para a API.
- Manter autenticacao inicialmente como estrutura de tela e contrato esperado, sem integrar JWT real ate o backend de auth existir.

## Estrutura Planejada

```text
frontend/
  app/
    (auth)/
      login/
    (dashboard)/
      chamados/
      indicadores/
    globals.css
    layout.tsx
    page.tsx
  postcss.config.mjs
  tailwind.config.ts
  src/
    components/
      layout/
      ui/
    config/
    features/
      auth/
      choices/
      tickets/
      indicators/
    routes/
    schemas/
    services/
    stores/
    types/
```

Pastas criadas para evolucao futura devem receber `.gitkeep` quando ainda nao tiverem arquivos de codigo.

## Fluxo Inicial

A rota raiz redireciona ou aponta para uma entrada clara da aplicacao. A tela de login exibe formulario de e-mail e senha e prepara o ponto de integracao com o backend. O dashboard autenticado entrega navegacao lateral ou superior para:

- chamados;
- indicadores;
- sair.

As paginas de chamados e indicadores podem iniciar com estados vazios e estrutura visual realista, sem dados mockados complexos. O objetivo e validar navegacao, layout e contratos iniciais.

## Integracao Com API

A instancia Axios em `src/services/api.ts` deve montar URLs a partir de `NEXT_PUBLIC_API_BASE_URL`. Services de dominio devem importar essa instancia e expor funcoes sem acoplar componentes a detalhes HTTP.

Schemas Zod devem validar os limites da aplicacao: entrada de formulario, configuracao publica e respostas externas. Tipos TypeScript derivados de schemas devem ser preferidos quando o dado vem de fora da aplicacao.

Choices de enums serao consumidos futuramente de:

```http
GET /api/v1/choices
```

O frontend nao deve definir labels proprias para status, prioridade, categoria, papeis ou origem de classificacao.

## Tratamento De Erros

Neste ciclo, os erros previstos sao:

- configuracao ausente de URL da API;
- falha de chamada HTTP futura;
- validacao basica de formulario de login.

As telas devem evitar travar a interface e deixar pontos claros para mensagens de erro quando a integracao real for adicionada.

O estado global via Zustand deve ficar restrito ao que precisa ser compartilhado entre telas ou componentes distantes. Estado local de formulario e componentes deve continuar local.

## Testes E Verificacao

O ciclo deve permitir verificar:

- instalacao de dependencias do frontend;
- lint do frontend;
- build do frontend;
- configuracao do Tailwind CSS aplicada nas paginas iniciais;
- validacao Zod aplicada pelo menos ao formulario de login ou configuracao publica;
- store Zustand criada sem acoplar regra de negocio ainda inexistente;
- testes existentes do backend sem regressao.

Neste ciclo nao havera testes automatizados no frontend por decisao do projeto. A verificacao do frontend sera feita com lint e build.

## Fora De Escopo

- Login real com JWT.
- Persistencia de sessao.
- Estado global completo de chamados, comentarios ou indicadores.
- CRUD de chamados.
- Consumo real de indicadores.
- SSE, WebSocket ou polling.
- Biblioteca de componentes.
- Deploy.

## Revisao Da Spec

- Nao ha placeholders pendentes.
- O escopo esta limitado a scaffold e shell navegavel.
- Tailwind CSS 3 foi incluido como decisao de estilo do frontend.
- Zod e Zustand foram incluidos com uso limitado aos pontos de extensao iniciais.
- Axios foi incluido como base HTTP em `src/services/api.ts`.
- Testes automatizados do frontend ficaram fora deste ciclo.
- Next.js foi pinado em 15.5.23 para estabilidade de build local.
- A arquitetura preserva o contrato de choices vindo do backend.
- As paginas iniciais nao dependem de endpoints ainda nao implementados.
