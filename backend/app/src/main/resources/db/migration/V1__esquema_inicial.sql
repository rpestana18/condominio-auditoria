-- Só dados processados. Os arquivos originais ficam na pasta de dados, nunca no banco.
-- Toda tabela de negócio leva condominio_id (produto multi-condomínio).

create table condominio (
    id          uuid primary key,
    nome        varchar(200) not null,
    cnpj        varchar(18),
    criado_em   timestamptz not null default now()
);

create table arquivo (
    id                 uuid primary key,
    condominio_id      uuid not null references condominio (id),
    categoria          varchar(30) not null,
    nome_original      varchar(300) not null,
    caminho            varchar(600) not null,
    sha256             varchar(64) not null,
    tamanho_bytes      bigint not null,
    tipo_conteudo      varchar(120),
    status             varchar(20) not null,
    mensagem           text,
    interpretador      varchar(60),
    periodo_inicio     date,
    periodo_fim        date,
    total_lancamentos  integer,
    enviado_por        varchar(120) not null,
    enviado_em         timestamptz not null,
    processado_em      timestamptz,
    constraint uk_arquivo_hash unique (condominio_id, sha256)
);
create index ix_arquivo_lista on arquivo (condominio_id, categoria, enviado_em desc);
create index ix_arquivo_status on arquivo (status);

create table fundo (
    id             uuid primary key,
    condominio_id  uuid not null references condominio (id),
    nome           varchar(120) not null,
    constraint uk_fundo_nome unique (condominio_id, nome)
);

create table lancamento (
    id                          uuid primary key,
    condominio_id               uuid not null references condominio (id),
    arquivo_id                  uuid not null references arquivo (id),
    fundo_id                    uuid not null references fundo (id),
    data                        date not null,
    conta_codigo                varchar(20),
    conta_nome                  varchar(200),
    documento                   varchar(40),
    historico                   text not null,
    credito                     numeric(15, 2) not null,
    debito                      numeric(15, 2) not null,
    saldo                       numeric(15, 2) not null,
    pagina                      integer not null,
    ordem                       integer not null,
    -- Campos enriquecidos (deduzidos do histórico)
    nota_fiscal                 varchar(20),
    fornecedor                  varchar(200),
    meio_pagamento              varchar(40),
    transferencia_entre_fundos  boolean not null default false
);
create index ix_lancamento_arquivo on lancamento (arquivo_id, ordem);
create index ix_lancamento_consulta on lancamento (condominio_id, data);

create table saldo_fundo (
    id              uuid primary key,
    condominio_id   uuid not null references condominio (id),
    arquivo_id      uuid not null references arquivo (id),
    fundo_id        uuid not null references fundo (id),
    periodo_inicio  date not null,
    periodo_fim     date not null,
    saldo_anterior  numeric(15, 2) not null,
    creditos        numeric(15, 2) not null,
    debitos         numeric(15, 2) not null,
    saldo_atual     numeric(15, 2) not null
);
create index ix_saldo_fundo_arquivo on saldo_fundo (arquivo_id);

create table conferencia (
    id          uuid primary key,
    arquivo_id  uuid not null references arquivo (id),
    ordem       integer not null,
    codigo      varchar(40) not null,
    descricao   varchar(200) not null,
    ok          boolean not null,
    detalhe     text
);
create index ix_conferencia_arquivo on conferencia (arquivo_id, ordem);
