package br.com.condominioauditoria.api.model.enums;

/**
 * Indexing status of the file for the document search (ADR 0003, Decision 5.1). It is separate from the accounting read
 * status ({@link FileStatus}): a file may be read and not yet indexed, and vice versa.
 */
public enum IndexingStatus {
    /** Request published on the rag.indexacao queue, waiting for the rag. */
    NA_FILA,
    /** The rag started splitting the chunks and generating the vectors. */
    INDEXANDO,
    /** Chunks saved in the index: the file shows up in the search. */
    INDEXADO,
    /** The file has no extractable text (e.g. a scanned PDF without OCR). The reason says what happened. */
    SEM_TEXTO,
    /** Index marked as withdrawn (logical deletion or replaced version); it does not show up in the search. */
    RETIRADO,
    /** Indexing failed. The reason says what happened. */
    ERRO
}
