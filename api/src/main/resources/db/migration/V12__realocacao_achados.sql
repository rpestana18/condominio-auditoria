-- Realocação mínima (RF-03.1.7; ADR 0004, Decisão 3), chave estável do lançamento e estados dos achados
-- recalculados (RF-03.1.12, Q27). Nada aqui altera o lançamento original da administradora.

create function recusar_exclusao() returns trigger language plpgsql as $$
begin
    raise exception 'Nada é apagado na tabela %: % não é permitido', tg_table_name, tg_op;
end;
$$;

-- Realocação de um lançamento "a realocar" para uma linha da PO. O id do lançamento muda a cada reprocesso do fluxo
-- (a gravação apaga e recria), por isso a realocação guarda a impressão do lançamento (arquivo, página, ordem, data,
-- conta, documento e valor) e a chave calculada dela. A consulta religa pela chave; sem casamento, a realocação
-- aparece como "sem lançamento correspondente" e nada é somado em silêncio.
-- Desfazer encerra a realocação (desfeita_por e desfeita_em) e grava o evento; a linha nunca é apagada.
create table realocacao (
    id                uuid primary key,
    condominio_id     uuid not null references condominio (id),
    previsao_id       uuid not null references previsao_orcamentaria (id),
    chave_lancamento  varchar(64) not null,
    arquivo_id        uuid not null references arquivo (id),
    sha256            varchar(64) not null,
    pagina            integer not null,
    ordem             integer not null,
    data              date not null,
    conta_codigo      varchar(20),
    conta_nome        varchar(200),
    documento         varchar(40),
    historico         text not null,
    valor             numeric(15, 2) not null,
    linha_po_id       uuid not null references linha_po (id),
    realocada_por     varchar(200) not null,
    realocada_em      timestamptz not null,
    desfeita_por      varchar(200),
    desfeita_em       timestamptz,
    constraint ck_realocacao_desfeita check ((desfeita_por is null) = (desfeita_em is null))
);
-- Um lançamento tem no máximo uma realocação ativa por versão da PO
create unique index uk_realocacao_ativa on realocacao (previsao_id, chave_lancamento) where desfeita_em is null;
create index ix_realocacao_condominio on realocacao (condominio_id, previsao_id, data);

create trigger tg_realocacao_sem_exclusao before delete on realocacao
    for each row execute function recusar_exclusao();

-- Trilha da realocação (REALOCADA, DESFEITA). Só de inserção.
create table evento_realocacao (
    id              uuid primary key,
    realocacao_id   uuid not null references realocacao (id),
    condominio_id   uuid not null references condominio (id),
    acao            varchar(20) not null,
    usuario         varchar(200) not null,
    em              timestamptz not null,
    detalhe         text not null,
    constraint ck_evento_realocacao_acao check (acao in ('REALOCADA', 'DESFEITA'))
);
create index ix_evento_realocacao on evento_realocacao (realocacao_id, em);

create trigger tg_evento_realocacao_so_insercao before update or delete on evento_realocacao
    for each row execute function recusar_alteracao_trilha();

-- Achados: estado "não se aplica mais" (Q27), que é do sistema, separado das marcações humanas do RF-02.8.
-- condicao_presente diz se a condição da regra existia no último recálculo: achado marcado por pessoa mantém o
-- estado, e a mudança da condição só entra no histórico.
alter table achado add column condicao_presente boolean not null default true;
alter table achado add column estado_motivo text;
alter table achado add column estado_em timestamptz;
alter table achado add constraint ck_achado_estado
    check (estado in ('ABERTO', 'NAO_SE_APLICA_MAIS', 'JUSTIFICADO', 'RESOLVIDO', 'FALSO_POSITIVO'));
create index ix_achado_competencia on achado (condominio_id, competencia, regra);

create trigger tg_achado_sem_exclusao before delete on achado
    for each row execute function recusar_exclusao();

-- Histórico do achado: o quê (estado anterior e novo, condição), quem e quando, com o evento que causou.
-- Só de inserção.
create table evento_achado (
    id                 uuid primary key,
    achado_id          uuid not null references achado (id),
    condominio_id      uuid not null references condominio (id),
    estado_anterior    varchar(30),
    estado_novo        varchar(30) not null,
    condicao_presente  boolean not null,
    motivo             text not null,
    usuario            varchar(200) not null,
    em                 timestamptz not null
);
create index ix_evento_achado on evento_achado (achado_id, em);

create trigger tg_evento_achado_so_insercao before update or delete on evento_achado
    for each row execute function recusar_alteracao_trilha();
