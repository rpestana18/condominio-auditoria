-- Previsão orçamentária (PO) lida, confirmação e primeiras regras do orçamento (ADR 0004, Decisões 3 e 8;
-- RF-03.1.1 a RF-03.1.3). Dinheiro em numeric(15,2); nada aqui altera o arquivo original.
-- V6 está reservada por outro branch.

-- Uma PO por arquivo lido. Reprocessar o mesmo arquivo atualiza esta linha (nunca duplica).
create table previsao_orcamentaria (
    id                          uuid primary key,
    condominio_id               uuid not null references condominio (id),
    arquivo_id                  uuid not null references arquivo (id),
    sha256                      varchar(64) not null,
    interpretador               varchar(60),
    titulo                      text,
    exercicio_impresso          varchar(60),
    coluna_orcado_anterior      varchar(40),
    coluna_orcado               varchar(40),
    -- LIDA, LIDA_COM_DIVERGENCIA, CONFIRMADA, SUBSTITUIDA
    estado                      varchar(30) not null,
    -- Lidos do documento: total impresso e total − fundos impressos
    total_impresso              numeric(15, 2),
    previsto_mes_impresso       numeric(15, 2),
    -- Usado nos cálculos: soma das linhas dos grupos que não são fundos (RF-03.1.2, Q29)
    previsto_mes                numeric(15, 2),
    -- Diferença máxima, por conferência, tratada como arredondamento na leitura desta PO
    tolerancia_arredondamento   numeric(15, 2) not null,
    lida_em                     timestamptz not null,
    -- Preenchidos na confirmação (RF-03.1.3)
    versao                      integer,
    exercicio_inicio            date,
    exercicio_fim               date,
    substituida_desde        date,
    ata_arquivo_id              uuid references arquivo (id),
    sem_ata                     boolean not null default false,
    data_aprovacao              date,
    ciente_divergencia          boolean not null default false,
    justificativa_divergencia   text,
    confirmada_por              varchar(200),
    confirmada_em               timestamptz,
    constraint uk_previsao_arquivo unique (arquivo_id),
    constraint ck_previsao_exercicio check (exercicio_fim is null or exercicio_fim >= exercicio_inicio)
);
create index ix_previsao_condominio on previsao_orcamentaria (condominio_id, estado);

-- Linhas como impressas. O código efetivo é igual ao impresso; o Admin só muda quando o código se repete.
-- O id é o destino do de-para (nunca a conta da PO).
create table linha_po (
    id                uuid primary key,
    previsao_id       uuid not null references previsao_orcamentaria (id),
    condominio_id     uuid not null references condominio (id),
    arquivo_id        uuid not null references arquivo (id),
    sha256            varchar(64) not null,
    ordem             integer not null,
    pagina            integer not null,
    -- TOTAL, GRUPO, LINHA
    tipo              varchar(10) not null,
    codigo_impresso   varchar(20) not null,
    codigo_efetivo    varchar(20) not null,
    conta             text,
    conta_texto       text,
    marca             varchar(40),
    descricao         text not null,
    orcado_anterior   numeric(15, 2) not null,
    orcado            numeric(15, 2) not null,
    percentual_texto  varchar(40),
    observacoes       text,
    constraint uk_linha_po_ordem unique (previsao_id, ordem)
);

-- Ligação de cada linha de fundo (1.9.x) a um fundo do fluxo, feita pelo Admin na confirmação (ADR 0004, Decisão 7).
create table po_fundo (
    id           uuid primary key,
    previsao_id  uuid not null references previsao_orcamentaria (id),
    linha_po_id  uuid not null references linha_po (id),
    fundo_id     uuid not null references fundo (id),
    constraint uk_po_fundo_linha unique (previsao_id, linha_po_id),
    constraint uk_po_fundo_fundo unique (previsao_id, fundo_id)
);

-- Trilha da PO (confirmação, substituição). Só de inserção: o banco recusa update e delete.
-- Migra para a trilha geral quando o RF-07.4 existir.
create table evento_previsao (
    id             uuid primary key,
    previsao_id    uuid not null references previsao_orcamentaria (id),
    condominio_id  uuid not null references condominio (id),
    tipo           varchar(30) not null,
    usuario        varchar(200) not null,
    em             timestamptz not null,
    justificativa  text,
    detalhe        text not null
);
create index ix_evento_previsao on evento_previsao (previsao_id, em);

create function recusar_alteracao_trilha() returns trigger language plpgsql as $$
begin
    raise exception 'A tabela % é só de inserção: % não é permitido', tg_table_name, tg_op;
end;
$$;

create trigger tg_evento_previsao_so_insercao before update or delete on evento_previsao
    for each row execute function recusar_alteracao_trilha();

-- Parâmetros das regras, por condomínio e com vigência (o valor vale para datas entre vigente_desde e vigente_ate).
create table parametro_regra (
    id             uuid primary key,
    condominio_id  uuid not null references condominio (id),
    codigo         varchar(60) not null,
    valor          numeric(15, 4) not null,
    vigente_desde  date not null,
    vigente_ate    date,
    fonte          text not null,
    constraint uk_parametro_regra unique (condominio_id, codigo, vigente_desde)
);

-- Conv. 20.1 do piloto: fundo de reserva de até 5% (parâmetro de implantação, não constante no código).
insert into parametro_regra (id, condominio_id, codigo, valor, vigente_desde, fonte)
select gen_random_uuid(), c.id, 'TETO_FUNDO_RESERVA_PERCENTUAL', 5.0000, date '1900-01-01',
       'Convenção, cláusula 20.1 (implantação)'
from condominio c;

-- Achados (primeira versão da tabela do §4 da arquitetura). Gravação idempotente pela chave única.
create table achado (
    id             uuid primary key,
    condominio_id  uuid not null references condominio (id),
    regra          varchar(60) not null,
    versao_regra   varchar(20) not null,
    -- INFORMATIVO, ATENCAO, CRITICO
    severidade     varchar(20) not null,
    competencia    date not null,
    alvo           varchar(200) not null,
    descricao      text not null,
    -- ABERTO (demais estados entram com o RF-02.8 e a Q27)
    estado         varchar(30) not null,
    criado_em      timestamptz not null,
    constraint uk_achado unique (condominio_id, regra, competencia, alvo)
);

create table achado_evidencia (
    id           uuid primary key,
    achado_id    uuid not null references achado (id),
    ordem        integer not null,
    arquivo_id   uuid not null references arquivo (id),
    sha256       varchar(64) not null,
    pagina       integer,
    referencia   text not null,
    linha_po_id  uuid references linha_po (id),
    constraint uk_achado_evidencia unique (achado_id, ordem)
);
