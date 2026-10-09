package br.com.condominioauditoria.rag.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.UUID;

/** Message api → rag (queue rag.indexacao). Contract: contracts/mensagens/v1/indexar-arquivo.schema.json. */
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
        @JsonProperty("modoEmbeddings") EmbeddingMode embeddingMode,
        @JsonProperty("modeloEmbeddings") String embeddingModel) {

    public enum Operation {
        INDEXAR, RETIRAR
    }

    /** Missing or null = LOCAL. DESLIGADO = only chunks for the keyword search, no vectors. */
    public enum EmbeddingMode {
        LOCAL, DESLIGADO
    }

    public boolean withVectors() {
        return embeddingMode != EmbeddingMode.DESLIGADO;
    }
}
