package br.com.condominioauditoria.api.dto.request.ai;

import br.com.condominioauditoria.api.model.enums.AiMode;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The Assistant's embeddings: only LOCAL or DESLIGADO in this phase. */
public record AiEmbeddingsRequest(
        @JsonProperty("modo") AiMode mode,
        @JsonProperty("provedor") String provider,
        @JsonProperty("modelo") String model) {
}
