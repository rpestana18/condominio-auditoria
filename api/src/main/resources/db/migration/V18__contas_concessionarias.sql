-- Contas do fluxo de cada concessionária (RF-12.1). O código e o nome da conta mudam de uma administradora para outra,
-- então o Gestor ou o Admin confirma cada conta. Sem linha aqui, a conta é só sugestão (calculada na consulta) e não
-- entra nos gráficos. utility_type nulo: a conta foi recusada (não é de concessionária).

create table utility_account (
    id              uuid primary key,
    condominium_id  uuid not null references condominium (id),
    account_code    varchar(50) not null,
    account_name    varchar(300),
    -- WATER, ELECTRICITY, GAS; nulo = recusada
    utility_type    varchar(20),
    decided_by      varchar(200) not null,
    decided_at      timestamptz not null,
    constraint uq_utility_account unique (condominium_id, account_code),
    constraint ck_utility_account_type check (utility_type is null or utility_type in ('WATER', 'ELECTRICITY', 'GAS'))
);
