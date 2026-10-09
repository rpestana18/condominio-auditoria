package br.com.condominioauditoria.api.messaging;

import br.com.condominioauditoria.api.model.file.SourceFile;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Message api → rag. Contract: contracts/mensagens/v1/arquivo-recebido.schema.json. */
public record FileReceivedMessage(
        @JsonProperty("versao") int version,
        @JsonProperty("processamentoId") UUID processingId,
        @JsonProperty("arquivoId") UUID fileId,
        @JsonProperty("condominioId") UUID condominiumId,
        @JsonProperty("categoria") String category,
        @JsonProperty("nomeOriginal") String originalName,
        @JsonProperty("caminho") String path,
        String sha256) {

    static FileReceivedMessage from(SourceFile a) {
        return new FileReceivedMessage(1, a.getProcessingId(), a.getId(), a.getCondominiumId(), a.getCategory().name(),
                a.getOriginalName(), a.getPath(), a.getSha256());
    }
}
