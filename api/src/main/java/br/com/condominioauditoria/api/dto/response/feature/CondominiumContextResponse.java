package br.com.condominioauditoria.api.dto.response.feature;

import java.util.List;
import java.util.UUID;

/** Null assistant = Assistant feature disabled (contracts/openapi.yaml, CondominiumContextResponse). */
public record CondominiumContextResponse(
        UUID condominiumId,
        String name,
        List<String> enabledFeatures,
        AssistantContextResponse assistant) {
}
