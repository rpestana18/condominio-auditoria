package br.com.condominioauditoria.api.dto.response.file;

import br.com.condominioauditoria.api.model.enums.IndexingStatus;

/** Indexing status for the document search. Null = the file was never sent to the index. */
public record IndexingResponse(
        IndexingStatus status,
        String reason,
        Integer pages,
        Integer chunks) {
}
