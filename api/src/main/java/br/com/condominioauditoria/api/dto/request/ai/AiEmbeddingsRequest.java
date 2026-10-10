package br.com.condominioauditoria.api.dto.request.ai;

import br.com.condominioauditoria.api.model.enums.AiMode;

/** The Assistant's embeddings: only LOCAL or OFF in this phase. */
public record AiEmbeddingsRequest(
        AiMode mode,
        String provider,
        String model) {
}
