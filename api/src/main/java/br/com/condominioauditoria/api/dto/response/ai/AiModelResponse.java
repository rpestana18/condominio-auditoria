package br.com.condominioauditoria.api.dto.response.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A catalog model (contracts/openapi.yaml, ModeloIa). Prices are exact decimal text in US$ per million tokens. */
public record AiModelResponse(
        String id,
        @JsonProperty("nome") String name,
        @JsonProperty("padrao") boolean isDefault,
        @JsonProperty("precoEntradaMilhaoUsd") String inputPricePerMillionUsd,
        @JsonProperty("precoSaidaMilhaoUsd") String outputPricePerMillionUsd) {
}
