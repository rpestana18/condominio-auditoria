-- "Transporte/Combustível" era lido pelo parser do PostgreSQL como um token de arquivo (file) só, então
-- "transporte" não casava (nem para incluir nem para excluir com -). A barra passa a valer como espaço; a pergunta
-- recebe a mesma troca no rag (RepositorioIndice).
drop index if exists ix_trecho_busca;
alter table trecho drop column busca;
alter table trecho add column busca tsvector
    generated always as (to_tsvector('rag.portuguese_unaccent'::regconfig, translate(texto, '/', ' '))) stored;
create index ix_trecho_busca on trecho using gin (busca);
