package br.com.condominioauditoria.api.config.properties;

import br.com.condominioauditoria.api.exception.UnknownFeatureException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Catalog of contractable features (RF-10.1), read at startup from catalogo-modulos.yml. An invalid catalog (no
 * version, repeated code, dependency outside the catalog) keeps the api from starting.
 */
@ConfigurationProperties(prefix = "catalogo-modulos")
public record FeatureCatalog(int version, List<FeatureDefinition> features) {

    public FeatureCatalog {
        if (version <= 0) {
            throw new IllegalArgumentException("catalogo-modulos.versao precisa ser maior que zero");
        }
        features = features == null ? List.of() : List.copyOf(features);
        Set<String> codes = new HashSet<>();
        for (FeatureDefinition m : features) {
            if (!codes.add(m.code())) {
                throw new IllegalArgumentException("Módulo repetido no catálogo: " + m.code());
            }
        }
        for (FeatureDefinition m : features) {
            for (String dependency : m.dependsOn()) {
                if (!codes.contains(dependency)) {
                    throw new IllegalArgumentException(
                            "Módulo " + m.code() + " depende de " + dependency + ", que não está no catálogo");
                }
            }
        }
    }

    /** A catalog entry: code, name, description, what it includes, dependencies and the state of a new condominium. */
    public record FeatureDefinition(String code, String name, String description, List<String> includes,
            List<String> dependsOn, boolean enabledByDefault) {

        public FeatureDefinition {
            if (code == null || !code.matches("[A-Z][A-Z0-9_]{1,39}")) {
                throw new IllegalArgumentException("Código de módulo inválido no catálogo: " + code);
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Módulo " + code + " sem nome no catálogo");
            }
            description = description == null ? "" : description.strip();
            includes = includes == null ? List.of() : List.copyOf(includes);
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        }
    }

    public Optional<FeatureDefinition> find(String code) {
        return features.stream().filter(m -> m.code().equals(code)).findFirst();
    }

    /** The catalog feature; an unknown code becomes 404 in the API. */
    public FeatureDefinition require(String code) {
        return find(code).orElseThrow(() -> new UnknownFeatureException(code));
    }
}
