-- PO prorrogada (ADR 0005, Decisão 4; RF-11.3): o Admin marca a PO confirmada como prorrogada até um mês depois do fim
-- do exercício, com justificativa. Os meses prorrogados usam esta PO e o seu de-para, com a marca "PO prorrogada", e
-- ficam fora do acumulado do exercício. Nunca é automática. Marcar, desfazer e encurtar vão para evento_previsao.

alter table previsao_orcamentaria
    add column prorrogada_ate            date,
    add column prorrogacao_justificativa text,
    add column prorrogada_por            varchar(200),
    add column prorrogada_em             timestamptz,
    add constraint ck_previsao_prorrogacao check (
        prorrogada_ate is null
        or (exercicio_fim is not null and prorrogada_ate > exercicio_fim
            and prorrogacao_justificativa is not null and prorrogada_por is not null and prorrogada_em is not null));
