-- ADR 0009, Decisão 8 (Q54, usuário, 29/10/2026): o de-para é aplicado sozinho e vale para o cálculo sem confirmação.
-- Nada é apagado: a trilha (account_mapping_event) fica como estava, com as sugestões antigas registradas como SUGGESTED.

-- AUTOMATIC: ligação feita pelo sistema que já vale para o cálculo. As sugestões gravadas até aqui passam a valer.
-- SUGGESTED continua aceito só para não quebrar linhas antigas; o código novo não grava mais esse estado.
alter table account_mapping drop constraint ck_account_mapping_status;
update account_mapping set status = 'AUTOMATIC' where status = 'SUGGESTED';
alter table account_mapping add constraint ck_account_mapping_status
    check (status in ('AUTOMATIC', 'SUGGESTED', 'CONFIRMED', 'REJECTED'));

-- Ligação de baixa confiança (regra objetiva da Decisão 8): vale para o cálculo, mas aparece em "só exceções".
alter table account_mapping add column low_confidence boolean not null default false;
alter table account_mapping_event add column low_confidence boolean;

-- Contas da aba das despesas da planilha de apoio da PO (contracts/mensagens/v5), com a conta-mãe deduzida pelas somas
-- pelo rag. São do plano de contas da PO: o de-para compara só os nomes com os do extrato, nunca o número (RF-00.8).
-- A planilha não vai para o banco: ficam só os dados lidos, com o arquivo, a aba, a linha e o hash de origem.
-- Uma planilha nova da mesma PO substitui as contas da anterior.
create table budget_sheet_account (
    id              uuid primary key,
    condominium_id  uuid not null references condominium (id),
    budget_id       uuid not null references budget (id) on delete cascade,
    account_code    varchar(20) not null,
    account_name    varchar(300) not null,
    parent_code     varchar(20),
    file_id         uuid references source_file (id),
    file_name       varchar(300),
    sheet           varchar(100),
    sheet_row       integer not null,
    sha256          varchar(64),
    saved_at        timestamptz not null,
    constraint uk_budget_sheet_account unique (budget_id, account_code)
);
create index ix_budget_sheet_account_budget on budget_sheet_account (budget_id);

-- Destino "sem linha da PO" escolhido pelo Admin (O7: 1688 Outros serviços contratados e 1665 Honorários advogados): a
-- conta fica em "sem linha da PO" sem o aviso de conta sem linha encontrada. Também aceito na planilha guardada.
alter table account_mapping drop constraint ck_account_mapping_target_type;
alter table account_mapping add constraint ck_account_mapping_target_type
    check (target_type in ('BUDGET_LINE', 'ADJUSTMENT', 'TO_REALLOCATE', 'TRANSFER', 'WITHOUT_LINE'));
alter table account_mapping_sheet_item drop constraint ck_account_mapping_sheet_item_type;
alter table account_mapping_sheet_item add constraint ck_account_mapping_sheet_item_type
    check (target_type in ('BUDGET_LINE', 'ADJUSTMENT', 'TO_REALLOCATE', 'TRANSFER', 'WITHOUT_LINE'));
