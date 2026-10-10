-- ADR 0006, fase 2: tabelas e colunas do rag em inglês. Só renomeia: nenhum dado muda.
-- A coluna gerada da busca e o índice HNSW parcial por modelo acompanham o novo nome sozinhos.

alter table documento_indexado rename column arquivo_id to file_id;
alter table documento_indexado rename column condominio_id to condominium_id;
alter table documento_indexado rename column categoria to category;
alter table documento_indexado rename column nome_original to original_name;
alter table documento_indexado rename column caminho to path;
alter table documento_indexado rename column competencia_inicio to period_start;
alter table documento_indexado rename column competencia_fim to period_end;
alter table documento_indexado rename column versao_arquivo to file_version;
alter table documento_indexado rename column estado to status;
alter table documento_indexado rename column motivo to reason;
alter table documento_indexado rename column paginas to pages;
alter table documento_indexado rename column trechos to chunks;
alter table documento_indexado rename column modelo_embeddings to embedding_model;
alter table documento_indexado rename column versao_indexador to indexer_version;
alter table documento_indexado rename column vigente to is_current;
alter table documento_indexado rename column retirado to withdrawn;
alter table documento_indexado rename column indexacao_id to indexing_id;
alter table documento_indexado rename column atualizado_em to updated_at;
alter table documento_indexado rename to indexed_document;

alter table trecho rename column arquivo_id to file_id;
alter table trecho rename column condominio_id to condominium_id;
alter table trecho rename column ordem to sequence;
alter table trecho rename column pagina to page;
alter table trecho rename column aba to tab;
alter table trecho rename column linha_inicio to start_row;
alter table trecho rename column linha_fim to end_row;
alter table trecho rename column secao to section;
alter table trecho rename column paragrafo_inicio to paragraph_start;
alter table trecho rename column paragrafo_fim to paragraph_end;
alter table trecho rename column texto to text;
alter table trecho rename column busca to search_vector;
alter table trecho rename to chunk;

alter table trecho_vetor rename column trecho_id to chunk_id;
alter table trecho_vetor rename column modelo to model;
alter table trecho_vetor rename column vetor to embedding;
alter table trecho_vetor rename to chunk_vector;
