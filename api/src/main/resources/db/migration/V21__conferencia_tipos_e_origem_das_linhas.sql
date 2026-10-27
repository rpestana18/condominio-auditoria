-- ADR 0009, Decisões 1, 2 e 3 B (RF-13.12, RF-13.13 e RF-13.14).

-- Decisão 2: tipos revistos. MONTHLY vira RECURRING; ANNUAL passa a ser só "anual"; entram OCCASIONAL e SEASONAL.
-- Os tipos confirmados ficam com o nome novo. As sugestões gravadas são apagadas: a consulta recalcula a sugestão pelas
-- regras novas, e onde ela difere do tipo confirmado a tela mostra "a sugestão mudou".
alter table budget_line drop constraint ck_budget_line_spending_profile;
alter table budget_line drop constraint ck_budget_line_spending_profile_both;
update budget_line set spending_profile = 'RECURRING' where spending_profile = 'MONTHLY';
update budget_line set spending_profile = null, spending_profile_status = null, spending_profile_reason = null
    where spending_profile_status = 'SUGGESTED';
alter table budget_line add constraint ck_budget_line_spending_profile check (spending_profile is null
    or spending_profile in ('RECURRING', 'OCCASIONAL', 'SEASONAL', 'ANNUAL', 'PROVISION', 'SEPARATE_APPORTIONMENT'));
alter table budget_line add constraint ck_budget_line_spending_profile_both check (
    (spending_profile is null) = (spending_profile_status is null));

-- A trilha guarda o nome novo do mesmo tipo (como na V17): o valor MONTHLY não existe mais no código.
alter table spending_profile_event disable trigger user;
update spending_profile_event set previous_profile = 'RECURRING' where previous_profile = 'MONTHLY';
update spending_profile_event set new_profile = 'RECURRING' where new_profile = 'MONTHLY';
alter table spending_profile_event enable trigger user;

-- Decisão 1: conferência de cada linha por uma pessoa. As linhas das POs já confirmadas ficam PENDING: o piloto passa
-- pela conferência uma vez. group_account (O2): a linha cita uma conta de grupo; description_prevails (O1): a linha vale
-- pela descrição, não pela conta impressa. As duas marcas são do Admin e entram na sugestão do de-para.
alter table budget_line add column review_status varchar(20) not null default 'PENDING';
alter table budget_line add column reviewed_by varchar(200);
alter table budget_line add column reviewed_at timestamptz;
alter table budget_line add column group_account boolean not null default false;
alter table budget_line add column description_prevails boolean not null default false;
alter table budget_line add constraint ck_budget_line_review_status check (review_status in ('PENDING', 'REVIEWED'));

-- Trilha da conferência: quem, quando, estado e marcas. Só de inserção, como a do tipo da linha (V19).
create table budget_line_review_event (
    id                    uuid primary key,
    condominium_id        uuid not null references condominium (id),
    budget_id             uuid not null,
    budget_line_id        uuid not null,
    -- Código (rótulo) e descrição da linha no momento do evento
    line_code             varchar(20),
    line_description      text,
    -- REVIEWED, REOPENED, MARKS_CHANGED, ORIGIN_CHANGED
    action                varchar(20) not null,
    username              varchar(200) not null,
    occurred_at           timestamptz not null,
    previous_status       varchar(20) not null,
    new_status            varchar(20) not null,
    group_account         boolean not null,
    description_prevails  boolean not null,
    reason                text,
    constraint ck_budget_line_review_event_action check (action in ('REVIEWED', 'REOPENED', 'MARKS_CHANGED',
        'ORIGIN_CHANGED'))
);
create index ix_budget_line_review_event on budget_line_review_event (budget_id, occurred_at);

create trigger tg_budget_line_review_event_insert_only before update or delete on budget_line_review_event
    for each row execute function recusar_alteracao_trilha();

-- Decisão 3 B: de onde veio o valor de cada linha (planilha de apoio ou Admin). 1:1 com a linha. Só explica: nunca entra
-- no cálculo, o previsto é sempre o valor da PO (O4). A planilha não vai para o banco: ficam só os dados lidos, com o
-- arquivo, a aba, a linha e o hash de origem.
create table budget_line_origin (
    id                uuid primary key,
    budget_line_id    uuid not null unique references budget_line (id) on delete cascade,
    budget_id         uuid not null,
    condominium_id    uuid not null references condominium (id),
    -- Códigos das contas de origem, separados por vírgula (ex.: 1591,1592)
    source_accounts   text,
    average_from      date,
    average_to        date,
    average_amount    numeric(15, 2),
    base_amount       numeric(15, 2),
    -- AVERAGE_TIMES_INDEX, EQUAL_TO_AVERAGE, TYPED, NO_AVERAGE
    method            varchar(30),
    index_name        varchar(100),
    index_rate        numeric(9, 6),
    index_forecast    boolean not null default false,
    index_note        text,
    final_amount      numeric(15, 2),
    reason            text,
    -- SHEET (planilha de apoio) ou ADMIN
    source            varchar(10) not null,
    file_id           uuid references source_file (id),
    file_name         varchar(300),
    sheet             varchar(100),
    sheet_row         integer,
    sha256            varchar(64),
    changed_by        varchar(200) not null,
    changed_at        timestamptz not null,
    constraint ck_budget_line_origin_method check (method is null
        or method in ('AVERAGE_TIMES_INDEX', 'EQUAL_TO_AVERAGE', 'TYPED', 'NO_AVERAGE')),
    constraint ck_budget_line_origin_source check (source in ('SHEET', 'ADMIN'))
);
create index ix_budget_line_origin_budget on budget_line_origin (budget_id);
