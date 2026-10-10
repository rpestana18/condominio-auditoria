package br.com.condominioauditoria.api.dto.response.feature;

import java.time.Instant;
import java.util.List;

/** A catalog feature with its state in the condominium. */
public record FeatureResponse(
        String code,
        String name,
        String description,
        List<String> includes,
        List<String> dependsOn,
        boolean enabledByDefault,
        boolean enabled,
        Instant since,
        int catalogVersion) {
}
