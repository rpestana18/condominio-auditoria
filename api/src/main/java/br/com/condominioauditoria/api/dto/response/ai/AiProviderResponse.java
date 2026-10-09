package br.com.condominioauditoria.api.dto.response.ai;

import br.com.condominioauditoria.api.model.enums.AiFunction;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A catalog provider, in the rag's order and without the public key (contracts/openapi.yaml, ProvedorIa). */
public record AiProviderResponse(
        @JsonProperty("codigo") String code,
        @JsonProperty("nome") String name,
        @JsonProperty("tipo") String type,
        @JsonProperty("uso") AiFunction function,
        boolean local,
        @JsonProperty("precisaChave") boolean requiresKey,
        @JsonProperty("dimensao") Integer dimension,
        @JsonProperty("modelos") List<AiModelResponse> models) {
}
