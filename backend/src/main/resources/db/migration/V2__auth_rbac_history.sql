alter table users
    add column must_change_password boolean not null default false;

create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(255) not null,
    expires_at timestamp not null,
    revoked_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_refresh_tokens_user foreign key (user_id) references users (id),
    constraint uk_refresh_tokens_token_hash unique (token_hash)
);

create table ticket_events (
    id uuid primary key,
    ticket_id uuid not null,
    actor_id uuid,
    type varchar(50) not null,
    description varchar(255) not null,
    metadata text,
    created_at timestamp not null,
    constraint fk_ticket_events_ticket foreign key (ticket_id) references tickets (id),
    constraint fk_ticket_events_actor foreign key (actor_id) references users (id),
    constraint ck_ticket_events_type check (type in (
        'CHAMADO_CRIADO',
        'COMENTARIO_ADICIONADO',
        'STATUS_ALTERADO',
        'RESPONSAVEL_ATRIBUIDO',
        'PRIORIDADE_ALTERADA',
        'CATEGORIA_ALTERADA',
        'CLASSIFICACAO_ATUALIZADA'
    ))
);

create index idx_refresh_tokens_user_id on refresh_tokens (user_id);
create index idx_refresh_tokens_expires_at on refresh_tokens (expires_at);
create index idx_ticket_events_ticket_id_created_at on ticket_events (ticket_id, created_at);
create index idx_ticket_events_actor_id on ticket_events (actor_id);
create index idx_ticket_events_type on ticket_events (type);
