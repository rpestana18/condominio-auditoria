-- ADR 0006, fase 2: códigos gravados em inglês (situação da indexação e categoria do arquivo).
alter table indexed_document drop constraint ck_documento_estado;

update indexed_document set
    status = case status when 'na_fila' then 'queued' when 'indexando' then 'indexing' when 'indexado' then 'indexed'
        when 'sem_texto' then 'no_text' when 'erro' then 'error' else status end,
    category = case category when 'BALANCETE' then 'TRIAL_BALANCE' when 'EXTRATO' then 'BANK_STATEMENT'
        when 'CONTRATO' then 'CONTRACT' when 'FOLHA' then 'PAYROLL' when 'COMPROVANTE' then 'RECEIPT'
        when 'ATA' then 'MINUTES' when 'CONVENCAO_RI' then 'BYLAWS' when 'OUTROS' then 'OTHER' else category end;

alter table indexed_document add constraint ck_indexed_document_status
    check (status in ('queued', 'indexing', 'indexed', 'no_text', 'error'));
