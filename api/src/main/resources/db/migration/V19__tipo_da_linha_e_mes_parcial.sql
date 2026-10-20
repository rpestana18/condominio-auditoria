-- ADR 0007, Decisões 2 e 5 A (RF-13.3 e RF-13.10).

-- Tipo da linha da PO (como a linha gasta ao longo do exercício). Nulo = ainda sem sugestão gravada: a consulta calcula
-- a sugestão na hora. Só CONFIRMED é usado na ponte do saldo; sem confirmação vale MONTHLY. O tipo nunca muda o previsto.
-- A coluna "type" já existe (TOTAL, GROUP, LINE), por isso o nome spending_profile.
alter table budget_line add column spending_profile varchar(30);
alter table budget_line add column spending_profile_status varchar(20);
-- Por que o sistema sugeriu (regra que casou) ou o motivo informado pelo Admin
alter table budget_line add column spending_profile_reason text;
alter table budget_line add column spending_profile_changed_by varchar(200);
alter table budget_line add column spending_profile_changed_at timestamptz;
alter table budget_line add constraint ck_budget_line_spending_profile check (spending_profile is null
    or spending_profile in ('MONTHLY', 'PROVISION', 'ANNUAL', 'SEPARATE_APPORTIONMENT'));
alter table budget_line add constraint ck_budget_line_spending_profile_status check (spending_profile_status is null
    or spending_profile_status in ('SUGGESTED', 'CONFIRMED'));
alter table budget_line add constraint ck_budget_line_spending_profile_both check (
    (spending_profile is null) = (spending_profile_status is null));

-- Trilha do tipo da linha: quem, quando, tipo e estado anteriores e novos. Só de inserção, como a do de-para (V8).
create table spending_profile_event (
    id                uuid primary key,
    condominium_id    uuid not null references condominium (id),
    budget_id         uuid not null,
    budget_line_id    uuid not null,
    -- Código (rótulo) e descrição da linha no momento do evento
    line_code         varchar(20),
    line_description  text,
    -- SUGGESTED, CONFIRMED
    action            varchar(20) not null,
    username          varchar(200) not null,
    occurred_at       timestamptz not null,
    previous_profile  varchar(30),
    previous_status   varchar(20),
    new_profile       varchar(30) not null,
    new_status        varchar(20) not null,
    reason            text,
    constraint ck_spending_profile_event_action check (action in ('SUGGESTED', 'CONFIRMED'))
);
create index ix_spending_profile_event on spending_profile_event (budget_id, occurred_at);

create trigger tg_spending_profile_event_insert_only before update or delete on spending_profile_event
    for each row execute function recusar_alteracao_trilha();

-- Mês parcial (Decisão 5 A): o extrato enviado até o último dia do período é parcial. O Gestor ou o Admin pode marcar
-- ou desmarcar. partial_override nulo = vale a regra automática (enviado em ≤ fim do período); true = marcado parcial;
-- false = marcado completo. partial_until: data informada na marcação ("parcial até DD/MM"); nula = data do envio.
alter table source_file add column partial_override boolean;
alter table source_file add column partial_until date;
alter table source_file add column partial_reason text;
alter table source_file add column partial_changed_by varchar(200);
alter table source_file add column partial_changed_at timestamptz;

-- Trilha do mês parcial: marcação, volta à regra automática e substituição pelo extrato completo do mesmo mês.
create table partial_month_event (
    id                uuid primary key,
    condominium_id    uuid not null references condominium (id),
    file_id           uuid not null references source_file (id),
    -- Arquivo completo que substituiu o parcial (só em SUPERSEDED)
    superseded_by_id  uuid references source_file (id),
    -- MARKED_PARTIAL, MARKED_COMPLETE, AUTOMATIC, SUPERSEDED
    action            varchar(20) not null,
    previous_partial  boolean not null,
    new_partial       boolean not null,
    partial_until     date,
    username          varchar(200) not null,
    occurred_at       timestamptz not null,
    reason            text,
    constraint ck_partial_month_event_action check (action in ('MARKED_PARTIAL', 'MARKED_COMPLETE', 'AUTOMATIC',
        'SUPERSEDED'))
);
create index ix_partial_month_event on partial_month_event (file_id, occurred_at);

create trigger tg_partial_month_event_insert_only before update or delete on partial_month_event
    for each row execute function recusar_alteracao_trilha();
