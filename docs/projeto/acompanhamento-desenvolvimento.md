# Acompanhamento do Desenvolvimento

Documento de controle dos requisitos do desafio técnico da Fadex para a aplicação Fadex Helpdesk.

Prazo de submissão: 15/08/2026 às 12h.

## Resumo do Desafio

Construir uma central de chamados internos com API REST, autenticação, autorização por papel, CRUD de chamados, comentários/histórico, triagem automática por IA e indicadores em tempo real.

O projeto deve demonstrar organização de código, modelagem relacional, documentação, boas práticas de Git e uma solução funcional acima de um CRUD tradicional.

## Critérios de Avaliação

| Critério | Peso | Direcionamento do projeto |
| --- | ---: | --- |
| Funcionalidade | 25% | Priorizar requisitos obrigatórios funcionando de ponta a ponta. |
| Qualidade do código | 20% | Manter camadas claras, arquivos pequenos, nomenclatura consistente e responsabilidades bem separadas. |
| Triagem por IA e tempo real | 20% | Entregar classificação automática justificada e indicadores atualizados automaticamente. |
| Modelagem de dados | 15% | Garantir entidades, relacionamentos, constraints e regras de negócio coerentes. |
| Boas práticas de Git | 10% | Trabalhar com branches, PRs, commits em português e histórico significativo. |
| Documentação | 5% | README, contrato da API e instruções de execução local claras. |
| Diferenciais | 5% | Frontend, Docker, testes, Swagger, deploy e/ou detecção de duplicados. |

## Status Geral

| Área | Status | Observações |
| --- | --- | --- |
| Configuração do monorepo | Concluído | Estrutura base, Makefile, environments, Docker do banco e convenções iniciais. |
| Backend Spring Boot | Em andamento | Base da API, autenticação, usuários, chamados e comentários iniciados. |
| Frontend Next.js | Em andamento | Shell inicial em desenvolvimento por fluxo separado. |
| Banco relacional | Parcial | PostgreSQL com Docker e migrações iniciais. Revisar constraints finais conforme regras evoluírem. |
| IA | Pendente | Estrutura prevista; implementação ainda não iniciada. |
| Tempo real | Pendente | Decidir SSE, WebSocket ou polling curto. |
| Documentação final | Parcial | `docs/backend/api.md` iniciado. README final ainda precisa consolidar execução e testes. |
| Entrega pública | Pendente | Repositório deve ser tornado público antes da submissão. |

## Requisitos Obrigatórios

| Requisito | Status | Evidência atual | Próximo passo |
| --- | --- | --- | --- |
| Cadastro de usuários | Parcial | `POST /api/v1/users` existe protegido. | Definir se haverá cadastro público ou fluxo apenas administrativo. |
| Hash de senha | Concluído | `UserService` usa `PasswordEncoder`. | Manter cobertura de teste. |
| Login com token | Concluído | `POST /api/v1/auth/login` retorna JWT. | Validar fluxo no frontend. |
| Rotas protegidas | Parcial | Security/JWT configurados. | Revisar todas as rotas e permissões por papel. |
| Autorização ADMIN x SOLICITANTE | Pendente | Papéis existem no modelo e token. | Implementar regra: ADMIN lista todos; SOLICITANTE vê/gerencia apenas próprios chamados. |
| CRUD completo de chamados | Parcial | Criar, listar com filtros e detalhar existem. | Implementar atualização de status, atribuição, exclusão/cancelamento e regras de transição. |
| Filtros de chamados | Parcial | Filtros por status, prioridade, categoria, solicitante, responsável e busca existem. | Validar autorização aplicada nos filtros. |
| Triagem automática por IA | Pendente | Campo `classificationOrigin` preparado como `PENDENTE`. | Criar service de classificação, mesmo que inicialmente mock/heurística justificada. |
| ADMIN aceitar/corrigir sugestão da IA | Pendente | Não implementado. | Criar endpoint/fluxo de revisão da classificação. |
| Indicadores em tempo real | Pendente | Não implementado. | Criar agregações por status/prioridade e atualização via SSE, WebSocket ou polling curto. |
| Alerta para prioridade ALTA | Pendente | Não implementado. | Emitir evento/estado quando chamado ALTA for aberto. |
| Comentários em chamado | Parcial | Endpoints de criação e listagem de comentários existem na branch atual. | Garantir histórico cronológico e integrar com mudanças de status. |
| Histórico de mudanças de status | Pendente | Entidade de comentário existe, mas eventos de status ainda não. | Definir modelo de evento/histórico e registrar transições. |
| Campos obrigatórios | Parcial | DTOs usam validações iniciais. | Revisar todos os fluxos novos. |
| E-mail único | Concluído | `UserService` valida conflito de e-mail. | Garantir constraint no banco. |
| Não reabrir chamado fechado | Pendente | Não implementado. | Implementar regra no service de chamados e cobrir com teste. |
| Tratamento de erros HTTP | Parcial | `GlobalExceptionHandler` e estrutura padrão existem. | Confirmar 400, 401, 403, 404 e 500 nos fluxos finais. |
| Banco relacional | Concluído | PostgreSQL configurado com Docker. | Revisar migrações finais. |
| Organização em camadas | Concluído | Backend segue controller/service/repository/model. | Manter padrão nos próximos fluxos. |
| README completo | Pendente | Ainda precisa ser consolidado para entrega. | Incluir descrição, stack, execução local, seeds e exemplos de API. |
| Usuários de teste no README | Pendente | Seed dev existe para ADMIN e SOLICITANTE. | Documentar credenciais no README. |
| Exemplos de requisições | Pendente | Contrato em `docs/backend/api.md` existe. | Adicionar curls ou coleção Postman/Insomnia. |

## Diferenciais

| Diferencial | Status | Observações |
| --- | --- | --- |
| Interface web consumindo API | Em andamento | Frontend Next.js iniciado em fluxo separado. |
| Painel em tempo real no frontend | Pendente | Depende dos indicadores do backend. |
| Detecção de duplicados/similares | Pendente | Pode usar embeddings/modelo local se houver tempo. |
| Docker/docker-compose | Parcial | Banco dockerizado; avaliar app/serviços auxiliares. |
| Testes automatizados | Parcial | Testes de service iniciados no backend. |
| Swagger/OpenAPI | Concluído | Swagger disponível em `/swagger-ui.html`. |
| Deploy funcional | Pendente | Avaliar tempo e prioridade depois do fluxo principal. |

## Roadmap de Desenvolvimento

| Etapa | Objetivo | Status |
| --- | --- | --- |
| 1. Configuração base | Monorepo, branches, ambientes, Docker do banco, Swagger, convenções e documentação inicial. | Concluído |
| 2. Autenticação e usuários | Login JWT, usuários, roles, seeds e autorização base. | Parcial |
| 3. Chamados | Criação, listagem, detalhe, filtros, atualização, cancelamento e regras de status. | Parcial |
| 4. Comentários e histórico | Comentários em chamados e registro cronológico de interações/mudanças. | Parcial |
| 5. Autorização por papel | Restringir visibilidade e ações por ADMIN/SOLICITANTE. | Pendente |
| 6. Triagem por IA | Classificação automática de categoria/prioridade e revisão pelo ADMIN. | Pendente |
| 7. Indicadores em tempo real | Contadores por status/prioridade e alerta de prioridade ALTA. | Pendente |
| 8. Frontend | Telas de login, chamados, detalhe, comentários, indicadores e experiência responsiva. | Em andamento |
| 9. Documentação final | README, exemplos de requisição, credenciais de teste, justificativa da IA e instruções locais. | Pendente |
| 10. Submissão | Tornar repositório público, revisar checklist final e enviar link. | Pendente |

## Checklist de Submissão

- [ ] Repositório público e acessível no GitHub.
- [ ] Histórico de commits significativo, sem commit único de projeto final.
- [ ] README com descrição do projeto.
- [ ] README com tecnologias utilizadas.
- [ ] README com passo a passo de instalação e execução local.
- [ ] README com credenciais de ADMIN e SOLICITANTE.
- [ ] README com justificativa da abordagem de IA.
- [ ] Exemplos de requisições com curl ou coleção Postman/Insomnia.
- [ ] Nenhum segredo, senha real ou chave de API commitado.
- [ ] Execução local validada do backend.
- [ ] Execução local validada do frontend.
- [ ] Testes automatizados principais passando.
- [ ] Swagger acessível.
- [ ] Link de deploy informado, se houver.

## Riscos e Pendências de Atenção

- IA e tempo real têm peso alto na avaliação; não devem ficar para o fim.
- Autorização por papel precisa ser validada antes do frontend depender dos dados.
- Histórico de status deve ser modelado sem misturar regra de negócio na entidade.
- O README final é item obrigatório e precisa ser escrito para avaliador reproduzir o projeto rapidamente.
- O repositório está privado durante o desenvolvimento, mas deve ficar público antes da entrega.
