# Diretrizes do Frontend

## Estrutura

O frontend usa TypeScript estrito, Next.js, React, Tailwind CSS e alias `@/*`.

Páginas do App Router ficam em `frontend/app`. Código compartilhado fica em `frontend/src`, incluindo `features`, `services`, `stores`, `schemas`, `types`, `routes` e `config`.

## Responsabilidade Única

Cada arquivo deve ter responsabilidade única. Evite arquivos grandes que misturam layout, regra de tela, chamadas de API, schema, tipos e componentes reutilizáveis.

Quando um arquivo começar a acumular responsabilidades, separe em componentes, hooks, services, schemas ou tipos conforme o papel do código.

## Componentização

Prefira reutilizar componentes existentes antes de criar novos.

Componentes compartilhados devem ser pequenos, previsíveis e focados em uma finalidade. Componentes específicos de uma feature devem ficar próximos da feature.

Use kebab-case para arquivos de rotas e componentes quando esse padrão já existir, como `login-form.tsx`, e camelCase para variáveis e funções.

## API e Dados

O frontend não deve duplicar regra de enum ou label. Choices devem vir do backend.

Contratos de endpoint devem seguir `docs/backend/api.md`. Quando o contrato parecer insuficiente, atualize a documentação ou alinhe o backend antes de inferir comportamento pelo código.

## Responsividade

As telas devem ser responsivas desde o início.

Em listagens, tabelas podem virar cards em telas menores quando isso melhorar leitura e uso. Evite layouts que dependam de scroll horizontal como experiência principal no mobile.

Garanta que textos, botões e ações principais continuem acessíveis em telas pequenas.

## Validação

Rode `make frontend-lint` e `make frontend-build` ao alterar UI, rotas, schemas ou cliente de API.
