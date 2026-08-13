# Frontend Shell Inicial

## Contexto

O projeto Fadex Helpdesk e um monorepo com backend Spring Boot e frontend planejado em React/Next.js. O desafio tecnico valoriza uma interface web simples consumindo a API e exibindo indicadores em tempo real. Este ciclo cria a base navegavel do frontend sem implementar regras de negocio completas.

## Objetivo

Inicializar o frontend com Next.js, TypeScript e uma casca de aplicacao suficiente para evoluir em PRs pequenos:

- tela de login;
- layout autenticado;
- navegacao para chamados e indicadores;
- cliente HTTP centralizado;
- leitura de variaveis de ambiente;
- estrutura de pastas com `.gitkeep` nas pastas ainda sem codigo;
- preparacao para consumir choices do backend sem hardcode de labels de enums.

## Decisoes

- Usar Next.js com App Router, React e TypeScript.
- Manter o frontend dentro de `frontend/`, separado do backend.
- Usar CSS global simples neste ciclo, evitando biblioteca visual ate haver necessidade real.
- Usar fetch nativo encapsulado em um cliente HTTP proprio.
- Centralizar rotas, tipos e servicos por dominio quando houver codigo suficiente.
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
    lib/
      http/
      routes/
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

O cliente HTTP deve montar URLs a partir de `NEXT_PUBLIC_API_BASE_URL`. Erros devem ser normalizados em um formato simples para que telas futuras exibam mensagens consistentes.

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

## Testes E Verificacao

O ciclo deve permitir verificar:

- instalacao de dependencias do frontend;
- lint do frontend;
- build do frontend;
- testes existentes do backend sem regressao.

Se forem criados testes automatizados no frontend, eles devem cobrir pelo menos renderizacao das paginas principais ou utilitarios centrais de configuracao.

## Fora De Escopo

- Login real com JWT.
- Persistencia de sessao.
- CRUD de chamados.
- Consumo real de indicadores.
- SSE, WebSocket ou polling.
- Biblioteca de componentes.
- Deploy.

## Revisao Da Spec

- Nao ha placeholders pendentes.
- O escopo esta limitado a scaffold e shell navegavel.
- A arquitetura preserva o contrato de choices vindo do backend.
- As paginas iniciais nao dependem de endpoints ainda nao implementados.
