create table users (
    id uuid primary key,
    name varchar(120) not null,
    email varchar(180) not null,
    password_hash varchar(255) not null,
    role varchar(30) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_users_email unique (email),
    constraint ck_users_role check (role in ('ADMIN', 'SOLICITANTE'))
);

create table tickets (
    id uuid primary key,
    title varchar(160) not null,
    description text not null,
    category varchar(40) not null,
    priority varchar(20) not null,
    status varchar(30) not null,
    requester_id uuid not null,
    assignee_id uuid,
    classification_origin varchar(30) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_tickets_requester foreign key (requester_id) references users (id),
    constraint fk_tickets_assignee foreign key (assignee_id) references users (id),
    constraint ck_tickets_category check (category in ('ACESSO', 'SISTEMAS', 'INFRAESTRUTURA', 'EQUIPAMENTOS', 'FINANCEIRO', 'RH', 'OUTROS')),
    constraint ck_tickets_priority check (priority in ('BAIXA', 'MEDIA', 'ALTA')),
    constraint ck_tickets_status check (status in ('ABERTO', 'EM_ANDAMENTO', 'RESOLVIDO', 'FECHADO')),
    constraint ck_tickets_classification_origin check (classification_origin in ('IA', 'MANUAL', 'PENDENTE'))
);

create table ticket_comments (
    id uuid primary key,
    ticket_id uuid not null,
    author_id uuid not null,
    text text not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_ticket_comments_ticket foreign key (ticket_id) references tickets (id),
    constraint fk_ticket_comments_author foreign key (author_id) references users (id)
);

create index idx_tickets_requester_id on tickets (requester_id);
create index idx_tickets_assignee_id on tickets (assignee_id);
create index idx_tickets_status on tickets (status);
create index idx_tickets_priority on tickets (priority);
create index idx_tickets_category on tickets (category);
create index idx_ticket_comments_ticket_id_created_at on ticket_comments (ticket_id, created_at);
