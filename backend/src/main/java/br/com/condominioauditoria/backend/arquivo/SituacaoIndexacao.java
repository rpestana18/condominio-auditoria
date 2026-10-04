package br.com.condominioauditoria.backend.arquivo;

/**
 * Estado da indexação do arquivo para a busca nos documentos (ADR 0003, Decisão 5.1). É separado do status da
 * leitura contábil ({@link StatusArquivo}): um arquivo pode estar lido e ainda sem índice, e vice-versa.
 */
public enum SituacaoIndexacao {
    /** Pedido publicado na fila rag.indexacao, aguardando o rag. */
    NA_FILA,
    /** O rag começou a cortar os trechos e gerar os vetores. */
    INDEXANDO,
    /** Trechos gravados no índice: o arquivo aparece na busca. */
    INDEXADO,
    /** O arquivo não tem texto extraível (ex.: PDF digitalizado sem OCR). O motivo diz o que houve. */
    SEM_TEXTO,
    /** Índice marcado como retirado (exclusão lógica ou versão substituída); não aparece na busca. */
    RETIRADO,
    /** A indexação falhou. O motivo diz o que houve. */
    ERRO
}
