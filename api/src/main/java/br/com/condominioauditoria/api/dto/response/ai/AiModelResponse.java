package br.com.condominioauditoria.api.dto.response.ai;


/**
 * A catalog model (contracts/openapi.yaml, AiModelResponse). Prices are exact decimal text in US$ per million
 * tokens.
 */
public record AiModelResponse(
        String id,
        String name,
        boolean isDefault,
        String inputPricePerMillionUsd,
        String outputPricePerMillionUsd) {
}
