-- Cancelamento de chamado: exclusao logica por status, preservando historico, comentarios e
-- metricas. Nenhum registro e removido.
--
-- Os dois check abaixo sao validados em runtime, e nao no build: sem esta migracao o status novo e
-- o evento novo compilam e falham na gravacao, ja em producao.

alter table tickets drop constraint ck_tickets_status;

alter table tickets add constraint ck_tickets_status
    check (status in ('ABERTO', 'EM_ANDAMENTO', 'RESOLVIDO', 'FECHADO', 'CANCELADO'));

-- ck_ticket_events_type foi redefinido pela V4 com oito valores. O evento de cancelamento entra
-- aqui pelo mesmo padrao de drop e re-add.
alter table ticket_events drop constraint ck_ticket_events_type;

alter table ticket_events add constraint ck_ticket_events_type check (type in (
    'CHAMADO_CRIADO',
    'COMENTARIO_ADICIONADO',
    'STATUS_ALTERADO',
    'RESPONSAVEL_ATRIBUIDO',
    'RESPONSAVEL_REMOVIDO',
    'PRIORIDADE_ALTERADA',
    'CATEGORIA_ALTERADA',
    'CLASSIFICACAO_ATUALIZADA',
    'CHAMADO_CANCELADO'
));
