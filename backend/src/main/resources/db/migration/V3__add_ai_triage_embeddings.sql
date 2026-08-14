${pgvector-extension};

alter table tickets add column classification_justification text;
alter table tickets add column embedding ${ticket-embedding-column-type};
alter table tickets add column embedding_model varchar(120);
alter table tickets add column embedding_updated_at timestamp;

create table ai_jobs (
    id uuid primary key,
    ticket_id uuid not null,
    type varchar(30) not null,
    status varchar(30) not null,
    attempts integer not null,
    next_attempt_at timestamp not null,
    last_error text,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_ai_jobs_ticket foreign key (ticket_id) references tickets (id),
    constraint ck_ai_jobs_type check (type in ('CLASSIFICATION', 'EMBEDDING')),
    constraint ck_ai_jobs_status check (status in ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    constraint ck_ai_jobs_attempts_non_negative check (attempts >= 0)
);

create table ticket_links (
    id uuid primary key,
    source_ticket_id uuid not null,
    target_ticket_id uuid not null,
    created_by uuid not null,
    created_at timestamp not null,
    constraint fk_ticket_links_source foreign key (source_ticket_id) references tickets (id),
    constraint fk_ticket_links_target foreign key (target_ticket_id) references tickets (id),
    constraint fk_ticket_links_created_by foreign key (created_by) references users (id),
    constraint ck_ticket_links_distinct check (source_ticket_id <> target_ticket_id),
    constraint uk_ticket_links_pair unique (source_ticket_id, target_ticket_id)
);

create index idx_ai_jobs_status_next_attempt_at on ai_jobs (status, next_attempt_at);
create index idx_ai_jobs_ticket_id on ai_jobs (ticket_id);
create index idx_ticket_links_source on ticket_links (source_ticket_id);
create index idx_ticket_links_target on ticket_links (target_ticket_id);

${ticket-embedding-index};
