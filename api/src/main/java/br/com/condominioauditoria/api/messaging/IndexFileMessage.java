package br.com.condominioauditoria.api.messaging;

import br.com.condominioauditoria.api.model.file.SourceFile;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Message api → rag (queue rag.indexacao). Contract: contracts/mensagens/v1/indexar-arquivo.schema.json. The file
 * content is not in the message: the rag reads the original by its path.
 *
 * Delivery 1: the file's period and version do not exist in the api yet (they go as null); embedding mode and model are
 * left out of the message when null (= LOCAL with the rag's default model) until the AI configuration per condominium
 * (delivery 2).
 */
public record IndexFileMessage(
        @JsonProperty("versao") int version,
        @JsonProperty("operacao") Operation operation,
        @JsonProperty("indexacaoId") UUID indexingId,
        @JsonProperty("arquivoId") UUID fileId,
        @JsonProperty("condominioId") UUID condominiumId,
        @JsonProperty("categoria") String category,
        @JsonProperty("nomeOriginal") String originalName,
        @JsonProperty("caminho") String path,
        String sha256,
        @JsonProperty("competenciaInicio") LocalDate periodStart,
        @JsonProperty("competenciaFim") LocalDate periodEnd,
        @JsonProperty("versaoArquivo") Integer fileVersion,
        @JsonProperty("modoEmbeddings") @JsonInclude(JsonInclude.Include.NON_NULL) String embeddingMode,
        @JsonProperty("modeloEmbeddings") @JsonInclude(JsonInclude.Include.NON_NULL) String embeddingModel) {

    public enum Operation {
        INDEXAR, RETIRAR
    }

    static IndexFileMessage index(SourceFile a) {
        return new IndexFileMessage(1, Operation.INDEXAR, a.getIndexingId(), a.getId(), a.getCondominiumId(),
                a.getCategory().name(), a.getOriginalName(), a.getPath(), a.getSha256(), null, null, null, null,
                null);
    }
}
