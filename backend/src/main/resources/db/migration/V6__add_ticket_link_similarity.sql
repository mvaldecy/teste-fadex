alter table ticket_links add column similarity double precision;

-- Cosseno varia de -1 a 1. O valor e arredondado antes de gravar, entao a borda superior nao
-- estoura por erro de ponto flutuante.
alter table ticket_links add constraint ck_ticket_links_similarity_range
    check (similarity is null or (similarity >= -1 and similarity <= 1));
