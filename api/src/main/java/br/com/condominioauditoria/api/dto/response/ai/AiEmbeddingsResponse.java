package br.com.condominioauditoria.api.dto.response.ai;

import br.com.condominioauditoria.api.model.enums.AiMode;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The Assistant's embeddings (search by meaning). */
public record AiEmbeddingsResponse(
        @JsonProperty("modo") AiMode mode,
        @JsonProperty("provedor") String provider,
        @JsonProperty("modelo") String model) {
}
