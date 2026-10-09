-- Catálogo de rubricas do condomínio (ADR 0005, Decisão 1; RF-11.7). Linhas de exercícios diferentes ligadas à mesma
-- rubrica correspondem na comparação entre exercícios. A sugestão usa a conta da PO e o grupo, nunca o código do item.

create table rubrica (
    id               uuid primary key,
    condominio_id    uuid not null references condominio (id),
    nome             varchar(300) not null,
    -- Grupo da PO onde a rubrica nasceu (ex.: 1.3). Só informativo: a linha de outro exercício pode estar noutro grupo
    grupo_codigo     varchar(20),
    -- Linha da PO que deu origem à rubrica (primeira PO confirmada ou "nova rubrica" a partir de uma linha)
    linha_origem_id  uuid references linha_po (id),
    criada_por       varchar(200) not null,
    criada_em        timestamptz not null
);
create index ix_rubrica_condominio on rubrica (condominio_id);

-- Rubrica de cada linha de uma PO confirmada: uma por linha (várias linhas podem ter a mesma rubrica).
-- Só CONFIRMADO entra na comparação por linha; linha sem rubrica confirmada fica "sem correspondência" (RF-11.6).
create table linha_rubrica (
    id               uuid primary key,
    condominio_id    uuid not null references condominio (id),
    previsao_id      uuid not null references previsao_orcamentaria (id),
    linha_po_id      uuid not null references linha_po (id),
    rubrica_id       uuid not null references rubrica (id),
    -- SUGERIDO, CONFIRMADO, RECUSADO
    estado           varchar(20) not null,
    -- PRIMEIRA_PO, CONTA_PO, VERSAO_ANTERIOR, MANUAL
    origem           varchar(20) not null,
    motivo           text,
    atualizado_por   varchar(200) not null,
    atualizado_em    timestamptz not null,
    constraint uk_linha_rubrica unique (linha_po_id),
    constraint ck_linha_rubrica_estado check (estado in ('SUGERIDO', 'CONFIRMADO', 'RECUSADO')),
    constraint ck_linha_rubrica_origem check (origem in ('PRIMEIRA_PO', 'CONTA_PO', 'VERSAO_ANTERIOR', 'MANUAL'))
);
create index ix_linha_rubrica_previsao on linha_rubrica (previsao_id);
create index ix_linha_rubrica_condominio on linha_rubrica (condominio_id, estado);

-- Trilha das rubricas: quem, quando, linha, rubrica e estado anteriores e novos. Só de inserção: o banco recusa
-- update e delete (mesmo gatilho da trilha da PO, V7). Criação e troca de nome da rubrica não têm linha.
create table evento_rubrica (
    id                   uuid primary key,
    condominio_id        uuid not null references condominio (id),
    previsao_id          uuid,
    linha_po_id          uuid,
    -- Código efetivo e descrição da linha no momento do evento
    linha_codigo         varchar(20),
    linha_descricao      text,
    -- CRIADA, RENOMEADA, SUGERIDO, CONFIRMADO, RECUSADO, ALTERADO
    acao                 varchar(20) not null,
    usuario              varchar(200) not null,
    em                   timestamptz not null,
    rubrica_anterior_id  uuid,
    rubrica_anterior     text,
    estado_anterior      varchar(20),
    rubrica_nova_id      uuid not null,
    rubrica_nova         text not null,
    estado_novo          varchar(20),
    origem               varchar(20),
    motivo               text
);
create index ix_evento_rubrica on evento_rubrica (previsao_id, em);

create trigger tg_evento_rubrica_so_insercao before update or delete on evento_rubrica
    for each row execute function recusar_alteracao_trilha();
