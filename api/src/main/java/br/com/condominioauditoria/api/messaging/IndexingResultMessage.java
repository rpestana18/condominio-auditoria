package br.com.condominioauditoria.api.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Message rag → api (queue backend.indexacao). Contract: contracts/mensagens/v1/resultado-indexacao.schema.json. These
 * records belong to the api: the rag has its own. Only the JSON contract is shared.
 */
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
}
