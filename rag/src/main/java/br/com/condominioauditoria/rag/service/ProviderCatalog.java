package br.com.condominioauditoria.rag.service;

import br.com.condominioauditoria.rag.config.properties.AiProperties;
import br.com.condominioauditoria.rag.config.properties.AiProperties.AiModel;
import br.com.condominioauditoria.rag.config.properties.AiProperties.AiProvider;
import br.com.condominioauditoria.rag.config.properties.AiProperties.AiFunction;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Queries on the provider catalog (configuration), used by the ListarProvedores rpc and by Perguntar. */
@Component
public class ProviderCatalog {

    private final List<AiProvider> providers;

    public ProviderCatalog(AiProperties properties) {
        this.providers = properties.providers() == null ? List.of() : List.copyOf(properties.providers());
    }

    /** In catalog order, as the contract requires. */
    public List<AiProvider> providers() {
        return providers;
    }

    public Optional<AiProvider> byCode(String code) {
        return providers.stream().filter(p -> p.code().equals(code)).findFirst();
    }

    /**
     * Provider and model must exist in the catalog and the provider must be for answers; anything else is
     * FAILED_PRECONDITION in Perguntar (contract assistente.proto).
     */
    public AiModel answerModel(String providerCode, String modelId) {
        AiProvider provider = byCode(providerCode).orElseThrow(() -> new ModelNotInCatalogException(
                "provedor de IA \"" + providerCode + "\" não está no catálogo deste rag"));
        if (provider.function() != AiFunction.ANSWERS) {
            throw new ModelNotInCatalogException(
                    "o provedor \"" + providerCode + "\" não serve para redigir respostas");
        }
        return provider.models().stream().filter(m -> m.id().equals(modelId)).findFirst()
                .orElseThrow(() -> new ModelNotInCatalogException("modelo \"" + modelId
                        + "\" não está no catálogo do provedor \"" + providerCode + "\""));
    }

    /** Provider or model not in the catalog, or with a function other than ANSWERS. */
    public static class ModelNotInCatalogException extends RuntimeException {
        public ModelNotInCatalogException(String message) {
            super(message);
        }
    }
}
