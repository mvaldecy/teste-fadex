# Design: Autenticacao, RBAC e historico de chamados

## Contexto

O backend do Fadex Helpdesk ja possui login JWT, criacao de usuarios, chamados,
comentarios, filtros por `Specification`, envio SMTP e tratamento padrao de
erros. A proxima etapa endurece as regras de acesso por perfil, troca o fluxo de
senha inicial para senha provisoria, adiciona refresh token persistido e cria um
historico de eventos do chamado.

O escopo desta especificacao e apenas a API. O frontend sera adaptado depois.

## Objetivos

- Fazer buscas respeitarem a role do usuario autenticado.
- Permitir que administradores criem usuarios com senha provisoria enviada por
  e-mail.
- Obrigar troca de senha no primeiro login antes de liberar uso normal da API.
- Adicionar refresh token persistido no banco, com expiracao e revogacao.
- Melhorar mensagens de validacao em DTOs e entidades.
- Registrar eventos relevantes do chamado em historico separado dos comentarios.

## Fora de escopo

- Implementacao de telas no frontend.
- Recuperacao de senha por e-mail.
- Logout em todos os dispositivos por endpoint dedicado, exceto revogacao
  interna necessaria na troca de senha.
- Implementacao final de classificacao automatica por IA.
- Auditoria generica de usuario fora do dominio de chamados.

## Acesso por role

As regras de acesso devem ser aplicadas no service, compondo filtros resolvidos
antes de chamar as specifications.

Para `ADMIN`:

- `GET /api/v1/tickets` mantem visao global e respeita filtros informados.
- `GET /api/v1/users` mantem visao global e respeita filtros informados.
- Detalhes de usuario, chamados, comentarios e eventos podem ser acessados sem
  restricao por propriedade do recurso.

Para `SOLICITANTE`:

- `GET /api/v1/tickets` deve forcar `requesterId` igual ao id autenticado.
- `GET /api/v1/users` deve forcar `id` igual ao id autenticado.
- `GET /api/v1/tickets/{id}` so retorna chamado em que ele seja solicitante.
- `GET /api/v1/tickets/{ticketId}/comments` so lista se o chamado for dele.
- `POST /api/v1/tickets/{ticketId}/comments` so cria comentario se o chamado for
  dele.
- `GET /api/v1/tickets/{ticketId}/events` so lista eventos se o chamado for dele.

Quando um solicitante tentar acessar recurso de outro usuario, a API deve
retornar erro tratado pelo `GlobalExceptionHandler` com status `403 FORBIDDEN`.

## Senha provisoria

`POST /api/v1/users` deixa de receber `password`. O administrador informa nome,
e-mail e role. A API deve:

1. Validar disponibilidade do e-mail.
2. Gerar senha provisoria forte.
3. Salvar apenas o hash da senha.
4. Marcar o usuario com `mustChangePassword = true`.
5. Enviar e-mail com a senha provisoria.

Se o envio de e-mail falhar, a criacao deve falhar para evitar usuario criado sem
receber credencial inicial.

Usuarios criados pelo seed de desenvolvimento podem continuar com senha fixa para
facilitar uso local. Para esses usuarios, `mustChangePassword` deve ser `false`.

## Login com troca obrigatoria

Ao autenticar usuario com `mustChangePassword = true`, a API deve retornar login
valido com token limitado. A resposta deve incluir:

- `accessToken`
- `refreshToken`, nulo enquanto a senha provisoria nao for trocada
- `tokenType`
- `expiresIn`
- `mustChangePassword`
- `role`
- `user`

O access token limitado deve conter claim indicando que a senha precisa ser
trocada. Enquanto essa claim estiver ativa, a camada de seguranca deve permitir
apenas `POST /api/v1/auth/change-password` entre endpoints protegidos.

O endpoint de troca de senha deve receber senha atual provisoria, nova senha e
confirmacao. Ao trocar com sucesso, a API deve:

1. Validar a senha atual.
2. Validar forca e confirmacao da nova senha.
3. Atualizar o hash.
4. Marcar `mustChangePassword = false`.
5. Revogar refresh tokens antigos do usuario.
6. Emitir novo access token normal e novo refresh token.

## Refresh token

O refresh token deve ser persistido no banco usando hash do valor entregue ao
cliente. A tabela deve registrar:

- id
- usuario
- hash do token
- data de expiracao
- data de revogacao, quando houver
- `createdAt`
- `updatedAt`

O endpoint `POST /api/v1/auth/refresh` deve receber o refresh token bruto,
comparar com o hash salvo, validar expiracao e revogacao, e emitir novo access
token. Refresh para usuario ainda obrigado a trocar senha deve ser recusado.

## Historico de chamados

Comentarios continuam representando a conversa entre solicitante e atendimento.
Historico de eventos fica em entidade separada chamada `TicketEvent`.

Campos:

- `id`
- `ticket`
- `actor`, nullable
- `type`
- `description`
- `metadata`, opcional
- `createdAt`

`actor` deve ser preenchido quando a acao for feita por usuario autenticado. Para
acoes do sistema, como classificacao automatica futura, `actor` fica nulo.

Eventos iniciais:

- `CHAMADO_CRIADO`
- `COMENTARIO_ADICIONADO`
- `STATUS_ALTERADO`
- `RESPONSAVEL_ATRIBUIDO`
- `PRIORIDADE_ALTERADA`
- `CATEGORIA_ALTERADA`
- `CLASSIFICACAO_ATUALIZADA`

Nesta etapa, devem ser gravados pelo menos os eventos ja suportados pelos fluxos
atuais:

- criacao de chamado
- adicao de comentario

O endpoint inicial deve ser:

- `GET /api/v1/tickets/{ticketId}/events`

A listagem deve ser paginada, ordenada por `createdAt desc` por padrao e seguir
as mesmas regras de acesso do chamado.

## Validacoes

DTOs devem usar mensagens explicitas em portugues nas anotacoes de validacao.
Exemplos:

- `@NotBlank(message = "Nome e obrigatorio.")`
- `@Email(message = "E-mail deve ter formato valido.")`
- `@Size(max = 120, message = "Nome deve ter no maximo 120 caracteres.")`
- `@NotNull(message = "Perfil e obrigatorio.")`

Entidades devem refletir constraints compativeis com banco e dominio, como
tamanho maximo, obrigatoriedade e formato de e-mail no cadastro de usuarios. O
formato de erro global permanece o atual.

## Migracoes

Como o banco usa Flyway com `ddl-auto=validate`, as alteracoes de schema devem
entrar em nova migration.

Mudancas esperadas:

- adicionar `must_change_password` em `users`
- criar `refresh_tokens`
- criar `ticket_events`
- criar indices para buscas por usuario, ticket, token e data de evento

## Testes

Devem ser adicionados ou atualizados testes de service e seguranca para cobrir:

- solicitante lista apenas seus chamados
- solicitante lista apenas seu usuario em `/users`
- solicitante nao acessa chamado de outro usuario
- criacao de usuario gera senha provisoria, marca troca obrigatoria e envia
  e-mail
- login com senha provisoria retorna `mustChangePassword = true`
- usuario com senha provisoria fica bloqueado fora do endpoint de troca de senha
- troca de senha libera usuario e revoga refresh tokens antigos
- refresh token expirado ou revogado e recusado
- criacao de chamado grava evento `CHAMADO_CRIADO`
- criacao de comentario grava evento `COMENTARIO_ADICIONADO`

## Documentacao

Atualizar `docs/backend/api.md` para refletir:

- novo contrato de `POST /api/v1/users`
- campos adicionais da resposta de login
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/change-password`
- regras de acesso por role
- endpoint de historico de eventos
