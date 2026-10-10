package br.com.condominioauditoria.api.dto.response.ai;

import br.com.condominioauditoria.api.model.enums.AiFunction;
import java.util.List;

/** A catalog provider, in the rag's order and without the public key (contracts/openapi.yaml, AiProviderResponse). */
public record AiProviderResponse(
        String code,
        String name,
        String type,
        AiFunction function,
        boolean local,
        boolean requiresKey,
        Integer dimension,
        List<AiModelResponse> models) {
}
