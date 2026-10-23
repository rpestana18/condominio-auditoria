-- Planilha de sugestões do de-para guardada como dado do condomínio (Q51, RF-03.1.5).
-- Só a última planilha carregada vale: cada carga substitui todas as linhas do condomínio. O arquivo em si não vai
-- para o banco: ficam só as linhas aceitas, já interpretadas, com o nome do arquivo, a linha e o hash de origem.
-- A linha da PO é guardada pelo código efetivo (ex.: 1.7.8), resolvido em cada versão da PO; nunca pelo número da
-- conta do fluxo.
create table account_mapping_sheet_item (
    id               uuid primary key,
    condominium_id   uuid not null references condominium (id),
    -- Conta do fluxo como impressa (com os zeros à esquerda)
    account_code     varchar(20) not null,
    -- BUDGET_LINE, ADJUSTMENT, TO_REALLOCATE, TRANSFER
    target_type      varchar(20) not null,
    -- Código efetivo da linha da PO, só com BUDGET_LINE
    target_line_code varchar(20),
    -- Texto do destino especial, ex.: "estorno" em "AJUSTE (estorno)"
    target_detail    varchar(200),
    file_name        varchar(300),
    file_sha256      varchar(64) not null,
    file_line        integer not null,
    loaded_by        varchar(200) not null,
    loaded_at        timestamptz not null,
    constraint uk_account_mapping_sheet_item unique (condominium_id, account_code),
    constraint ck_account_mapping_sheet_item_type check (target_type in ('BUDGET_LINE', 'ADJUSTMENT', 'TO_REALLOCATE', 'TRANSFER')),
    constraint ck_account_mapping_sheet_item_line check ((target_type = 'BUDGET_LINE') = (target_line_code is not null))
);
