-- ADR 0006, fase 2: códigos gravados em inglês (estados, tipos, categorias, regras e alvos dos achados).
-- As restrições e o índice parcial que listam os códigos são recriados com os valores novos.

alter table source_file drop constraint ck_arquivo_indexacao_situacao;
drop index ix_arquivo_fila_indexacao;
alter table finding drop constraint ck_achado_estado;
alter table account_mapping drop constraint ck_depara_estado;
alter table account_mapping drop constraint ck_depara_tipo;
alter table account_mapping drop constraint ck_depara_destino;
alter table feature_usage drop constraint ck_uso_modulo_funcao;
alter table feature_usage drop constraint ck_uso_modulo_modo;
alter table reallocation_event drop constraint ck_evento_realocacao_acao;
alter table ai_configuration drop constraint ck_configuracao_ia_geral;
alter table ai_configuration drop constraint ck_configuracao_ia_funcao;
alter table ai_configuration drop constraint ck_configuracao_ia_embeddings;
alter table ai_configuration drop constraint ck_configuracao_ia_chave;
alter table ai_configuration drop constraint ck_configuracao_ia_modo;
alter table ai_configuration_event drop constraint ck_evento_configuracao_ia_funcao;
alter table budget_line_item drop constraint ck_linha_rubrica_estado;
alter table budget_line_item drop constraint ck_linha_rubrica_origem;

update source_file set
    category = case category when 'BALANCETE' then 'TRIAL_BALANCE' when 'EXTRATO' then 'BANK_STATEMENT' when 'CONTRATO' then 'CONTRACT' when 'FOLHA' then 'PAYROLL' when 'COMPROVANTE' then 'RECEIPT' when 'ATA' then 'MINUTES' when 'CONVENCAO_RI' then 'BYLAWS' when 'OUTROS' then 'OTHER' else category end,
    status = case status when 'PENDENTE' then 'PENDING' when 'PROCESSANDO' then 'PROCESSING' when 'CONCLUIDO' then 'COMPLETED' when 'PRECISA_REVISAO' then 'NEEDS_REVIEW' when 'FALHOU' then 'FAILED' else status end,
    indexing_status = case indexing_status when 'NA_FILA' then 'QUEUED' when 'INDEXANDO' then 'INDEXING' when 'INDEXADO' then 'INDEXED' when 'SEM_TEXTO' then 'NO_TEXT' when 'RETIRADO' then 'WITHDRAWN' when 'ERRO' then 'ERROR' else indexing_status end;

update category_change set
    previous_category = case previous_category when 'BALANCETE' then 'TRIAL_BALANCE' when 'EXTRATO' then 'BANK_STATEMENT' when 'CONTRATO' then 'CONTRACT' when 'FOLHA' then 'PAYROLL' when 'COMPROVANTE' then 'RECEIPT' when 'ATA' then 'MINUTES' when 'CONVENCAO_RI' then 'BYLAWS' when 'OUTROS' then 'OTHER' else previous_category end,
    new_category = case new_category when 'BALANCETE' then 'TRIAL_BALANCE' when 'EXTRATO' then 'BANK_STATEMENT' when 'CONTRATO' then 'CONTRACT' when 'FOLHA' then 'PAYROLL' when 'COMPROVANTE' then 'RECEIPT' when 'ATA' then 'MINUTES' when 'CONVENCAO_RI' then 'BYLAWS' when 'OUTROS' then 'OTHER' else new_category end;

update ai_configuration set
    feature = case feature when 'ASSISTENTE' then 'ASSISTANT' else feature end,
    function = case function when 'RESPOSTAS' then 'ANSWERS' else function end,
    mode = case mode when 'MCP_EXTERNO' then 'EXTERNAL_MCP' when 'DESLIGADO' then 'OFF' else mode end;

alter table ai_configuration_event disable trigger user;
update ai_configuration_event set
    feature = case feature when 'ASSISTENTE' then 'ASSISTANT' else feature end,
    function = case function when 'RESPOSTAS' then 'ANSWERS' else function end,
    previous_mode = case previous_mode when 'MCP_EXTERNO' then 'EXTERNAL_MCP' when 'DESLIGADO' then 'OFF' else previous_mode end,
    new_mode = case new_mode when 'MCP_EXTERNO' then 'EXTERNAL_MCP' when 'DESLIGADO' then 'OFF' else new_mode end;
alter table ai_configuration_event enable trigger user;

update condominium_feature set
    feature = case feature when 'ASSISTENTE' then 'ASSISTANT' else feature end;

alter table feature_event disable trigger user;
update feature_event set
    feature = case feature when 'ASSISTENTE' then 'ASSISTANT' else feature end;
alter table feature_event enable trigger user;

alter table feature_usage disable trigger user;
update feature_usage set
    feature = case feature when 'ASSISTENTE' then 'ASSISTANT' else feature end,
    mode = case mode when 'MCP_EXTERNO' then 'EXTERNAL_MCP' when 'DESLIGADO' then 'OFF' else mode end,
    function = case function when 'busca_documentos' then 'document_search' when 'chamada_mcp' then 'mcp_call' when 'indexacao' then 'indexing' when 'pergunta' then 'question' else function end;
alter table feature_usage enable trigger user;

alter table finding disable trigger user;
update finding set
    rule = case rule when 'CONTA_SEM_LINHA_PO' then 'ACCOUNT_WITHOUT_BUDGET_LINE' when 'FUNDO_RESERVA_ACIMA_TETO' then 'RESERVE_FUND_ABOVE_CAP' when 'EXCESSO_MES_ACIMA_LIMITE' then 'MONTHLY_OVERRUN_ABOVE_LIMIT' else rule end,
    severity = case severity when 'INFORMATIVO' then 'INFO' when 'ATENCAO' then 'WARNING' when 'CRITICO' then 'CRITICAL' else severity end,
    status = case status when 'ABERTO' then 'OPEN' when 'NAO_SE_APLICA_MAIS' then 'NO_LONGER_APPLIES' when 'JUSTIFICADO' then 'JUSTIFIED' when 'RESOLVIDO' then 'RESOLVED' when 'FALSO_POSITIVO' then 'FALSE_POSITIVE' else status end;
update finding set target = case
    when target = 'conta:sem-conta' then 'account:no-account'
    when target like 'conta:%' then 'account:' || substr(target, 7)
    when target = 'fundo-condominio' then 'operating-fund'
    when target like 'previsao:%' then regexp_replace(target, '^previsao:([^:]*):linha:', 'budget:\1:line:')
    else target end;
alter table finding enable trigger user;

alter table finding_event disable trigger user;
update finding_event set
    previous_status = case previous_status when 'ABERTO' then 'OPEN' when 'NAO_SE_APLICA_MAIS' then 'NO_LONGER_APPLIES' when 'JUSTIFICADO' then 'JUSTIFIED' when 'RESOLVIDO' then 'RESOLVED' when 'FALSO_POSITIVO' then 'FALSE_POSITIVE' else previous_status end,
    new_status = case new_status when 'ABERTO' then 'OPEN' when 'NAO_SE_APLICA_MAIS' then 'NO_LONGER_APPLIES' when 'JUSTIFICADO' then 'JUSTIFIED' when 'RESOLVIDO' then 'RESOLVED' when 'FALSO_POSITIVO' then 'FALSE_POSITIVE' else new_status end;
alter table finding_event enable trigger user;

update rule_parameter set
    code = case code when 'TETO_FUNDO_RESERVA_PERCENTUAL' then 'RESERVE_FUND_CAP_PERCENT' when 'LIMITE_EXCESSO_MES_PERCENTUAL' then 'MONTHLY_OVERRUN_LIMIT_PERCENT' else code end;

alter table budget_event disable trigger user;
update budget_event set
    type = case type when 'CONFIRMADA' then 'CONFIRMED' when 'PRORROGADA' then 'EXTENDED' when 'PRORROGACAO_ENCURTADA' then 'EXTENSION_SHORTENED' when 'PRORROGACAO_DESFEITA' then 'EXTENSION_UNDONE' when 'FUNDOS_ALTERADOS' then 'FUNDS_CHANGED' when 'SUBSTITUIDA' then 'SUPERSEDED' else type end;
alter table budget_event enable trigger user;

alter table reallocation_event disable trigger user;
update reallocation_event set
    action = case action when 'REALOCADA' then 'REALLOCATED' when 'DESFEITA' then 'UNDONE' else action end;
alter table reallocation_event enable trigger user;

update account_mapping set
    target_type = case target_type when 'LINHA_PO' then 'BUDGET_LINE' when 'AJUSTE' then 'ADJUSTMENT' when 'A_REALOCAR' then 'TO_REALLOCATE' when 'TRANSFERENCIA' then 'TRANSFER' else target_type end,
    status = case status when 'SUGERIDO' then 'SUGGESTED' when 'CONFIRMADO' then 'CONFIRMED' when 'RECUSADO' then 'REJECTED' else status end,
    source = case source when 'VERSAO_ANTERIOR' then 'PREVIOUS_VERSION' when 'PLANILHA' then 'SPREADSHEET' when 'NOME' then 'NAME' else source end;

alter table account_mapping_event disable trigger user;
update account_mapping_event set
    action = case action when 'SUGERIDO' then 'SUGGESTED' when 'CONFIRMADO' then 'CONFIRMED' when 'RECUSADO' then 'REJECTED' when 'ALTERADO' then 'CHANGED' else action end,
    previous_target_type = case previous_target_type when 'LINHA_PO' then 'BUDGET_LINE' when 'AJUSTE' then 'ADJUSTMENT' when 'A_REALOCAR' then 'TO_REALLOCATE' when 'TRANSFERENCIA' then 'TRANSFER' else previous_target_type end,
    new_target_type = case new_target_type when 'LINHA_PO' then 'BUDGET_LINE' when 'AJUSTE' then 'ADJUSTMENT' when 'A_REALOCAR' then 'TO_REALLOCATE' when 'TRANSFERENCIA' then 'TRANSFER' else new_target_type end,
    previous_status = case previous_status when 'SUGERIDO' then 'SUGGESTED' when 'CONFIRMADO' then 'CONFIRMED' when 'RECUSADO' then 'REJECTED' else previous_status end,
    new_status = case new_status when 'SUGERIDO' then 'SUGGESTED' when 'CONFIRMADO' then 'CONFIRMED' when 'RECUSADO' then 'REJECTED' else new_status end,
    source = case source when 'VERSAO_ANTERIOR' then 'PREVIOUS_VERSION' when 'PLANILHA' then 'SPREADSHEET' when 'NOME' then 'NAME' else source end;
alter table account_mapping_event enable trigger user;

update budget set
    status = case status when 'LIDA' then 'READ' when 'LIDA_COM_DIVERGENCIA' then 'READ_WITH_DISCREPANCY' when 'CONFIRMADA' then 'CONFIRMED' when 'SUBSTITUIDA' then 'SUPERSEDED' else status end;

update budget_line set
    type = case type when 'GRUPO' then 'GROUP' when 'LINHA' then 'LINE' else type end,
    mark = case mark when 'RATEIO_A_PARTE' then 'SEPARATE_APPORTIONMENT' when 'NEGOCIADA_ISENCAO' then 'NEGOTIATED_EXEMPTION' when 'SEM_VALOR' then 'NO_AMOUNT' when 'VALOR_FIXO_SEM_REFERENCIA' then 'FIXED_AMOUNT_NO_REFERENCE' else mark end;

update budget_line_item set
    status = case status when 'SUGERIDO' then 'SUGGESTED' when 'CONFIRMADO' then 'CONFIRMED' when 'RECUSADO' then 'REJECTED' else status end,
    source = case source when 'PRIMEIRA_PO' then 'FIRST_BUDGET' when 'CONTA_PO' then 'BUDGET_ACCOUNT' when 'VERSAO_ANTERIOR' then 'PREVIOUS_VERSION' else source end;

alter table budget_item_event disable trigger user;
update budget_item_event set
    action = case action when 'CRIADA' then 'CREATED' when 'RENOMEADA' then 'RENAMED' when 'SUGERIDO' then 'SUGGESTED' when 'CONFIRMADO' then 'CONFIRMED' when 'RECUSADO' then 'REJECTED' when 'ALTERADO' then 'CHANGED' else action end,
    previous_status = case previous_status when 'SUGERIDO' then 'SUGGESTED' when 'CONFIRMADO' then 'CONFIRMED' when 'RECUSADO' then 'REJECTED' else previous_status end,
    new_status = case new_status when 'SUGERIDO' then 'SUGGESTED' when 'CONFIRMADO' then 'CONFIRMED' when 'RECUSADO' then 'REJECTED' else new_status end,
    source = case source when 'PRIMEIRA_PO' then 'FIRST_BUDGET' when 'CONTA_PO' then 'BUDGET_ACCOUNT' when 'VERSAO_ANTERIOR' then 'PREVIOUS_VERSION' else source end;
alter table budget_item_event enable trigger user;

update totals_check set
    code = case code when 'TOTAIS_FUNDO' then 'FUND_TOTALS' when 'SALDO_FINAL_FUNDO' then 'FUND_CLOSING_BALANCE' when 'FUNDO_TAXA' then 'FUND_RATE' when 'SALDO_CORRENTE' then 'RUNNING_BALANCE' when 'TOTAL_POSICAO' then 'POSITION_TOTAL' when 'LINHA_SEM_GRUPO' then 'LINE_WITHOUT_GROUP' when 'SUBTOTAL_GRUPO' then 'GROUP_SUBTOTAL' when 'PREVISTO_MES' then 'MONTHLY_PLANNED' when 'CODIGO_REPETIDO' then 'REPEATED_CODE' else code end;

alter table source_file add constraint ck_source_file_indexing_status check (indexing_status in ('QUEUED', 'INDEXING', 'INDEXED', 'NO_TEXT', 'WITHDRAWN', 'ERROR'));
create index ix_source_file_indexing_queue on source_file (indexing_status, indexing_queued_at) where indexing_status in ('QUEUED', 'INDEXING');
alter table finding add constraint ck_finding_status check (status in ('OPEN', 'NO_LONGER_APPLIES', 'JUSTIFIED', 'RESOLVED', 'FALSE_POSITIVE'));
alter table account_mapping add constraint ck_account_mapping_status check (status in ('SUGGESTED', 'CONFIRMED', 'REJECTED'));
alter table account_mapping add constraint ck_account_mapping_target_type check (target_type in ('BUDGET_LINE', 'ADJUSTMENT', 'TO_REALLOCATE', 'TRANSFER'));
alter table account_mapping add constraint ck_account_mapping_target check ((target_type = 'BUDGET_LINE') = (budget_line_id is not null));
alter table feature_usage add constraint ck_feature_usage_function check (function in ('document_search', 'mcp_call', 'indexing', 'embeddings', 'question'));
alter table feature_usage add constraint ck_feature_usage_mode check (mode in ('API_KEY', 'EXTERNAL_MCP', 'LOCAL', 'OFF'));
alter table reallocation_event add constraint ck_reallocation_event_action check (action in ('REALLOCATED', 'UNDONE'));
alter table ai_configuration add constraint ck_ai_configuration_general check (feature is not null or (function = 'ANSWERS' and mode is not null and provider is null and model is null and encrypted_key is null));
alter table ai_configuration add constraint ck_ai_configuration_function check (function in ('ANSWERS', 'EMBEDDINGS'));
alter table ai_configuration add constraint ck_ai_configuration_embeddings check (function <> 'EMBEDDINGS' or mode is not null);
alter table ai_configuration add constraint ck_ai_configuration_key check ((encrypted_key is null) = (key_suffix is null) and (encrypted_key is null or function = 'ANSWERS'));
alter table ai_configuration add constraint ck_ai_configuration_mode check (mode in ('API_KEY', 'EXTERNAL_MCP', 'LOCAL', 'OFF'));
alter table ai_configuration_event add constraint ck_ai_configuration_event_function check (function in ('ANSWERS', 'EMBEDDINGS'));
alter table budget_line_item add constraint ck_budget_line_item_status check (status in ('SUGGESTED', 'CONFIRMED', 'REJECTED'));
alter table budget_line_item add constraint ck_budget_line_item_source check (source in ('FIRST_BUDGET', 'BUDGET_ACCOUNT', 'PREVIOUS_VERSION', 'MANUAL'));
