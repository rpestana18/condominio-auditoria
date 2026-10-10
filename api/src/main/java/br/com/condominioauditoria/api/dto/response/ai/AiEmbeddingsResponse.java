package br.com.condominioauditoria.api.dto.response.ai;

import br.com.condominioauditoria.api.model.enums.AiMode;

/** The Assistant's embeddings (search by meaning). */
public record AiEmbeddingsResponse(
        AiMode mode,
        String provider,
        String model) {
}
