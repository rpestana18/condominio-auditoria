-- Índice dos documentos para a busca do assistente (ADR 0003, Decisão 3).
-- Só dados processados, reconstruíveis a partir dos originais: o original nunca entra no banco.
-- Toda linha leva arquivo, condomínio e sha256 de origem; trecho leva a localização (página; aba e linhas; parágrafos).

-- Extensões ficam no schema public (o pgvector já vem na imagem pgvector/pgvector; unaccent vem com o PostgreSQL)
create extension if not exists vector schema public;
create extension if not exists unaccent schema public;

-- Português sem acento: "manutenção" acha "manutencao" e vice-versa
create text search configuration rag.portuguese_unaccent (copy = pg_catalog.portuguese);
alter text search configuration rag.portuguese_unaccent
    alter mapping for hword, hword_part, word with public.unaccent, portuguese_stem;

create table documento_indexado (
    arquivo_id          uuid primary key,
    condominio_id       uuid not null,
    categoria           varchar(30) not null,
    nome_original       varchar(300) not null,
    caminho             varchar(600) not null,
    sha256              varchar(64) not null,
    competencia_inicio  date,
    competencia_fim     date,
    versao_arquivo      integer,
    estado              varchar(20) not null
        constraint ck_documento_estado check (estado in ('na_fila', 'indexando', 'indexado', 'sem_texto', 'erro')),
    motivo              text,
    paginas             integer,
    trechos             integer,
    modelo_embeddings   varchar(100),
    versao_indexador    varchar(20),
    vigente             boolean not null default true,
    retirado            boolean not null default false,
    indexacao_id        uuid not null,
    atualizado_em       timestamptz not null default now()
);

create index ix_documento_condominio on documento_indexado (condominio_id);

create table trecho (
    id                uuid primary key,
    arquivo_id        uuid not null references documento_indexado (arquivo_id) on delete cascade,
    condominio_id     uuid not null,
    ordem             integer not null,
    -- PDF
    pagina            integer,
    -- Excel
    aba               varchar(200),
    linha_inicio      integer,
    linha_fim         integer,
    -- Word
    secao             varchar(500),
    paragrafo_inicio  integer,
    paragrafo_fim     integer,
    texto             text not null,
    busca             tsvector generated always as (to_tsvector('rag.portuguese_unaccent'::regconfig, texto)) stored,
    constraint uk_trecho_ordem unique (arquivo_id, ordem),
    -- Exatamente uma localização: página, ou aba com linhas, ou parágrafos
    constraint ck_trecho_localizacao check (
        (pagina is not null and aba is null and paragrafo_inicio is null)
        or (pagina is null and aba is not null and linha_inicio is not null and linha_fim is not null
            and paragrafo_inicio is null)
        or (pagina is null and aba is null and paragrafo_inicio is not null and paragrafo_fim is not null))
);

create index ix_trecho_busca on trecho using gin (busca);
create index ix_trecho_condominio on trecho (condominio_id);

-- Um vetor por trecho e modelo; trocar de modelo reindexa (a dimensão é fixa: 1024)
create table trecho_vetor (
    trecho_id  uuid not null references trecho (id) on delete cascade,
    modelo     varchar(100) not null,
    vetor      public.vector(1024) not null,
    primary key (trecho_id, modelo)
);

-- Índice parcial por modelo (ADR 0003): a consulta repete "modelo = 'bge-m3'" para usá-lo
create index ix_trecho_vetor_bge_m3 on trecho_vetor using hnsw (vetor public.vector_cosine_ops)
    where modelo = 'bge-m3';
