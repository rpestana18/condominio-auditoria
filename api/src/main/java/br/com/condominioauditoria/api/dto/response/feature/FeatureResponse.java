package br.com.condominioauditoria.api.dto.response.feature;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/** A catalog feature with its state in the condominium. */
public record FeatureResponse(
        @JsonProperty("codigo") String code,
        @JsonProperty("nome") String name,
        @JsonProperty("descricao") String description,
        @JsonProperty("inclui") List<String> includes,
        @JsonProperty("dependeDe") List<String> dependsOn,
        @JsonProperty("ligadoPorPadrao") boolean enabledByDefault,
        @JsonProperty("ligado") boolean enabled,
        @JsonProperty("desde") Instant since,
        @JsonProperty("versaoCatalogo") int catalogVersion) {
}
