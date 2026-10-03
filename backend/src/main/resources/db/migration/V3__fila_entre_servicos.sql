-- Leitura feita pelo serviço rag, pela fila. O processamento_id identifica a leitura em andamento:
-- resultado que chega com outro id (velho ou repetido) é descartado.
alter table arquivo add column processamento_id uuid;
alter table arquivo add column enfileirado_em timestamptz;
alter table arquivo add column tentativas integer not null default 0;
update arquivo set processamento_id = gen_random_uuid(), enfileirado_em = enviado_em, tentativas = 1;
alter table arquivo alter column processamento_id set not null;
create index ix_arquivo_fila on arquivo (status, enfileirado_em);
