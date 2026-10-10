package br.com.condominioauditoria.rag.search;

import java.util.List;

/**
 * Result of chunking a document. {@code pages} = pages (PDF), tabs (Excel) or 1 (Word), as in the indexing result
 * contract ({@code IndexingResultMessage}). Without chunks, {@code noTextReason} explains why.
 */
public record ChunkedDocument(int pages, List<Chunk> chunks, String noTextReason) {

    public boolean noText() {
        return chunks.isEmpty();
    }
}
