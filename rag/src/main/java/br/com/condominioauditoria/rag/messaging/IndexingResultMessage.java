package br.com.condominioauditoria.rag.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Message rag → api (queue backend.indexacao). Contract: contracts/mensagens/v1/resultado-indexacao.schema.json. */
public record IndexingResultMessage(
        @JsonProperty("versao") int version,
        @JsonProperty("indexacaoId") UUID indexingId,
        @JsonProperty("arquivoId") UUID fileId,
        @JsonProperty("condominioId") UUID condominiumId,
        @JsonProperty("situacao") Status status,
        @JsonProperty("motivo") String reason,
        @JsonProperty("paginas") Integer pages,
        @JsonProperty("trechos") Integer chunks,
        @JsonProperty("modeloEmbeddings") String embeddingModel,
        @JsonProperty("versaoIndexador") String indexerVersion) {

    public enum Status {
        INDEXANDO, INDEXADO, SEM_TEXTO, RETIRADO, ERRO
    }

    public static IndexingResultMessage indexing(IndexFileMessage p) {
        return new IndexingResultMessage(1, p.indexingId(), p.fileId(), p.condominiumId(), Status.INDEXANDO, null,
                null, null, null, null);
    }

    public static IndexingResultMessage indexed(IndexFileMessage p, int pages, int chunks, String model,
            String indexerVersion) {
        return indexed(p, pages, chunks, model, indexerVersion, null);
    }

    /** {@code warning}: e.g. indexed only for the keyword search because Ollama was down (goes in the reason). */
    public static IndexingResultMessage indexed(IndexFileMessage p, int pages, int chunks, String model,
            String indexerVersion, String warning) {
        return new IndexingResultMessage(1, p.indexingId(), p.fileId(), p.condominiumId(), Status.INDEXADO, warning,
                pages, chunks, model, indexerVersion);
    }

    public static IndexingResultMessage noText(IndexFileMessage p, String reason, int pages, String indexerVersion) {
        return new IndexingResultMessage(1, p.indexingId(), p.fileId(), p.condominiumId(), Status.SEM_TEXTO, reason,
                pages, 0, null, indexerVersion);
    }

    public static IndexingResultMessage withdrawn(IndexFileMessage p) {
        return new IndexingResultMessage(1, p.indexingId(), p.fileId(), p.condominiumId(), Status.RETIRADO, null,
                null, null, null, null);
    }

    public static IndexingResultMessage error(IndexFileMessage p, String reason) {
        return new IndexingResultMessage(1, p.indexingId(), p.fileId(), p.condominiumId(), Status.ERRO, reason,
                null, null, null, null);
    }
}
