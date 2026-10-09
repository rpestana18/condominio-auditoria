-- Estado da indexação do arquivo para a busca nos documentos (ADR 0003, Decisão 5.1), separado do status da
-- leitura contábil. O indexacao_id identifica o pedido de indexação em andamento: resultado que chega com outro id
-- (velho ou repetido) é descartado. Os trechos e vetores ficam no schema do rag; aqui só o estado.
-- Arquivos já existentes ficam sem estado (colunas nulas) até serem reprocessados (RF-04.6).
alter table arquivo add column indexacao_situacao      varchar(20);
alter table arquivo add column indexacao_motivo        text;
alter table arquivo add column indexacao_paginas       integer;
alter table arquivo add column indexacao_trechos       integer;
alter table arquivo add column indexacao_id            uuid;
alter table arquivo add column indexacao_enfileirada_em timestamptz;
alter table arquivo add column indexacao_tentativas    integer not null default 0;
alter table arquivo add column indexacao_atualizada_em timestamptz;

alter table arquivo add constraint ck_arquivo_indexacao_situacao
    check (indexacao_situacao in ('NA_FILA', 'INDEXANDO', 'INDEXADO', 'SEM_TEXTO', 'RETIRADO', 'ERRO'));
-- Com situação, sempre há pedido (id e data); sem situação, nada.
alter table arquivo add constraint ck_arquivo_indexacao_pedido
    check ((indexacao_situacao is null) = (indexacao_id is null)
       and (indexacao_situacao is null) = (indexacao_enfileirada_em is null));
alter table arquivo add constraint ck_arquivo_indexacao_contagens
    check ((indexacao_paginas is null or indexacao_paginas >= 0) and (indexacao_trechos is null or indexacao_trechos >= 0));

-- Varredura dos pedidos parados (Na fila ou Indexando há mais tempo que o limite)
create index ix_arquivo_fila_indexacao on arquivo (indexacao_situacao, indexacao_enfileirada_em)
    where indexacao_situacao in ('NA_FILA', 'INDEXANDO');
