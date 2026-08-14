alter table tickets add column resolved_at timestamp;
alter table tickets add column closed_at timestamp;
alter table tickets add column first_response_at timestamp;
alter table tickets add column assigned_at timestamp;
alter table tickets add column ai_suggested_category varchar(40);
alter table tickets add column ai_suggested_priority varchar(20);
alter table tickets add column ai_confidence double precision;

alter table tickets add constraint ck_tickets_ai_suggested_category
    check (ai_suggested_category is null or ai_suggested_category in (
        'ACESSO', 'SISTEMAS', 'INFRAESTRUTURA', 'EQUIPAMENTOS', 'FINANCEIRO', 'RH', 'OUTROS'
    ));

alter table tickets add constraint ck_tickets_ai_suggested_priority
    check (ai_suggested_priority is null or ai_suggested_priority in ('BAIXA', 'MEDIA', 'ALTA'));

alter table tickets add constraint ck_tickets_ai_confidence_range
    check (ai_confidence is null or (ai_confidence >= 0 and ai_confidence <= 1));

create index idx_tickets_closed_at on tickets (closed_at);
create index idx_tickets_created_at on tickets (created_at);

alter table ticket_events drop constraint ck_ticket_events_type;

alter table ticket_events add constraint ck_ticket_events_type check (type in (
    'CHAMADO_CRIADO',
    'COMENTARIO_ADICIONADO',
    'STATUS_ALTERADO',
    'RESPONSAVEL_ATRIBUIDO',
    'RESPONSAVEL_REMOVIDO',
    'PRIORIDADE_ALTERADA',
    'CATEGORIA_ALTERADA',
    'CLASSIFICACAO_ATUALIZADA'
));
