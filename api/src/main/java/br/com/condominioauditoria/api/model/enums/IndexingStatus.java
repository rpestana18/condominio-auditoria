package br.com.condominioauditoria.api.model.enums;

/**
 * Indexing status of the file for the document search (ADR 0003, Decision 5.1). It is separate from the accounting read
 * status ({@link FileStatus}): a file may be read and not yet indexed, and vice versa.
 */
public enum IndexingStatus {
    /** Request published on the rag.indexing queue, waiting for the rag. */
    QUEUED,
    /** The rag started splitting the chunks and generating the vectors. */
    INDEXING,
    /** Chunks saved in the index: the file shows up in the search. */
    INDEXED,
    /** The file has no extractable text (e.g. a scanned PDF without OCR). The reason says what happened. */
    NO_TEXT,
    /** Index marked as withdrawn (logical deletion or replaced version); it does not show up in the search. */
    WITHDRAWN,
    /** Indexing failed. The reason says what happened. */
    ERROR
}
