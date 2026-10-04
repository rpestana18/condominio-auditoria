-- De-para das contas do fluxo para a PO (ADR 0004, Decisões 3 e 4; RF-03.1.4 e RF-03.1.5).
-- Cada conta do fluxo vai para exatamente um destino por versão da PO. O casamento nunca usa o número da conta:
-- nenhuma coluna aqui compara o código da conta do fluxo com a conta da PO.

create table depara_conta (
    id                     uuid primary key,
    condominio_id          uuid not null references condominio (id),
    previsao_id            uuid not null references previsao_orcamentaria (id),
    -- Conta do fluxo como impressa (código com os zeros à esquerda e nome)
    conta_codigo           varchar(20) not null,
    conta_nome             varchar(200),
    -- LINHA_PO, AJUSTE, A_REALOCAR, TRANSFERENCIA
    tipo_destino           varchar(20) not null,
    -- Destino LINHA_PO: a linha da PO (identificador interno, nunca a conta da PO)
    linha_po_id            uuid references linha_po (id),
    -- Texto do destino especial, ex.: "estorno" em "AJUSTE (estorno)"
    detalhe_destino        varchar(200),
    -- SUGERIDO, CONFIRMADO, RECUSADO. Só CONFIRMADO entra no previsto × realizado.
    estado                 varchar(20) not null,
    -- VERSAO_ANTERIOR, PLANILHA, NOME, ADMIN
    origem                 varchar(20) not null,
    motivo                 text,
    -- Sugestão copiada de linha igual da versão anterior (filtro "iguais à versão anterior")
    igual_versao_anterior  boolean not null default false,
    atualizado_por         varchar(200) not null,
    atualizado_em          timestamptz not null,
    constraint uk_depara_conta unique (previsao_id, conta_codigo),
    constraint ck_depara_destino check ((tipo_destino = 'LINHA_PO') = (linha_po_id is not null)),
    constraint ck_depara_tipo check (tipo_destino in ('LINHA_PO', 'AJUSTE', 'A_REALOCAR', 'TRANSFERENCIA')),
    constraint ck_depara_estado check (estado in ('SUGERIDO', 'CONFIRMADO', 'RECUSADO'))
);
create index ix_depara_previsao on depara_conta (previsao_id, estado);

-- Trilha do de-para: quem, quando, conta, destino e estado anteriores e novos. Só de inserção: o banco recusa
-- update e delete (mesmo gatilho da trilha da PO, V7). Migra para a trilha geral quando o RF-07.4 existir.
create table evento_depara (
    id                       uuid primary key,
    condominio_id            uuid not null references condominio (id),
    previsao_id              uuid not null references previsao_orcamentaria (id),
    conta_codigo             varchar(20) not null,
    conta_nome               varchar(200),
    -- SUGERIDO, CONFIRMADO, RECUSADO, ALTERADO
    acao                     varchar(20) not null,
    usuario                  varchar(200) not null,
    em                       timestamptz not null,
    tipo_destino_anterior    varchar(20),
    linha_po_anterior_id     uuid,
    destino_anterior         text,
    estado_anterior          varchar(20),
    tipo_destino_novo        varchar(20) not null,
    linha_po_nova_id         uuid,
    destino_novo             text not null,
    estado_novo              varchar(20) not null,
    origem                   varchar(20) not null,
    motivo                   text
);
create index ix_evento_depara on evento_depara (previsao_id, em);

create trigger tg_evento_depara_so_insercao before update or delete on evento_depara
    for each row execute function recusar_alteracao_trilha();
