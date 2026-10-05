-- Previsto × realizado (ADR 0004, passo 7; RF-03.1.6 a RF-03.1.11).

-- Recebimento de cota do lançamento (mensagem v2, ADR 0004, Decisão 7): verdadeiro nos créditos "RECIBOS ACUMULADOS"
-- do layout Protest. Nulo = lançamento gravado antes desta coluna: a arrecadação dos fundos do mês aparece como
-- "reprocesse o fluxo" até o arquivo ser lido de novo (nunca como zero).
alter table lancamento add column recebimento_cota boolean;

-- Conv. 16.2 do piloto: despesas não previstas até 20% das despesas previstas para o mês (parâmetro de implantação,
-- com vigência, não constante no código).
insert into parametro_regra (id, condominio_id, codigo, valor, vigente_desde, fonte)
select gen_random_uuid(), c.id, 'LIMITE_EXCESSO_MES_PERCENTUAL', 20.0000, date '1900-01-01',
       'Convenção, cláusula 16.2 (implantação)'
from condominio c;
