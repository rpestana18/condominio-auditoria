-- Troca de categoria de um arquivo (RF-01.7): quem, quando, de qual para qual.
-- Enquanto a trilha de auditoria (RF-07.4) não existe, o registro fica aqui e migra para ela depois.
create table arquivo_categoria_historico (
    id                  uuid primary key,
    arquivo_id          uuid not null references arquivo (id),
    categoria_anterior  varchar(30) not null,
    categoria_nova      varchar(30) not null,
    alterado_por        varchar(120) not null,
    alterado_em         timestamptz not null
);
create index ix_arquivo_categoria_historico on arquivo_categoria_historico (arquivo_id, alterado_em);
